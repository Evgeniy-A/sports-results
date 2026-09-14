package ru.sportsresults.storage.attachments;

public record StoredObjectMetadata(
        long sizeBytes,
        String contentType,
        String eTag
) {
}
