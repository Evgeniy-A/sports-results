package ru.sportsresults.api.dto;

import ru.sportsresults.domain.CategoryGender;

public record AdminCategoryDto(
        Long id,
        Long raceId,
        String sourceName,
        String displayName,
        Integer minAge,
        Integer maxAge,
        CategoryGender gender,
        int displayOrder,
        boolean enabled
) {
}
