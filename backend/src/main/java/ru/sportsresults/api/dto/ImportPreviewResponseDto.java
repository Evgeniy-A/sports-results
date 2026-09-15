package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ImportOperationMode;
import ru.sportsresults.domain.ImportOperationStatus;
import ru.sportsresults.importing.ImportPreviewAction;
import ru.sportsresults.importing.ImportPreviewDecision;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImportPreviewResponseDto(
        UUID operationId,
        ImportOperationStatus operationStatus,
        ImportOperationMode mode,
        List<Long> raceIds,
        String fileSha256,
        long baseRevision,
        String planDigest,
        Instant createdAt,
        Instant expiresAt,
        Totals totals,
        ModeSummary modeSummary,
        boolean blockingErrorsPresent,
        int rowLimit,
        boolean rowsTruncated,
        List<Row> rows,
        List<DuplicateBib> duplicateBibs,
        boolean duplicateGroupsTruncated,
        List<Diagnostic> diagnostics,
        boolean diagnosticsTruncated,
        EmergencySummary emergencySummary
) {
    public ImportPreviewResponseDto {
        raceIds = List.copyOf(raceIds);
        rows = List.copyOf(rows);
        duplicateBibs = List.copyOf(duplicateBibs);
        diagnostics = List.copyOf(diagnostics);
    }

    public record Totals(
            int totalRows,
            int inScopeRows,
            int newCount,
            int unchangedCount,
            int changedCount,
            int ambiguousCount,
            int conflictCount,
            int invalidCount,
            int duplicateCount,
            int outOfScopeCount
    ) {
    }

    public record ModeSummary(
            int wouldInsert,
            int wouldUpdate,
            int existingSkipped,
            int newSkipped,
            int unchanged,
            int blocked
    ) {
    }

    public record Row(
            int sourceRowNumber,
            String bib,
            String participantName,
            RaceRef targetRace,
            Long matchedRegistrationId,
            Long matchedResultId,
            ImportPreviewDecision decision,
            ImportPreviewAction futureAction,
            String reasonCode,
            String reason,
            List<FieldDiff> diffs
    ) {
        public Row {
            diffs = List.copyOf(diffs);
        }
    }

    public record RaceRef(
            Long raceId,
            String raceName
    ) {
    }

    public record FieldDiff(String field, String oldValue, String newValue) {
    }

    public record DuplicateBib(String bib, int count, boolean rowsTruncated, List<DuplicateBibRow> rows) {
        public DuplicateBib {
            rows = List.copyOf(rows);
        }
    }

    public record DuplicateBibRow(
            int sourceRowNumber,
            String participantName,
            String birthDate,
            RaceRef targetRace
    ) {
    }

    public record Diagnostic(
            Integer sourceRowNumber,
            String field,
            String code,
            String message
    ) {
    }

    public record EmergencySummary(
            boolean dangerousOperation,
            EventRef event,
            FileRef file,
            List<EmergencyRaceSummary> races,
            EmergencyTotals totals
    ) {
        public EmergencySummary { races = List.copyOf(races); }
    }

    public record EventRef(String name, String location, Instant startsAt) {
    }

    public record FileRef(String filename, String sha256) {
    }

    public record EmergencyRaceSummary(
            RaceRef race,
            String resultsPublicationStatus,
            int currentCount,
            int sourceCount,
            int wouldRetireCount,
            int wouldInsertCount,
            int activeIssuesWouldArchiveCount
    ) {
    }

    public record EmergencyTotals(
            int currentCount,
            int sourceCount,
            int retireCount,
            int insertCount,
            int activeIssuesArchiveCount,
            int outOfScopeCount,
            int blockingCount
    ) {
    }
}
