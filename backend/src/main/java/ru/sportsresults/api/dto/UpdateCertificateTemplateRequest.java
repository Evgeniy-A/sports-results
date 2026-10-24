package ru.sportsresults.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateCertificateTemplateRequest(
        @NotBlank @Size(max = 255) String displayName,
        boolean active,
        @PositiveOrZero int displayOrder,
        @NotNull Set<Long> raceIds,
        @NotNull @Valid CertificateTemplateLayoutDto layout
) {
}
