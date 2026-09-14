package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record UpsertEventScheduleItemRequest(
        @NotNull LocalDateTime startsAt,
        LocalDateTime endsAt,
        @NotBlank @Size(max = 255) String title,
        String description,
        @PositiveOrZero int displayOrder
) {
}
