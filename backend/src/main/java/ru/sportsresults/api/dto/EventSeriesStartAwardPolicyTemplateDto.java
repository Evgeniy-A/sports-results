package ru.sportsresults.api.dto;

import ru.sportsresults.domain.AgeCalculationMode;
import ru.sportsresults.domain.PrimaryStandingMode;
import ru.sportsresults.domain.RankingBasis;

public record EventSeriesStartAwardPolicyTemplateDto(
        Long id,
        RankingBasis rankingBasis,
        PrimaryStandingMode primaryStandingMode,
        int absolutePrizePlaces,
        boolean categoryEnabled,
        AgeCalculationMode ageCalculationMode,
        int categoryPrizePlaces,
        boolean excludeAbsoluteWinnersFromCategory
) {
}
