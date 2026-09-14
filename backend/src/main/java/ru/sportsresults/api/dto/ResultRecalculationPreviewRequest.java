package ru.sportsresults.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ResultRecalculationPreviewRequest(
        @NotEmpty List<@NotNull Long> raceIds,
        @Min(0) @Max(500) Integer rowLimit
) {
}
