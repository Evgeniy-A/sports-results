package ru.sportsresults.api.dto;

import ru.sportsresults.domain.AttachmentScanStatus;
import ru.sportsresults.domain.AttachmentUploadStatus;

import java.time.Instant;

public record DevResultIssueAttachmentScanDto(
        Long attachmentId,
        AttachmentUploadStatus uploadStatus,
        AttachmentScanStatus scanStatus,
        Instant scannedAt
) {
}
