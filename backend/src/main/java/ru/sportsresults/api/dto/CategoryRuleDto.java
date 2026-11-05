package ru.sportsresults.api.dto;

import ru.sportsresults.domain.CategoryGender;
import ru.sportsresults.domain.RankingBasis;

public record CategoryRuleDto(
        Long id,
        String name,
        Integer minAge,
        Integer maxAge,
        CategoryGender gender,
        RankingBasis rankingBasis,
        int displayOrder
) {
}
