package ru.sportsresults.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.sportsresults.config.YandexDiskDocumentStorageProperties;
import ru.sportsresults.service.InvalidRequestException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YandexDiskDocumentStorageTest {
    private static final String AUTHORIZATION = "OAuth test-token";
    private static final String KEY = "events/123/documents/550e8400-e29b-41d4-a716-446655440000.pdf";
    private static final byte[] PDF = "%PDF-1.4\nsynthetic".getBytes(StandardCharsets.US_ASCII);

    private MockRestServiceServer apiServer;
    private MockRestServiceServer transferServer;
    private YandexDiskDocumentStorage storage;

    @BeforeEach
    void setUp() {
        RestClient.Builder apiBuilder = RestClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        RestClient.Builder transferBuilder = RestClient.builder();
        apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
        transferServer = MockRestServiceServer.bindTo(transferBuilder).build();
        storage = new YandexDiskDocumentStorage(
                apiBuilder.build(),
                transferBuilder.build(),
                new YandexDiskDocumentStorageProperties(
                        URI.create("https://cloud-api.example.test/v1/disk"), "app:/", "test-token"
                ),
                new ObjectMapper()
        );
    }

    @Test
    void storesPdfUnderTheEventDirectoryWithoutForwardingOAuthToTheUploader() {
        expectDirectory("app:/events");
        expectDirectory("app:/events/123");
        expectDirectory("app:/events/123/documents");
        apiServer.expect(requestTo(matchesPattern(".*\\/resources\\/upload\\?.*")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(queryParam("path", matchesPattern(
                        "app:/events/123/documents/[0-9a-f-]{36}\\.pdf"
                )))
                .andExpect(queryParam("overwrite", "false"))
                .andRespond(withSuccess(
                        "{\"href\":\"https://uploader.example.test/document\",\"method\":\"PUT\"}",
                        MediaType.APPLICATION_JSON
                ));
        transferServer.expect(requestTo("https://uploader.example.test/document"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(content().bytes(PDF))
                .andRespond(withStatus(HttpStatus.CREATED));

        String key = storage.store(123L, PDF);

        assertThat(key).matches("events/123/documents/[0-9a-f-]{36}\\.pdf");
        apiServer.verify();
        transferServer.verify();
    }

    @Test
    void downloadsChecksAndDeletesOnlyTheExactPrivateObject() throws Exception {
        apiServer.expect(requestTo(matchesPattern(".*\\/resources\\/download\\?.*")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("path", "app:/" + KEY))
                .andRespond(withSuccess(
                        "{\"href\":\"https://downloader.example.test/document\"}",
                        MediaType.APPLICATION_JSON
                ));
        transferServer.expect(requestTo("https://downloader.example.test/document"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess(PDF, MediaType.APPLICATION_PDF));
        apiServer.expect(requestTo(matchesPattern(".*\\/resources\\?.*")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("path", "app:/" + KEY))
                .andExpect(queryParam("fields", "path"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        apiServer.expect(requestTo(matchesPattern(".*\\/resources\\?.*")))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(queryParam("path", "app:/" + KEY))
                .andExpect(queryParam("permanently", "true"))
                .andRespond(withNoContent());

        assertThat(storage.open(KEY).readAllBytes()).isEqualTo(PDF);
        assertThat(storage.exists(KEY)).isTrue();
        storage.delete(KEY);

        apiServer.verify();
        transferServer.verify();
    }

    @Test
    void missingObjectExistsAndDeleteAreIdempotent() {
        apiServer.expect(requestTo(matchesPattern(".*\\/resources\\?.*")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());
        apiServer.expect(requestTo(matchesPattern(".*\\/resources\\?.*")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withResourceNotFound());

        assertThat(storage.exists(KEY)).isFalse();
        storage.delete(KEY);

        apiServer.verify();
    }

    @Test
    void existingDirectoriesAreAcceptedAndUnsafePathsOrRootsAreRejected() {
        apiServer.expect(requestTo(matchesPattern(".*\\/resources\\?.*")))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("{\"error\":\"DiskPathPointsToExistentDirectoryError\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        expectDirectory("app:/events/123");
        expectDirectory("app:/events/123/documents");
        apiServer.expect(requestTo(matchesPattern(".*\\/resources\\/upload\\?.*")))
                .andRespond(withSuccess(
                        "{\"href\":\"https://uploader.example.test/document\"}",
                        MediaType.APPLICATION_JSON
                ));
        transferServer.expect(requestTo("https://uploader.example.test/document"))
                .andRespond(withStatus(HttpStatus.CREATED));

        storage.store(123L, PDF);

        assertThatThrownBy(() -> storage.open("events/../secret.pdf"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> storage.open("C:\\secret.pdf"))
                .isInstanceOf(InvalidRequestException.class);

        YandexDiskDocumentStorage unsafeRoot = new YandexDiskDocumentStorage(
                RestClient.create(), RestClient.create(),
                new YandexDiskDocumentStorageProperties(
                        URI.create("https://cloud-api.example.test/v1/disk"), "disk:/documents", "test-token"
                )
        );
        assertThatThrownBy(unsafeRoot::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app:/");
    }

    private void expectDirectory(String path) {
        apiServer.expect(requestTo(matchesPattern(".*\\/resources\\?.*")))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(queryParam("path", path))
                .andRespond(withStatus(HttpStatus.CREATED));
    }
}
