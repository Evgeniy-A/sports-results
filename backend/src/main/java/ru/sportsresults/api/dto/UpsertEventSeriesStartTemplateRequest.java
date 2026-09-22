package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpsertEventSeriesStartTemplateRequest(
        @NotBlank @Size(max = 255) String name,
        @PositiveOrZero BigDecimal distanceMeters,
        Boolean publicVisible
) {
}
