package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import ru.sportsresults.api.dto.ImportPreviewResponseDto;
import ru.sportsresults.domain.ImportOperation;
import ru.sportsresults.domain.ImportOperationMode;
import ru.sportsresults.importing.TimingCsvFormatException;
import ru.sportsresults.importing.TimingCsvParseResult;
import ru.sportsresults.importing.TimingCsvParser;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ImportPreviewService {

    private static final int MAX_FILE_BYTES = 25 * 1024 * 1024;
    private static final int DEFAULT_ROW_LIMIT = 100;
    private static final int MAX_ROW_LIMIT = 500;
    private static final int MAX_DIAGNOSTICS = 100;
    private static final int MAX_DUPLICATE_GROUPS = 50;
    private static final int MAX_DUPLICATE_ROWS_PER_GROUP = 20;

    private final TimingCsvParser parser;
    private final ImportValidator validator;
    private final ImportPreviewSnapshotLoader snapshotLoader;
    private final ImportPreviewPlanner planner;
    private final ImportOperationLifecycleService operationLifecycleService;
    private final ObjectMapper objectMapper;

    public ImportPreviewService(
            TimingCsvParser parser,
            ImportValidator validator,
            ImportPreviewSnapshotLoader snapshotLoader,
            ImportPreviewPlanner planner,
            ImportOperationLifecycleService operationLifecycleService,
            ObjectMapper objectMapper
    ) {
        this.parser = parser;
        this.validator = validator;
        this.snapshotLoader = snapshotLoader;
        this.planner = planner;
        this.operationLifecycleService = operationLifecycleService;
        this.objectMapper = objectMapper;
    }

    public ImportPreviewResponseDto preview(
            Long eventId,
            String originalFilename,
            byte[] contents,
            ImportOperationMode mode,
            List<Long> raceIds,
            Integer requestedRowLimit,
            String actor
    ) {
        if (mode == null) {
            throw new InvalidRequestException("IMPORT_MODE_REQUIRED", "Import mode is required");
        }
        if (actor == null || actor.isBlank()) {
            throw new InvalidRequestException("IMPORT_ACTOR_REQUIRED", "Authenticated import actor is required");
        }
        if (contents == null || contents.length == 0) {
            throw new InvalidRequestException("EMPTY_IMPORT_FILE", "Uploaded CSV is empty");
        }
        if (contents.length > MAX_FILE_BYTES) {
            throw new InvalidRequestException("IMPORT_FILE_TOO_LARGE", "Uploaded CSV exceeds the 25 MiB limit");
        }
        int rowLimit = rowLimit(requestedRowLimit);
        String filename = safeFilename(originalFilename);
        String fileSha256 = sha256(contents);

        TimingCsvParseResult parsed;
        try (InputStreamReader reader = new InputStreamReader(
                new ByteArrayInputStream(contents), StandardCharsets.UTF_8
        )) {
            parsed = parser.parse(reader);
        } catch (TimingCsvFormatException exception) {
            throw new InvalidRequestException("INVALID_CSV_HEADER", exception.getMessage());
        } catch (Exception exception) {
            throw new InvalidRequestException("IMPORT_FILE_READ_FAILED", "CSV could not be read");
        }

        ImportPreviewDatabaseSnapshot snapshot = snapshotLoader.load(eventId);
        List<Long> scopeRaceIds = scope(raceIds, snapshot);
        if (mode == ImportOperationMode.EMERGENCY_REPLACE) {
            boolean published = snapshot.races().stream()
                    .filter(race -> scopeRaceIds.contains(race.id()))
                    .anyMatch(race -> race.resultsPublicationStatus()
                            != ru.sportsresults.domain.ResultsPublicationStatus.DRAFT);
            if (published) {
                throw new RequestConflictException(
                        "RACE_RESULTS_MUST_BE_DRAFT",
                        "Every Race in an emergency replacement scope must be DRAFT"
                );
            }
        }
        ImportPreviewPlan plan = planner.plan(
                eventId,
                mode,
                scopeRaceIds,
                fileSha256,
                filename,
                parsed,
                validator.validate(parsed.rows()),
                snapshot
        );

        UUID operationId = UUID.randomUUID();
        ImportOperation operation = operationLifecycleService.createPreview(
                operationId,
                eventId,
                scopeRaceIds,
                mode,
                filename,
                fileSha256,
                snapshot.resultDataRevision(),
                plan.planDigest(),
                actor.strip(),
                summary(plan)
        );

        return new ImportPreviewResponseDto(
                operation.getId(),
                operation.getStatus(),
                mode,
                scopeRaceIds,
                fileSha256,
                snapshot.resultDataRevision(),
                plan.planDigest(),
                operation.getCreatedAt(),
                operation.getExpiresAt(),
                plan.totals(),
                plan.modeSummary(),
                plan.blockingErrorsPresent(),
                rowLimit,
                plan.rows().size() > rowLimit,
                plan.rows().stream().limit(rowLimit).toList(),
                plan.duplicateBibs().stream()
                        .limit(MAX_DUPLICATE_GROUPS)
                        .map(ImportPreviewService::boundedDuplicate)
                        .toList(),
                plan.duplicateBibs().size() > MAX_DUPLICATE_GROUPS,
                plan.diagnostics().stream().limit(MAX_DIAGNOSTICS).toList(),
                plan.diagnostics().size() > MAX_DIAGNOSTICS,
                plan.emergencySummary()
        );
    }

    private static ImportPreviewResponseDto.DuplicateBib boundedDuplicate(
            ImportPreviewResponseDto.DuplicateBib duplicate
    ) {
        return new ImportPreviewResponseDto.DuplicateBib(
                duplicate.bib(),
                duplicate.count(),
                duplicate.rows().size() > MAX_DUPLICATE_ROWS_PER_GROUP,
                duplicate.rows().stream().limit(MAX_DUPLICATE_ROWS_PER_GROUP).toList()
        );
    }

    private static List<Long> scope(
            List<Long> requestedRaceIds,
            ImportPreviewDatabaseSnapshot snapshot
    ) {
        if (requestedRaceIds == null || requestedRaceIds.isEmpty()
                || requestedRaceIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new InvalidRequestException("IMPORT_SCOPE_REQUIRED", "At least one race must be selected");
        }
        Set<Long> eventRaceIds = snapshot.races().stream()
                .map(ImportPreviewDatabaseSnapshot.RaceSnapshot::id)
                .collect(java.util.stream.Collectors.toSet());
        List<Long> canonical = requestedRaceIds.stream().distinct().sorted().toList();
        if (!eventRaceIds.containsAll(canonical)) {
            throw new InvalidRequestException("INVALID_IMPORT_SCOPE", "Every scope race must belong to the event");
        }
        return canonical;
    }

    private String summary(ImportPreviewPlan plan) {
        try {
            Map<String, Object> summary = new java.util.LinkedHashMap<>();
            summary.put("totals", plan.totals());
            summary.put("modeSummary", plan.modeSummary());
            summary.put("blockingErrorsPresent", plan.blockingErrorsPresent());
            if (plan.emergencySummary() != null) summary.put("emergencySummary", plan.emergencySummary());
            return objectMapper.writeValueAsString(summary);
        } catch (Exception exception) {
            throw new IllegalStateException("Preview summary could not be serialized", exception);
        }
    }

    private static int rowLimit(Integer requested) {
        int value = requested == null ? DEFAULT_ROW_LIMIT : requested;
        if (value < 0 || value > MAX_ROW_LIMIT) {
            throw new InvalidRequestException(
                    "INVALID_PREVIEW_ROW_LIMIT", "rowLimit must be between 0 and " + MAX_ROW_LIMIT
            );
        }
        return value;
    }

    private static String safeFilename(String originalFilename) {
        String value = originalFilename == null || originalFilename.isBlank() ? "upload.csv" : originalFilename;
        String normalized = value.replace('\\', '/').replace("\u0000", "");
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1).strip();
        if (filename.isEmpty()) filename = "upload.csv";
        if (filename.length() > 255) filename = filename.substring(filename.length() - 255);
        return filename;
    }

    private static String sha256(byte[] contents) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
