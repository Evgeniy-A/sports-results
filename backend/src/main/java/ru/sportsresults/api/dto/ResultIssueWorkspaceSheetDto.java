package ru.sportsresults.api.dto;

import java.time.Instant;

public record ResultIssueWorkspaceSheetDto(
        Long seriesId,
        String seriesName,
        int year,
        long issueCount,
        long pendingSyncCount,
        long newCount,
        long inProgressCount,
        Instant lastSyncedAt
) {
}
