package ru.sportsresults.repository;

import java.time.Duration;

import ru.sportsresults.domain.RankingBasis;
import ru.sportsresults.domain.RegistrationEntryKind;

public record ResultListProjection(
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
        Long categoryId,
        String categorySourceName,
        String categoryName,
        String status,
        Duration gunTime,
        Duration chipTime,
        Integer overallPlace,
        Integer genderPlace,
        Integer categoryPlace,
        Integer netOverallPlace,
        Integer netGenderPlace,
        Integer netCategoryPlace,
        Integer contextualPlace,
        RankingBasis rankingBasis
) {
}
