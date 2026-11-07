package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateResultRequest(
        @NotBlank @Size(max = 64) String status,
        @PositiveOrZero @Max(2_592_000_000L) Long gunTimeMs,
        @PositiveOrZero @Max(2_592_000_000L) Long chipTimeMs,
        @Positive Integer overallPlace,
        @Positive Integer genderPlace,
        @Positive Integer categoryPlace,
        @Positive Integer netOverallPlace,
        @Positive Integer netGenderPlace,
        @Positive Integer netCategoryPlace
) {
}
