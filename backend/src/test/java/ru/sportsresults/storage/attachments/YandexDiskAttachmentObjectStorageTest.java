package ru.sportsresults.storage.attachments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.sportsresults.config.YandexDiskAttachmentStorageProperties;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YandexDiskAttachmentObjectStorageTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final String KEY = "result-issues/1/2/550e8400-e29b-41d4-a716-446655440000";
    private static final String AUTHORIZATION = "OAuth test-token";

    private MockRestServiceServer server;
    private YandexDiskAttachmentObjectStorage storage;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        server = MockRestServiceServer.bindTo(builder).build();
        storage = new YandexDiskAttachmentObjectStorage(
                builder.build(),
                new YandexDiskAttachmentStorageProperties(
                        URI.create("https://cloud-api.example.test/v1/disk"),
                        "app:/",
                        "test-token"
                ),
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void uploadMetadataDownloadAndDeleteStayInsideThePrivateAppFolder() {
        expectDirectory("app:/result-issues");
        expectDirectory("app:/result-issues/1");
        expectDirectory("app:/result-issues/1/2");
        server.expect(requestTo(containsString("/resources/upload?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(queryParam("path", "app:/" + KEY))
                .andExpect(queryParam("overwrite", "true"))
                .andRespond(withSuccess(
                        "{\"href\":\"https://uploader.example.test/temporary-upload\","
                                + "\"method\":\"PUT\",\"templated\":false}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo(containsString("/resources?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(queryParam("path", "app:/" + KEY))
                .andExpect(queryParam("fields", "size,mime_type,md5,sha256"))
                .andRespond(withSuccess(
                        "{\"size\":12,\"mime_type\":\"image/jpeg\",\"md5\":\"abc123\"}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo(containsString("/resources/download?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(queryParam("path", "app:/" + KEY))
                .andRespond(withSuccess(
                        "{\"href\":\"https://downloader.example.test/temporary-download\","
                                + "\"method\":\"GET\",\"templated\":false}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo(containsString("/resources?")))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(queryParam("path", "app:/" + KEY))
                .andExpect(queryParam("permanently", "true"))
                .andRespond(withNoContent());

        PreparedObjectUpload upload = storage.prepareUpload(
                KEY, "image/jpeg", 12L, Duration.ofMinutes(15)
        );

        assertThat(storage.supportsDirectBrowserUpload()).isTrue();
        assertThat(upload.url()).isEqualTo(URI.create("https://uploader.example.test/temporary-upload"));
        assertThat(upload.method()).isEqualTo("PUT");
        assertThat(upload.requiredHeaders()).containsExactlyEntriesOf(java.util.Map.of(
                "Content-Type", "image/jpeg"
        ));
        assertThat(upload.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));

        assertThat(storage.findObject(KEY)).contains(
                new StoredObjectMetadata(12L, "image/jpeg", "abc123")
        );

        PreparedObjectDownload download = storage.prepareDownload(KEY, Duration.ofMinutes(5));

        assertThat(download.url()).isEqualTo(URI.create("https://downloader.example.test/temporary-download"));
        assertThat(download.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));

        storage.deleteObject(KEY);
        server.verify();
    }

    @Test
    void missingObjectIsReportedAsEmptyAndRepeatedDeleteIsIdempotent() {
        server.expect(requestTo(containsString("/resources?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("path", "app:/" + KEY))
                .andRespond(withResourceNotFound());
        server.expect(requestTo(containsString("/resources?")))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(queryParam("path", "app:/" + KEY))
                .andRespond(withResourceNotFound());

        assertThat(storage.findObject(KEY)).isEmpty();
        storage.deleteObject(KEY);

        server.verify();
    }

    @Test
    void existingParentDirectoriesAreAccepted() {
        server.expect(requestTo(containsString("/resources?")))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("{\"error\":\"DiskPathPointsToExistentDirectoryError\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        expectDirectory("app:/result-issues/1");
        expectDirectory("app:/result-issues/1/2");
        server.expect(requestTo(containsString("/resources/upload?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"href\":\"https://uploader.example.test/upload\",\"method\":\"PUT\"}",
                        MediaType.APPLICATION_JSON
                ));

        assertThat(storage.prepareUpload(KEY, null, 1L, Duration.ofMinutes(1)).requiredHeaders()).isEmpty();
        server.verify();
    }

    private void expectDirectory(String path) {
        server.expect(requestTo(containsString("/resources?")))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(queryParam("path", path))
                .andRespond(withStatus(HttpStatus.CREATED));
    }
}
