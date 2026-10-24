package ru.sportsresults.api.dto;

import ru.sportsresults.domain.CertificateField;

import java.util.Set;

public record CertificateSettingsDto(boolean enabled, Set<CertificateField> allowedFields) {
    public CertificateSettingsDto {
        allowedFields = Set.copyOf(allowedFields);
    }
}
