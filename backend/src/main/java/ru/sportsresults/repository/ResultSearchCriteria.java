package ru.sportsresults.repository;

import ru.sportsresults.domain.RankingBasis;

public record ResultSearchCriteria(
        Long eventId,
        Long sportFormatId,
        Long raceId,
        String name,
        String bib,
        String gender,
        Long categoryId,
        Long clusterId,
        String status,
        RankingBasis rankingBasis,
        boolean publicOnly
) {
}
