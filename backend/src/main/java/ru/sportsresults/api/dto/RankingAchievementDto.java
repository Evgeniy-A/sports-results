package ru.sportsresults.api.dto;

import ru.sportsresults.domain.RankingAchievementType;

public record RankingAchievementDto(
        int place,
        RankingAchievementType type,
        String code,
        String name,
        String label,
        boolean prize
) {
}
