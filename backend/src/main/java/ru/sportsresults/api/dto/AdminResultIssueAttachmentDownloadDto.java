package ru.sportsresults.api.dto;

import java.time.Instant;

public record AdminResultIssueAttachmentDownloadDto(
        String downloadUrl,
        Instant expiresAt
) {
}
