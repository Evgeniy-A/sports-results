package ru.sportsresults.api.dto;

import ru.sportsresults.importing.ManualConflictDecision;

public record ManualConflictResolutionDto(
        Integer sourceRowNumber,
        String field,
        ManualConflictDecision decision
) {
}
