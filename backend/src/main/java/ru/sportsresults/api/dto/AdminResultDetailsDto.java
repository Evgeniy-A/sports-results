package ru.sportsresults.api.dto;

import ru.sportsresults.domain.RegistrationEntryKind;

import java.time.LocalDate;
import java.util.List;

public record AdminResultDetailsDto(
        Long registrationId,
        Long resultId,
        Long eventId,
        Long raceId,
        String raceName,
        String displayName,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String sourceCategory,
        String bib,
        String gender,
        RegistrationEntryKind entryKind,
        Long clusterId,
        String clusterName,
        CategoryDto effectiveCategory,
        String status,
        Long gunTimeMs,
        Long chipTimeMs,
        Integer overallPlace,
        Integer genderPlace,
        Integer categoryPlace,
        Integer netOverallPlace,
        Integer netGenderPlace,
        Integer netCategoryPlace,
        List<RankingAchievementDto> rankingAchievements,
        List<SplitDto> splits
) {
    public AdminResultDetailsDto {
        rankingAchievements = List.copyOf(rankingAchievements);
        splits = List.copyOf(splits);
    }
}
