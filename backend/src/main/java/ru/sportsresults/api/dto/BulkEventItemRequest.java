package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BulkEventItemRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String location,
        @NotBlank @Size(max = 64) String timeZone
) {
}
