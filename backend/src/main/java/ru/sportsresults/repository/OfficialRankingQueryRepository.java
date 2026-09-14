package ru.sportsresults.repository;

import java.util.Collection;
import java.util.List;

public interface OfficialRankingQueryRepository {
    List<OfficialRankingProjection> findAchievements(
            Long eventId,
            Collection<Long> raceIds,
            Collection<Long> resultIds
    );
}
