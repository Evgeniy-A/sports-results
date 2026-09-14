package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.ImportOperation;
import ru.sportsresults.domain.ImportOperationMode;
import ru.sportsresults.domain.ImportOperationStatus;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.ImportOperationRepository;
import ru.sportsresults.repository.RaceRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ImportOperationLifecycleService {

    private static final Duration PREVIEW_LIFETIME = Duration.ofHours(24);

    private final EventRepository eventRepository;
    private final RaceRepository raceRepository;
    private final ImportOperationRepository operationRepository;

    public ImportOperationLifecycleService(
            EventRepository eventRepository,
            RaceRepository raceRepository,
            ImportOperationRepository operationRepository
    ) {
        this.eventRepository = eventRepository;
        this.raceRepository = raceRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportOperation createPreview(
            UUID operationId,
            Long eventId,
            List<Long> scopeRaceIds,
            ImportOperationMode mode,
            String sourceFilename,
            String fileSha256,
            long baseRevision,
            String planDigest,
            String createdBy,
            String previewSummary
    ) {
        var event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_NOT_FOUND", "Event not found"));
        var races = raceRepository.findAllById(scopeRaceIds);
        if (races.size() != scopeRaceIds.size()
                || races.stream().anyMatch(race -> !race.getEvent().getId().equals(eventId))) {
            throw new InvalidRequestException("INVALID_IMPORT_SCOPE", "Every scope race must belong to the event");
        }

        Instant now = Instant.now();
        ImportOperation operation = new ImportOperation();
        operation.setId(operationId);
        operation.setEvent(event);
        operation.setRaces(new LinkedHashSet<>(races));
        operation.setMode(mode);
        operation.setStatus(ImportOperationStatus.PREVIEWED);
        operation.setSourceFilename(sourceFilename);
        operation.setFileSha256(fileSha256);
        operation.setBaseRevision(baseRevision);
        operation.setPlanDigest(planDigest);
        operation.setCreatedBy(createdBy);
        operation.setPreviewSummary(previewSummary);
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        operation.setExpiresAt(now.plus(PREVIEW_LIFETIME));
        return operationRepository.saveAndFlush(operation);
    }
}
