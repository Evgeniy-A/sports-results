package ru.sportsresults.api.dto;

import ru.sportsresults.domain.AttachmentScanStatus;
import ru.sportsresults.domain.AttachmentUploadStatus;

import java.time.Instant;

public record ResultIssueAttachmentStatusDto(
        Long attachmentId,
        AttachmentUploadStatus uploadStatus,
        AttachmentScanStatus scanStatus,
        Instant uploadedAt
) {
}
