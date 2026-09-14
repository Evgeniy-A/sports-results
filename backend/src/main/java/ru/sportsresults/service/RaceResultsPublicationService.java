package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.RaceResultsPublicationDto;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.AuditEntityType;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.RaceResultPublicationHistory;
import ru.sportsresults.domain.ResultsPublicationStatus;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.RaceResultPublicationHistoryRepository;

import java.time.Instant;

@Service
public class RaceResultsPublicationService {

    private final RaceRepository raceRepository;
    private final EventResultDataMutationGuard mutationGuard;
    private final RaceResultPublicationHistoryRepository historyRepository;
    private final AdminChangeLogRepository auditRepository;

    public RaceResultsPublicationService(
            RaceRepository raceRepository,
            EventResultDataMutationGuard mutationGuard,
            RaceResultPublicationHistoryRepository historyRepository,
            AdminChangeLogRepository auditRepository
    ) {
        this.raceRepository = raceRepository;
        this.mutationGuard = mutationGuard;
        this.historyRepository = historyRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public RaceResultsPublicationDto draft(Long eventId, Long raceId, String reason, String actor) {
        return transition(eventId, raceId, ResultsPublicationStatus.DRAFT, normalize(reason), actor);
    }

    @Transactional
    public RaceResultsPublicationDto publish(Long eventId, Long raceId, String actor) {
        return transition(eventId, raceId, ResultsPublicationStatus.PUBLISHED, null, actor);
    }

    private RaceResultsPublicationDto transition(
            Long eventId,
            Long raceId,
            ResultsPublicationStatus target,
            String reason,
            String actor
    ) {
        // Shared write order with imports: Event, then Race.
        mutationGuard.lock(eventId);
        Race race = raceRepository.findByIdAndEventIdForUpdate(raceId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RACE_NOT_FOUND", "Race not found in this event"
                ));
        ResultsPublicationStatus previous = race.getResultsPublicationStatus();
        if (previous == target) {
            return response(race, previous, target, null);
        }
        if (target == ResultsPublicationStatus.PUBLISHED && race.isResultRecalculationRequired()) {
            throw new RequestConflictException(
                    "RESULT_RECALCULATION_REQUIRED",
                    "Preview and apply result recalculation before publishing this Race"
            );
        }

        Instant changedAt = Instant.now();
        race.setResultsPublicationStatus(target);
        raceRepository.saveAndFlush(race);

        RaceResultPublicationHistory history = new RaceResultPublicationHistory();
        history.setRace(race);
        history.setFromStatus(previous);
        history.setToStatus(target);
        history.setActor(actor);
        history.setReason(reason);
        history.setCreatedAt(changedAt);
        historyRepository.save(history);

        AdminChangeLog audit = new AdminChangeLog();
        audit.setActor(actor);
        audit.setChangedAt(changedAt);
        audit.setEntityType(AuditEntityType.RACE);
        audit.setEntityId(raceId);
        audit.setFieldName("resultsPublicationStatus");
        audit.setOldValue(previous.name());
        audit.setNewValue(target.name());
        auditRepository.save(audit);

        return response(race, previous, target, changedAt);
    }

    private static RaceResultsPublicationDto response(
            Race race,
            ResultsPublicationStatus previous,
            ResultsPublicationStatus current,
            Instant changedAt
    ) {
        return new RaceResultsPublicationDto(
                race.getEvent().getId(), race.getId(), race.getName(), previous, current, changedAt
        );
    }

    private static String normalize(String reason) {
        return reason == null || reason.isBlank() ? null : reason.strip();
    }
}
