package ru.sportsresults.api.dto;

import ru.sportsresults.domain.RegistrationEntryKind;

import java.time.LocalDate;

public record AdminResultIssueRegistrationDto(
        Long registrationId,
        String bib,
        String displayName,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String gender,
        String sourceCategory,
        RegistrationEntryKind entryKind,
        CategoryDto effectiveCategory,
        Long raceId,
        String raceName,
        Long sportFormatId,
        String sportFormatName
) {
}
