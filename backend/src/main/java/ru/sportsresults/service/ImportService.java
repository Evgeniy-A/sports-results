package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sportsresults.api.dto.ImportErrorDto;
import ru.sportsresults.api.dto.ImportReportDto;
import ru.sportsresults.domain.ImportBatch;
import ru.sportsresults.domain.ImportBatchStatus;
import ru.sportsresults.domain.ImportScopeType;
import ru.sportsresults.importing.TimingCsvFormatException;
import ru.sportsresults.importing.TimingCsvParseResult;
import ru.sportsresults.importing.TimingCsvParser;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.ImportBatchRepository;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class ImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportService.class);
    private static final int MAX_ERRORS_IN_REPORT = 100;
    private static final int MAX_FILE_BYTES = 25 * 1024 * 1024;
    private static final String REPLACEMENT_WARNING =
            "Full EVENT import replaces current results and may overwrite manual result corrections.";

    private final EventRepository eventRepository;
    private final ImportBatchRepository importBatchRepository;
    private final TimingCsvParser parser;
    private final ImportValidator validator;
    private final ImportBatchLifecycleService lifecycleService;
    private final EventSnapshotWriter snapshotWriter;

    public ImportService(
            EventRepository eventRepository,
            ImportBatchRepository importBatchRepository,
            TimingCsvParser parser,
            ImportValidator validator,
            ImportBatchLifecycleService lifecycleService,
            EventSnapshotWriter snapshotWriter
    ) {
        this.eventRepository = eventRepository;
        this.importBatchRepository = importBatchRepository;
        this.parser = parser;
        this.validator = validator;
        this.lifecycleService = lifecycleService;
        this.snapshotWriter = snapshotWriter;
    }

    public ImportReportDto importEvent(Long eventId, String originalFilename, byte[] contents) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("EVENT_NOT_FOUND", "Event not found");
        }
        String filename = safeFilename(originalFilename);
        String fileSha256 = sha256(contents);

        var previous = importBatchRepository.findFirstByEventIdAndScopeTypeAndFileSha256AndStatus(
                eventId,
                ImportScopeType.EVENT,
                fileSha256,
                ImportBatchStatus.SUCCEEDED
        );
        if (previous.isPresent()) {
            return successfulReport(
                    previous.get(),
                    true,
                    "File was already successfully imported; no data was changed. " + REPLACEMENT_WARNING
            );
        }

        ImportBatch batch = lifecycleService.createEventBatch(eventId, filename, fileSha256);
        if (contents.length == 0 || contents.length > MAX_FILE_BYTES) {
            String message = contents.length == 0
                    ? "Uploaded CSV is empty"
                    : "Uploaded CSV exceeds the 25 MiB limit";
            return failedReport(batch.getId(), 0, 0, 1, List.of(new ImportErrorDto(null, "file", message)));
        }

        TimingCsvParseResult parsed;
        try (InputStreamReader reader = new InputStreamReader(
                new ByteArrayInputStream(contents),
                StandardCharsets.UTF_8
        )) {
            parsed = parser.parse(reader);
        } catch (TimingCsvFormatException exception) {
            return failedReport(
                    batch.getId(),
                    0,
                    0,
                    1,
                    List.of(new ImportErrorDto(null, "header", exception.getMessage()))
            );
        } catch (Exception exception) {
            return failedReport(
                    batch.getId(),
                    0,
                    0,
                    1,
                    List.of(new ImportErrorDto(null, "file", "CSV could not be read"))
            );
        }

        List<ImportErrorDto> errors = new ArrayList<>();
        parsed.errors().forEach(error -> errors.add(new ImportErrorDto(
                error.sourceRowNumber(),
                error.column(),
                error.message()
        )));
        errors.addAll(validator.validate(parsed.rows()));
        if (!errors.isEmpty()) {
            int failedRows = (int) errors.stream()
                    .map(ImportErrorDto::row)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
            if (failedRows == 0) {
                failedRows = 1;
            }
            int skippedRows = Math.max(0, parsed.totalRows() - failedRows);
            return failedReport(batch.getId(), parsed.totalRows(), skippedRows, failedRows, errors);
        }

        try {
            ImportBatch completed = snapshotWriter.replaceEventSnapshot(eventId, batch.getId(), parsed.rows());
            return successfulReport(completed, false, REPLACEMENT_WARNING);
        } catch (RuntimeException exception) {
            LOGGER.error("Event snapshot import failed for event {} and batch {}", eventId, batch.getId(), exception);
            var concurrentSuccess = importBatchRepository.findFirstByEventIdAndScopeTypeAndFileSha256AndStatus(
                    eventId,
                    ImportScopeType.EVENT,
                    fileSha256,
                    ImportBatchStatus.SUCCEEDED
            );
            if (concurrentSuccess.isPresent()) {
                lifecycleService.markFailed(
                        batch.getId(),
                        parsed.totalRows(),
                        parsed.totalRows(),
                        0,
                        List.of(new ImportErrorDto(null, "file", "A concurrent identical import completed first"))
                );
                return successfulReport(concurrentSuccess.get(), true, "Identical file was imported concurrently.");
            }
            return failedReport(
                    batch.getId(),
                    parsed.totalRows(),
                    parsed.totalRows(),
                    0,
                    List.of(new ImportErrorDto(null, "database", "Snapshot could not be saved; previous results remain active"))
            );
        }
    }

    private ImportReportDto failedReport(
            Long batchId,
            int totalRows,
            int skippedRows,
            int failedRows,
            List<ImportErrorDto> errors
    ) {
        List<ImportErrorDto> limited = errors.stream().limit(MAX_ERRORS_IN_REPORT).toList();
        ImportBatch failed = lifecycleService.markFailed(
                batchId,
                totalRows,
                skippedRows,
                failedRows,
                limited
        );
        return new ImportReportDto(
                failed.getId(),
                failed.getStatus(),
                failed.getTotalRows(),
                failed.getImportedRows(),
                failed.getSkippedRows(),
                failed.getFailedRows(),
                false,
                "Import failed; previous published results were not changed. " + REPLACEMENT_WARNING,
                limited
        );
    }

    private static ImportReportDto successfulReport(ImportBatch batch, boolean duplicate, String warning) {
        return new ImportReportDto(
                batch.getId(),
                batch.getStatus(),
                batch.getTotalRows(),
                batch.getImportedRows(),
                batch.getSkippedRows(),
                batch.getFailedRows(),
                duplicate,
                warning,
                List.of()
        );
    }

    private static String safeFilename(String originalFilename) {
        String value = originalFilename == null || originalFilename.isBlank() ? "upload.csv" : originalFilename;
        String normalized = value.replace('\\', '/').replace("\u0000", "");
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1).strip();
        if (filename.isEmpty()) {
            filename = "upload.csv";
        }
        if (filename.length() > 255) {
            filename = filename.substring(filename.length() - 255);
        }
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
