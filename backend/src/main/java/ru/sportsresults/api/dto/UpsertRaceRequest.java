package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.sportsresults.domain.RaceEntryMode;

import java.math.BigDecimal;
import java.time.Instant;

public record UpsertRaceRequest(
        @NotBlank @Size(max = 255) String sourceCode,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 160)
        @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "must be a lowercase URL slug")
        String slug,
        @PositiveOrZero BigDecimal distanceMeters,
        Instant startsAt,
        @NotNull RaceEntryMode entryMode,
        @PositiveOrZero int displayOrder,
        Long sportFormatId,
        Boolean publicVisible
) {
}
