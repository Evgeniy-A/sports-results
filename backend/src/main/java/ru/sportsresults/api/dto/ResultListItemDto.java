package ru.sportsresults.api.dto;

import java.util.List;
import ru.sportsresults.domain.RegistrationEntryKind;

public record ResultListItemDto(
        Long registrationId,
        Long resultId,
        Long raceId,
        String raceName,
        String displayName,
        String firstName,
        String lastName,
        String bib,
        String gender,
        String sourceCategory,
        Long clusterId,
        String clusterName,
        RegistrationEntryKind entryKind,
        CategoryDto category,
        String status,
        Long gunTimeMs,
        Long chipTimeMs,
        Integer overallPlace,
        Integer genderPlace,
        Integer categoryPlace,
        Integer netOverallPlace,
        Integer netGenderPlace,
        Integer netCategoryPlace,
        Integer place,
        Integer displayPosition,
        String rankingBasis,
        List<RankingAchievementDto> rankingAchievements
) {
    public ResultListItemDto {
        rankingAchievements = List.copyOf(rankingAchievements);
    }
}
