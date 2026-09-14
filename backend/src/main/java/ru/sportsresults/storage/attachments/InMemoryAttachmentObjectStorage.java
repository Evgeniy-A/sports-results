package ru.sportsresults.storage.attachments;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(
        prefix = "app.result-issues.attachments",
        name = "storage-provider",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryAttachmentObjectStorage implements AttachmentObjectStorage {

    private final Map<String, StoredObjectMetadata> objects = new ConcurrentHashMap<>();

    @Override
    public PreparedObjectUpload prepareUpload(
            String storageKey,
            String declaredContentType,
            long declaredSizeBytes,
            Duration lifetime
    ) {
        Instant expiresAt = Instant.now().plus(lifetime);
        return new PreparedObjectUpload(
                signedUri("upload", storageKey),
                "PUT",
                declaredContentType == null ? Map.of() : Map.of("Content-Type", declaredContentType),
                expiresAt
        );
    }

    @Override
    public Optional<StoredObjectMetadata> findObject(String storageKey) {
        return Optional.ofNullable(objects.get(storageKey));
    }

    @Override
    public PreparedObjectDownload prepareDownload(String storageKey, Duration lifetime) {
        return new PreparedObjectDownload(signedUri("download", storageKey), Instant.now().plus(lifetime));
    }

    @Override
    public PreparedObjectDownload prepareDownload(
            String storageKey,
            Duration lifetime,
            String contentDisposition
    ) {
        URI base = signedUri("download", storageKey);
        String suffix = contentDisposition == null || contentDisposition.isBlank()
                ? ""
                : "&response-content-disposition="
                + URLEncoder.encode(contentDisposition, StandardCharsets.UTF_8);
        return new PreparedObjectDownload(URI.create(base + suffix), Instant.now().plus(lifetime));
    }

    @Override
    public void deleteObject(String storageKey) {
        objects.remove(storageKey);
    }

    public void recordUploadedObject(
            String storageKey,
            long sizeBytes,
            String contentType,
            String eTag
    ) {
        objects.put(storageKey, new StoredObjectMetadata(sizeBytes, contentType, eTag));
    }

    public void clear() {
        objects.clear();
    }

    private static URI signedUri(String operation, String storageKey) {
        String encodedKey = URLEncoder.encode(storageKey, StandardCharsets.UTF_8);
        return URI.create("https://object-storage.invalid/" + operation + "/" + encodedKey
                + "?authorization=" + UUID.randomUUID());
    }
}
