package ru.sportsresults.api.dto;

import ru.sportsresults.domain.CategoryGender;

public record CategoryRuleDto(
        Long id,
        String name,
        Integer minAge,
        Integer maxAge,
        CategoryGender gender,
        int displayOrder
) {
}
