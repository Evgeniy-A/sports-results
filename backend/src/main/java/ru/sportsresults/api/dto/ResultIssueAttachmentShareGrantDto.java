package ru.sportsresults.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ResultIssueAttachmentShareGrantDto(
        UUID grantId,
        UUID batchId,
        Long attachmentId,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        String revokedBy,
        Instant lastAccessedAt,
        long accessCount
) {
}
