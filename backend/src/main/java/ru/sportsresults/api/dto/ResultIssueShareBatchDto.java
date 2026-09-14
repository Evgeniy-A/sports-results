package ru.sportsresults.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ResultIssueShareBatchDto(
        UUID batchId,
        String createdBy,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        String revokedBy,
        long matchedIssueCount,
        long grantCount,
        long accessedGrantCount
) {
}
