package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpsertSportFormatRequest(
        @Size(max = 100) String code,
        @Size(max = 255) String sourceName,
        @NotBlank @Size(max = 255) String displayName,
        @PositiveOrZero int displayOrder,
        Boolean publicVisible
) {
}
