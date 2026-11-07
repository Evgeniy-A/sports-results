package ru.sportsresults.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateResultIssueWorkItemRequest(
        @PositiveOrZero long expectedVersion,
        @Size(max = 64) String desiredResultStatus,
        @PositiveOrZero @Max(2_592_000_000L) Long desiredGunTimeMs,
        @PositiveOrZero @Max(2_592_000_000L) Long desiredChipTimeMs,
        @Positive Long assigneeUserId,
        @Size(max = 4000) String internalComment
) {
}
