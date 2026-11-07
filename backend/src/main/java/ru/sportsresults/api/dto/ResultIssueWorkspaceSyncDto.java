package ru.sportsresults.api.dto;

import java.time.Instant;

public record ResultIssueWorkspaceSyncDto(
        int attempted,
        int applied,
        int conflicts,
        int errors,
        int remainingPending,
        Instant completedAt
) {
}
