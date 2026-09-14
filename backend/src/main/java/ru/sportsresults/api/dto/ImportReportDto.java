package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ImportBatchStatus;

import java.util.List;

public record ImportReportDto(
        Long batchId,
        ImportBatchStatus status,
        int totalRows,
        int importedRows,
        int skippedRows,
        int failedRows,
        boolean duplicateFile,
        String warning,
        List<ImportErrorDto> errors
) {
    public ImportReportDto {
        errors = List.copyOf(errors);
    }
}
