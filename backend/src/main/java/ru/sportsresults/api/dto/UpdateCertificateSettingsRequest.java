package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotNull;
import ru.sportsresults.domain.CertificateField;

import java.util.Set;

public record UpdateCertificateSettingsRequest(boolean enabled, @NotNull Set<CertificateField> allowedFields) {
}
