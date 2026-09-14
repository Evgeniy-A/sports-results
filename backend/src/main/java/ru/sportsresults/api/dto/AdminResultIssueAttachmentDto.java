package ru.sportsresults.api.dto;

import ru.sportsresults.domain.AttachmentScanStatus;
import ru.sportsresults.domain.AttachmentUploadStatus;

import java.time.Instant;

public record AdminResultIssueAttachmentDto(
        Long attachmentId,
        String originalFileName,
        String contentType,
        String detectedContentType,
        long sizeBytes,
        AttachmentUploadStatus uploadStatus,
        AttachmentScanStatus scanStatus,
        Instant createdAt,
        Instant uploadedAt,
        Instant scannedAt,
        Instant deletedAt
) {
}
