package ru.sportsresults.api.dto;

import java.util.List;

public record AdminResultIssueResultDto(
        Long resultId,
        String status,
        Long gunTimeMs,
        Long chipTimeMs,
        Integer overallPlace,
        Integer genderPlace,
        Integer categoryPlace,
        Integer netOverallPlace,
        Integer netGenderPlace,
        Integer netCategoryPlace,
        List<RankingAchievementDto> rankingAchievements
) {
    public AdminResultIssueResultDto {
        rankingAchievements = List.copyOf(rankingAchievements);
    }
}
