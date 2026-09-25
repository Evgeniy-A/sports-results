package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.sportsresults.domain.RegistrationEntryKind;

import java.time.LocalDate;

public record UpdateRegistrationRequest(
        @NotBlank @Size(max = 320) String displayName,
        @Size(max = 160) String firstName,
        @Size(max = 160) String lastName,
        LocalDate birthDate,
        @Size(max = 32) String gender,
        @Size(max = 64) String bib,
        @Size(max = 255) String sourceCategory,
        Long clusterId,
        @NotNull RegistrationEntryKind entryKind,
        Long categoryId
) {
}
