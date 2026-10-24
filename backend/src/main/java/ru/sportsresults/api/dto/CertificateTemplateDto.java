package ru.sportsresults.api.dto;

import java.time.Instant;
import java.util.Set;

public record CertificateTemplateDto(
        Long id,
        String displayName,
        boolean active,
        int displayOrder,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String sha256,
        Instant uploadedAt,
        String uploadedBy,
        Set<Long> raceIds,
        CertificateTemplateLayoutDto layout
) {
    public CertificateTemplateDto {
        raceIds = Set.copyOf(raceIds);
    }
}
