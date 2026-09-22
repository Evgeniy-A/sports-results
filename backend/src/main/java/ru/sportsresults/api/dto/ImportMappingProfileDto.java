package ru.sportsresults.api.dto;

import ru.sportsresults.importing.CanonicalImportField;
import ru.sportsresults.importing.ImportFileType;

import java.time.Instant;
import java.util.Map;

public record ImportMappingProfileDto(
        Long id,
        String name,
        ImportFileType fileType,
        String headerSignature,
        Map<String, CanonicalImportField> mappings,
        String raceDiscriminatorHeader,
        Instant createdAt,
        Instant updatedAt
) {}
