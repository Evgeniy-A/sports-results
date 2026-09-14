package ru.sportsresults.service;

import ru.sportsresults.domain.AgeCalculationMode;
import ru.sportsresults.domain.CategoryGender;
import ru.sportsresults.domain.ResultsPublicationStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

record ImportPreviewDatabaseSnapshot(
        long resultDataRevision,
        List<RaceSnapshot> races,
        List<ClusterSnapshot> clusters,
        List<RegistrationSnapshot> registrations,
        Instant eventStartsAt,
        String eventTimeZone,
        List<CategorySnapshot> categories,
        String eventName,
        String eventLocation,
        List<ActiveIssueSnapshot> activeIssues
) {
    ImportPreviewDatabaseSnapshot {
        races = List.copyOf(races);
        clusters = List.copyOf(clusters);
        registrations = List.copyOf(registrations);
        categories = List.copyOf(categories);
        activeIssues = List.copyOf(activeIssues);
    }

    ImportPreviewDatabaseSnapshot(
            long resultDataRevision,
            List<RaceSnapshot> races,
            List<ClusterSnapshot> clusters,
            List<RegistrationSnapshot> registrations
    ) {
        this(resultDataRevision, races, clusters, registrations, null, "UTC", List.of(), null, null, List.of());
    }

    record RaceSnapshot(
            Long id,
            String sourceCode,
            String name,
            Long sportFormatId,
            String sportFormatName,
            AgeCalculationMode ageCalculationMode,
            ResultsPublicationStatus resultsPublicationStatus
    ) {
        RaceSnapshot(Long id, String sourceCode, String name, Long sportFormatId, String sportFormatName) {
            this(id, sourceCode, name, sportFormatId, sportFormatName,
                    AgeCalculationMode.EVENT_DATE, ResultsPublicationStatus.PUBLISHED);
        }

        RaceSnapshot(
                Long id,
                String sourceCode,
                String name,
                Long sportFormatId,
                String sportFormatName,
                AgeCalculationMode ageCalculationMode
        ) {
            this(id, sourceCode, name, sportFormatId, sportFormatName,
                    ageCalculationMode, ResultsPublicationStatus.PUBLISHED);
        }
    }

    record ClusterSnapshot(
            Long id,
            Long raceId,
            String code,
            String sourceName,
            String displayName
    ) {
    }

    record RegistrationSnapshot(
            Long id,
            String bib,
            String displayName,
            String firstName,
            String lastName,
            String gender,
            LocalDate birthDate,
            String sourceCategory,
            RaceSnapshot race,
            ClusterSnapshot cluster,
            CategorySnapshot category,
            ResultSnapshot result
    ) {
        RegistrationSnapshot(
                Long id,
                String bib,
                String displayName,
                String firstName,
                String lastName,
                String gender,
                LocalDate birthDate,
                String sourceCategory,
                RaceSnapshot race,
                ClusterSnapshot cluster,
                ResultSnapshot result
        ) {
            this(id, bib, displayName, firstName, lastName, gender, birthDate, sourceCategory,
                    race, cluster, null, result);
        }
    }

    record CategorySnapshot(
            Long id,
            Long raceId,
            String sourceName,
            String displayName,
            int displayOrder,
            Integer minAge,
            Integer maxAge,
            CategoryGender gender,
            boolean enabled
    ) {
    }

    record ResultSnapshot(
            Long id,
            String status,
            Duration gunTime,
            Duration chipTime,
            Integer overallPlace,
            Integer genderPlace,
            Integer categoryPlace,
            Integer netOverallPlace,
            Integer netGenderPlace,
            Integer netCategoryPlace
    ) {
    }

    record ActiveIssueSnapshot(Long id, Long registrationId, Long raceId) {
    }
}
