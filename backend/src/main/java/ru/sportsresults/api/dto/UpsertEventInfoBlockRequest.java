package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpsertEventInfoBlockRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content,
        @PositiveOrZero int displayOrder
) {
}
