package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultRecalculationOperationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResultRecalculationApplyDto(
        UUID operationId,
        ResultRecalculationOperationStatus status,
        Long eventId,
        List<Long> raceIds,
        int changedCategoryCount,
        Instant appliedAt,
        long newRevision
) {
}
