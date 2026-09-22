package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.sportsresults.importing.CanonicalImportField;
import ru.sportsresults.importing.ImportFileType;

import java.util.Map;

public record UpsertImportMappingProfileRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull ImportFileType fileType,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String headerSignature,
        @NotEmpty Map<@NotBlank @Size(max = 255) String, @NotNull CanonicalImportField> mappings,
        @Size(max = 255) String raceDiscriminatorHeader
) {}
