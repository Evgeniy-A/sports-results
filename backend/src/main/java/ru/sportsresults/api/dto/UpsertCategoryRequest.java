package ru.sportsresults.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.sportsresults.domain.CategoryGender;
import ru.sportsresults.domain.RankingBasis;

public record UpsertCategoryRequest(
        @NotBlank @Size(max = 255) String sourceName,
        @NotBlank @Size(max = 255) String displayName,
        @Min(0) @Max(150) Integer minAge,
        @Min(0) @Max(150) Integer maxAge,
        CategoryGender gender,
        @PositiveOrZero int displayOrder,
        boolean enabled,
        RankingBasis rankingBasisOverride
) {
    public UpsertCategoryRequest(
            String sourceName,
            String displayName,
            Integer minAge,
            Integer maxAge,
            CategoryGender gender,
            int displayOrder,
            boolean enabled
    ) {
        this(sourceName, displayName, minAge, maxAge, gender, displayOrder, enabled, null);
    }
}
