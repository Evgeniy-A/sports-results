package ru.sportsresults.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import ru.sportsresults.domain.PrimaryStandingMode;
import ru.sportsresults.domain.RankingBasis;
import ru.sportsresults.domain.AgeCalculationMode;

public record UpdateAwardPolicyRequest(
        @NotNull RankingBasis rankingBasis,
        @NotNull PrimaryStandingMode primaryStandingMode,
        @PositiveOrZero @Max(1000) int absolutePrizePlaces,
        boolean categoryEnabled,
        @NotNull AgeCalculationMode ageCalculationMode,
        @PositiveOrZero @Max(1000) int categoryPrizePlaces,
        boolean excludeAbsoluteWinnersFromCategory
) {
}
