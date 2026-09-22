package ru.sportsresults.api.dto;

import ru.sportsresults.importing.CanonicalImportField;
import ru.sportsresults.importing.ImportFileType;

import java.util.List;
import java.util.Map;

public record ImportFileAnalysisDto(
        String filename,
        ImportFileType fileType,
        boolean sportsResultsTemplate,
        boolean legacyCsv,
        Integer templateFormatVersion,
        Long metadataEventId,
        String headerSignature,
        List<Sheet> sheets,
        List<Column> columns,
        Map<String, CanonicalImportField> columnMappings,
        List<CanonicalField> canonicalFields,
        List<CanonicalImportField> missingRequiredFields,
        String raceDiscriminatorHeader,
        List<RaceValue> raceValues,
        List<Long> resolvedRaceIds,
        ImportMappingProfileDto suggestedProfile,
        List<Diagnostic> diagnostics,
        boolean readyForValidation
) {
    public ImportFileAnalysisDto {
        sheets = List.copyOf(sheets);
        columns = List.copyOf(columns);
        columnMappings = Map.copyOf(columnMappings);
        canonicalFields = List.copyOf(canonicalFields);
        missingRequiredFields = List.copyOf(missingRequiredFields);
        raceValues = List.copyOf(raceValues);
        resolvedRaceIds = List.copyOf(resolvedRaceIds);
        diagnostics = List.copyOf(diagnostics);
    }

    public record Sheet(String name, int rowCount, Long raceId) {}
    public record Column(
            String header,
            String normalizedHeader,
            CanonicalImportField mappedField,
            boolean automatic,
            List<CanonicalImportField> candidates
    ) {}
    public record CanonicalField(CanonicalImportField field, String displayName, boolean required) {}
    public record RaceValue(String sourceValue, Long raceId, String raceName, boolean automatic) {}
    public record Diagnostic(String sheet, Integer row, String column, String code, String message) {}
}
