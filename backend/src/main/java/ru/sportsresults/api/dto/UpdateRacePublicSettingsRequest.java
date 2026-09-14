package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotNull;
import ru.sportsresults.domain.RankingBasis;

public record UpdateRacePublicSettingsRequest(
        @NotNull RankingBasis publicRankingBasis
) {
}
