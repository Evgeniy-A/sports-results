package ru.sportsresults.api.dto;

import ru.sportsresults.domain.CategoryGender;

public record EventSeriesStartCategoryTemplateDto(
        Long id,
        Long templateStartId,
        String sourceName,
        String displayName,
        Integer minAge,
        Integer maxAge,
        CategoryGender gender,
        int displayOrder,
        boolean enabled
) {
}
