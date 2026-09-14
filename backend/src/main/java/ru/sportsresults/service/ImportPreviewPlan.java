package ru.sportsresults.service;

import ru.sportsresults.api.dto.ImportPreviewResponseDto;

import java.util.List;

record ImportPreviewPlan(
        ImportPreviewResponseDto.Totals totals,
        ImportPreviewResponseDto.ModeSummary modeSummary,
        boolean blockingErrorsPresent,
        List<ImportPreviewResponseDto.Row> rows,
        List<ImportPreviewResponseDto.DuplicateBib> duplicateBibs,
        List<ImportPreviewResponseDto.Diagnostic> diagnostics,
        String planDigest,
        ImportPreviewResponseDto.EmergencySummary emergencySummary
) {
    ImportPreviewPlan {
        rows = List.copyOf(rows);
        duplicateBibs = List.copyOf(duplicateBibs);
        diagnostics = List.copyOf(diagnostics);
    }
}
