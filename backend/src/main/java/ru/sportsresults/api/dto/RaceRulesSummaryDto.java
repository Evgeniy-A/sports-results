package ru.sportsresults.api.dto;

import ru.sportsresults.domain.AgeCalculationMode;
import ru.sportsresults.domain.PrimaryStandingMode;
import ru.sportsresults.domain.RankingBasis;

import java.util.List;

public record RaceRulesSummaryDto(
        RankingBasis rankingBasis,
        PrimaryStandingMode primaryStandingMode,
        int absolutePrizePlaces,
        boolean categoryEnabled,
        Integer categoryPrizePlaces,
        Boolean excludeAbsoluteWinnersFromCategory,
        AgeCalculationMode ageCalculationMode,
        List<CategoryRuleDto> categories
) {
    public RaceRulesSummaryDto {
        categories = List.copyOf(categories);
    }
}
