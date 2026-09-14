package ru.sportsresults.storage.attachments;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.sportsresults.config.S3AttachmentStorageProperties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(
        prefix = "app.result-issues.attachments",
        name = "storage-provider",
        havingValue = "s3"
)
public class S3AttachmentObjectStorage implements AttachmentObjectStorage {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3AttachmentObjectStorage(
            S3Client s3Client,
            S3Presigner presigner,
            S3AttachmentStorageProperties properties
    ) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.bucket = properties.bucket();
    }

    @Override
    public boolean supportsDirectBrowserUpload() {
        return true;
    }

    @Override
    public PreparedObjectUpload prepareUpload(
            String storageKey,
            String declaredContentType,
            long declaredSizeBytes,
            Duration lifetime
    ) {
        PutObjectRequest.Builder objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey);
        if (declaredContentType != null) {
            objectRequest.contentType(declaredContentType);
        }
        PresignedPutObjectRequest presigned = presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(lifetime)
                        .putObjectRequest(objectRequest.build())
                        .build()
        );
        return new PreparedObjectUpload(
                URI.create(presigned.url().toString()),
                presigned.httpRequest().method().name(),
                browserHeaders(presigned.signedHeaders()),
                Instant.now().plus(lifetime)
        );
    }

    @Override
    public Optional<StoredObjectMetadata> findObject(String storageKey) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .build());
            return Optional.of(new StoredObjectMetadata(
                    response.contentLength(), response.contentType(), response.eTag()
            ));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    @Override
    public PreparedObjectDownload prepareDownload(String storageKey, Duration lifetime) {
        return prepareDownload(storageKey, lifetime, null);
    }

    @Override
    public PreparedObjectDownload prepareDownload(
            String storageKey,
            Duration lifetime,
            String contentDisposition
    ) {
        GetObjectRequest.Builder objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey);
        if (contentDisposition != null && !contentDisposition.isBlank()) {
            objectRequest.responseContentDisposition(contentDisposition);
        }
        PresignedGetObjectRequest presigned = presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(lifetime)
                        .getObjectRequest(objectRequest.build())
                        .build()
        );
        return new PreparedObjectDownload(URI.create(presigned.url().toString()), Instant.now().plus(lifetime));
    }

    @Override
    public void deleteObject(String storageKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build());
    }

    private static Map<String, String> browserHeaders(Map<String, java.util.List<String>> signedHeaders) {
        Map<String, String> headers = new LinkedHashMap<>();
        signedHeaders.forEach((name, values) -> {
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (!normalizedName.equals("host") && !normalizedName.equals("content-length")) {
                headers.put(name, String.join(",", values));
            }
        });
        return Map.copyOf(headers);
    }
}
