package ru.sportsresults.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.sportsresults.domain.CategoryGender;

public record UpsertCategoryRequest(
        @NotBlank @Size(max = 255) String sourceName,
        @NotBlank @Size(max = 255) String displayName,
        @Min(0) @Max(150) Integer minAge,
        @Min(0) @Max(150) Integer maxAge,
        CategoryGender gender,
        @PositiveOrZero int displayOrder,
        boolean enabled
) {
}
