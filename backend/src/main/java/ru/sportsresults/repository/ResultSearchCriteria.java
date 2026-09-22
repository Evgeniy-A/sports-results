package ru.sportsresults.repository;

import ru.sportsresults.domain.RankingBasis;

import java.util.List;

public record ResultSearchCriteria(
        Long eventId,
        Long raceId,
        String name,
        String bib,
        String gender,
        Long categoryId,
        Long clusterId,
        String status,
        RankingBasis rankingBasis,
        boolean publicOnly,
        List<Long> excludedResultIds
) {
    public ResultSearchCriteria {
        excludedResultIds = excludedResultIds == null ? List.of() : List.copyOf(excludedResultIds);
    }
}
