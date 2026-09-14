package ru.sportsresults.storage.attachments;

import java.time.Duration;
import java.util.Optional;

public interface AttachmentObjectStorage {
    default boolean supportsDirectBrowserUpload() {
        return false;
    }

    PreparedObjectUpload prepareUpload(
            String storageKey,
            String declaredContentType,
            long declaredSizeBytes,
            Duration lifetime
    );

    Optional<StoredObjectMetadata> findObject(String storageKey);

    PreparedObjectDownload prepareDownload(String storageKey, Duration lifetime);

    default PreparedObjectDownload prepareDownload(
            String storageKey,
            Duration lifetime,
            String contentDisposition
    ) {
        return prepareDownload(storageKey, lifetime);
    }

    void deleteObject(String storageKey);
}
