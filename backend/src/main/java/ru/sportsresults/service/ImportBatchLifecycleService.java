package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.ImportErrorDto;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.ImportBatch;
import ru.sportsresults.domain.ImportBatchStatus;
import ru.sportsresults.domain.ImportScopeType;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.ImportBatchRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Service
public class ImportBatchLifecycleService {

    private final EventRepository eventRepository;
    private final ImportBatchRepository importBatchRepository;
    private final ObjectMapper objectMapper;

    public ImportBatchLifecycleService(
            EventRepository eventRepository,
            ImportBatchRepository importBatchRepository,
            ObjectMapper objectMapper
    ) {
        this.eventRepository = eventRepository;
        this.importBatchRepository = importBatchRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportBatch createEventBatch(Long eventId, String filename, String sha256) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_NOT_FOUND", "Event not found"));
        ImportBatch batch = new ImportBatch();
        batch.setEvent(event);
        batch.setScopeType(ImportScopeType.EVENT);
        batch.setSourceFilename(filename);
        batch.setFileSha256(sha256);
        batch.setStatus(ImportBatchStatus.VALIDATING);
        batch.setStartedAt(Instant.now());
        return importBatchRepository.saveAndFlush(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportBatch markFailed(
            Long batchId,
            int totalRows,
            int skippedRows,
            int failedRows,
            List<ImportErrorDto> errors
    ) {
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("Import batch disappeared: " + batchId));
        batch.setStatus(ImportBatchStatus.FAILED);
        batch.setTotalRows(totalRows);
        batch.setImportedRows(0);
        batch.setSkippedRows(skippedRows);
        batch.setFailedRows(failedRows);
        batch.setErrorSummary(writeErrors(errors));
        batch.setFinishedAt(Instant.now());
        return importBatchRepository.saveAndFlush(batch);
    }

    private String writeErrors(List<ImportErrorDto> errors) {
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (Exception exception) {
            return "[{\"field\":\"file\",\"message\":\"Import failed\"}]";
        }
    }
}
