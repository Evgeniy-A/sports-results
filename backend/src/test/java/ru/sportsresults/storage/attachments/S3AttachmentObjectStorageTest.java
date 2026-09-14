package ru.sportsresults.storage.attachments;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.sportsresults.config.S3AttachmentStorageProperties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3AttachmentObjectStorageTest {

    private static final String BUCKET = "private-result-issues";
    private static final String KEY = "result-issues/17/23/9a648e0f";

    private S3Client s3Client;
    private S3Presigner presigner;
    private S3AttachmentObjectStorage storage;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        presigner = S3Presigner.builder()
                .endpointOverride(URI.create("https://objects.example.test"))
                .region(Region.of("ru-central1"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access", "test-secret-value")
                ))
                .serviceConfiguration(serviceConfiguration)
                .build();
        storage = new S3AttachmentObjectStorage(
                s3Client,
                presigner,
                new S3AttachmentStorageProperties(
                        "https://objects.example.test", "ru-central1", BUCKET,
                        "test-access", "test-secret-value", true
                )
        );
    }

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    @Test
    void createsPathStylePresignedPutAndGetForCustomEndpointAndRegion() {
        var upload = storage.prepareUpload(KEY, "video/mp4", 700_000_000L, Duration.ofMinutes(15));

        String decodedUrl = URLDecoder.decode(upload.url().toString(), StandardCharsets.UTF_8);
        assertThat(storage.supportsDirectBrowserUpload()).isTrue();
        assertThat(upload.method()).isEqualTo("PUT");
        assertThat(upload.url().getHost()).isEqualTo("objects.example.test");
        assertThat(upload.url().getPath()).isEqualTo("/" + BUCKET + "/" + KEY);
        assertThat(decodedUrl).contains("test-access/").contains("/ru-central1/s3/");
        assertThat(decodedUrl).doesNotContain("test-secret-value");
        assertThat(upload.requiredHeaders()).anySatisfy((name, value) -> {
            assertThat(name).isEqualToIgnoringCase("content-type");
            assertThat(value).isEqualTo("video/mp4");
        });

        var download = storage.prepareDownload(KEY, Duration.ofMinutes(5));
        assertThat(download.url().getHost()).isEqualTo("objects.example.test");
        assertThat(download.url().getPath()).isEqualTo("/" + BUCKET + "/" + KEY);
        assertThat(URLDecoder.decode(download.url().toString(), StandardCharsets.UTF_8))
                .contains("/ru-central1/s3/")
                .doesNotContain("test-secret-value");
    }

    @Test
    void sharedGetKeepsRangeAvailableAndOverridesSafeContentDisposition() {
        var download = storage.prepareDownload(
                KEY,
                Duration.ofMinutes(5),
                "inline; filename*=UTF-8''finish-video.mp4"
        );

        String decodedUrl = URLDecoder.decode(download.url().toString(), StandardCharsets.UTF_8);
        assertThat(decodedUrl)
                .contains("response-content-disposition=inline")
                .contains("finish-video.mp4")
                .containsIgnoringCase("X-Amz-SignedHeaders=host")
                .doesNotContainIgnoringCase("X-Amz-SignedHeaders=host;range");
    }

    @Test
    void readsActualObjectMetadataWithHeadAndDeletesOnlyTheBackendKey() {
        when(s3Client.headObject(org.mockito.ArgumentMatchers.any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(42L)
                        .contentType("image/jpeg")
                        .eTag("etag-42")
                        .build());

        assertThat(storage.findObject(KEY)).contains(
                new StoredObjectMetadata(42L, "image/jpeg", "etag-42")
        );
        ArgumentCaptor<HeadObjectRequest> headCaptor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(headCaptor.capture());
        assertThat(headCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(headCaptor.getValue().key()).isEqualTo(KEY);

        storage.deleteObject(KEY);
        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(deleteCaptor.getValue().key()).isEqualTo(KEY);
    }

    @Test
    void treatsOnlyNotFoundHeadAsAnAbsentObject() {
        when(s3Client.headObject(org.mockito.ArgumentMatchers.any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("not found").build());

        assertThat(storage.findObject(KEY)).isEmpty();
    }
}
