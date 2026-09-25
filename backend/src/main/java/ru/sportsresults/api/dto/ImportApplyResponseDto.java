package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ImportOperationMode;
import ru.sportsresults.domain.ImportOperationStatus;

import java.time.Instant;
import java.util.UUID;

public record ImportApplyResponseDto(
        UUID operationId,
        ImportOperationStatus status,
        ImportOperationMode mode,
        Long eventId,
        Long importBatchId,
        int insertedCount,
        int updatedCount,
        int resultCreatedCount,
        int newSkippedCount,
        int existingSkippedCount,
        int unchangedCount,
        int outOfScopeCount,
        int retiredCount,
        int archivedIssueCount,
        long newRevision,
        Instant appliedAt,
        boolean noOp
) {
}
