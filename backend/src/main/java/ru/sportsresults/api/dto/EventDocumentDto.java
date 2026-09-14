package ru.sportsresults.api.dto;

import ru.sportsresults.domain.EventDocumentType;

import java.time.Instant;

public record EventDocumentDto(
        Long id,
        EventDocumentType type,
        String displayName,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String sha256,
        int displayOrder,
        boolean publicDocument,
        Instant uploadedAt,
        String contentUrl
) {
}
