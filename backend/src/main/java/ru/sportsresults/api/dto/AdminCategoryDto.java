package ru.sportsresults.api.dto;

import ru.sportsresults.domain.CategoryGender;
import ru.sportsresults.domain.RankingBasis;

public record AdminCategoryDto(
        Long id,
        Long raceId,
        String sourceName,
        String displayName,
        Integer minAge,
        Integer maxAge,
        CategoryGender gender,
        int displayOrder,
        boolean enabled,
        boolean inUse,
        RankingBasis rankingBasisOverride
) {
}
