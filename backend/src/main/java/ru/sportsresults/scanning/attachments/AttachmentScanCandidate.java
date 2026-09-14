package ru.sportsresults.scanning.attachments;

public record AttachmentScanCandidate(
        Long attachmentId,
        String storageKey,
        String declaredContentType,
        long sizeBytes
) {
}
