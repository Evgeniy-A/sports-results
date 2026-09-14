package ru.sportsresults.repository;

import ru.sportsresults.domain.AttachmentScanStatus;
import ru.sportsresults.domain.AttachmentUploadStatus;

import java.time.Instant;

public record ResultIssueAttachmentExportProjection(
        Long attachmentId,
        Long issueId,
        String originalFileName,
        String contentType,
        String detectedContentType,
        long sizeBytes,
        AttachmentUploadStatus uploadStatus,
        AttachmentScanStatus scanStatus,
        Instant deletedAt
) {
    public boolean shareable() {
        return uploadStatus == AttachmentUploadStatus.UPLOADED
                && scanStatus == AttachmentScanStatus.CLEAN
                && deletedAt == null;
    }
}
