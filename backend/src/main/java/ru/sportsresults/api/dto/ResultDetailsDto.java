package ru.sportsresults.api.dto;

import ru.sportsresults.domain.RegistrationEntryKind;

import java.util.List;

public record ResultDetailsDto(
        Long registrationId,
        Long resultId,
        Long eventId,
        Long raceId,
        String raceName,
        String displayName,
        String firstName,
        String lastName,
        String bib,
        String gender,
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
        String rankingBasis,
        List<RankingAchievementDto> rankingAchievements,
        List<SplitDto> splits
) {
    public ResultDetailsDto {
        rankingAchievements = List.copyOf(rankingAchievements);
        splits = List.copyOf(splits);
    }
}
