package ru.sportsresults.api.dto;

import ru.sportsresults.domain.PrimaryStandingMode;
import ru.sportsresults.domain.RankingBasis;
import ru.sportsresults.domain.AgeCalculationMode;

public record AwardPolicyDto(
        Long id,
        Long raceId,
        RankingBasis rankingBasis,
        PrimaryStandingMode primaryStandingMode,
        int absolutePrizePlaces,
        boolean categoryEnabled,
        AgeCalculationMode ageCalculationMode,
        int categoryPrizePlaces,
        boolean excludeAbsoluteWinnersFromCategory
) {
}
