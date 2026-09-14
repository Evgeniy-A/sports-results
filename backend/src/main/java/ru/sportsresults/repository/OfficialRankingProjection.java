package ru.sportsresults.repository;

import ru.sportsresults.domain.RankingAchievementType;

public record OfficialRankingProjection(
        Long resultId,
        int place,
        RankingAchievementType type,
        Long categoryId,
        String categorySourceName,
        String categoryDisplayName,
        String gender,
        boolean prize
) {
}
