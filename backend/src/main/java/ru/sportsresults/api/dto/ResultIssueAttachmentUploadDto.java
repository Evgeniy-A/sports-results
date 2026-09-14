package ru.sportsresults.api.dto;

import ru.sportsresults.domain.AttachmentScanStatus;
import ru.sportsresults.domain.AttachmentUploadStatus;

import java.time.Instant;
import java.util.Map;

public record ResultIssueAttachmentUploadDto(
        Long attachmentId,
        AttachmentUploadStatus uploadStatus,
        AttachmentScanStatus scanStatus,
        String uploadMethod,
        String uploadUrl,
        Map<String, String> requiredHeaders,
        Instant uploadExpiresAt
) {
    public ResultIssueAttachmentUploadDto {
        requiredHeaders = Map.copyOf(requiredHeaders);
    }
}
