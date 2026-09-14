package ru.sportsresults.api.dto;

import ru.sportsresults.domain.RankingBasis;

public record RacePublicSettingsDto(
        Long raceId,
        RankingBasis publicRankingBasis
) {
}
