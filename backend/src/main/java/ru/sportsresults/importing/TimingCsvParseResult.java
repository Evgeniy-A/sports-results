package ru.sportsresults.importing;

import java.util.List;

public record TimingCsvParseResult(
        int totalRows,
        List<TimingResultImportRow> rows,
        List<TimingCsvRowError> errors
) {
    public TimingCsvParseResult {
        rows = List.copyOf(rows);
        errors = List.copyOf(errors);
    }
}
