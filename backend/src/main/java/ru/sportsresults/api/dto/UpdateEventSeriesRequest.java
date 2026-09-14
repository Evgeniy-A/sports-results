package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateEventSeriesRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 160)
        @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "must be a lowercase URL slug")
        String slug,
        String description,
        boolean active
) {
}
