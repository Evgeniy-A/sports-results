package ru.sportsresults.service;

import ru.sportsresults.domain.AttachmentScanStatus;
import ru.sportsresults.domain.AttachmentUploadStatus;

import java.time.Instant;

public record AttachmentScanExecution(
        Long attachmentId,
        AttachmentUploadStatus uploadStatus,
        AttachmentScanStatus scanStatus,
        Instant scannedAt
) {
}
