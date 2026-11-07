package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultIssueWorkSyncStatus;

import java.time.Instant;

public record ResultIssueWorkItemDto(
        Long workItemId,
        Long issueId,
        Long resultId,
        String desiredResultStatus,
        Long desiredGunTimeMs,
        Long desiredChipTimeMs,
        Long assigneeUserId,
        String assigneeName,
        String internalComment,
        ResultIssueWorkSyncStatus syncStatus,
        String syncErrorCode,
        long version,
        Instant updatedAt,
        String updatedBy,
        Instant syncedAt,
        String syncedBy
) {
}
