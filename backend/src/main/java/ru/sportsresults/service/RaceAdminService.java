package ru.sportsresults.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.RaceDto;
import ru.sportsresults.api.dto.UpsertRaceRequest;
import ru.sportsresults.domain.*;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.AwardPolicyRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.RegistrationRepository;
import ru.sportsresults.repository.ResultRepository;

@Service
public class RaceAdminService {
    private final RaceRepository raceRepository;
    private final AwardPolicyRepository awardPolicyRepository;
    private final RegistrationRepository registrationRepository;
    private final ResultRepository resultRepository;
    private final AdminChangeLogRepository auditRepository;
    private final EventResultDataMutationGuard mutationGuard;
    private final SlugGenerator slugGenerator;

    public RaceAdminService(RaceRepository raceRepository,
                            AwardPolicyRepository awardPolicyRepository,
                            RegistrationRepository registrationRepository,
                            ResultRepository resultRepository,
                            AdminChangeLogRepository auditRepository,
                            EventResultDataMutationGuard mutationGuard,
                            SlugGenerator slugGenerator) {
        this.raceRepository = raceRepository;
        this.awardPolicyRepository = awardPolicyRepository;
        this.registrationRepository = registrationRepository;
        this.resultRepository = resultRepository;
        this.auditRepository = auditRepository;
        this.mutationGuard = mutationGuard;
        this.slugGenerator = slugGenerator;
    }

    @Transactional
    public RaceDto create(Long eventId, UpsertRaceRequest request, String actor) {
        Race race = new Race();
        Event event = mutationGuard.lock(eventId);
        race.setEvent(event);
        String slug = request.slug() == null || request.slug().isBlank()
                ? slugGenerator.uniqueSlug(request.name(), "start",
                        candidate -> raceRepository.existsByEventIdAndSlug(eventId, candidate))
                : request.slug().strip();
        String sourceCode = normalizeSourceCode(request.sourceCode());
        if (sourceCode == null) sourceCode = uniqueInternalSourceCode(eventId, slug);
        applyFields(race, request, slug, sourceCode);
        try {
            raceRepository.saveAndFlush(race);
            awardPolicyRepository.save(AwardPolicyService.createDefault(race));
            audit(actor, race.getId(), "created", null, race.getName());
            mutationGuard.bump(event);
            return toDto(race, false);
        } catch (DataIntegrityViolationException exception) {
            throw new RequestConflictException("RACE_EXISTS", "Race source code or slug already exists in this event");
        }
    }

    @Transactional
    public RaceDto update(Long eventId, Long raceId, UpsertRaceRequest request, String actor) {
        Event event = mutationGuard.lock(eventId);
        Race race = raceRepository.findByIdAndEventId(raceId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("RACE_NOT_FOUND", "Race not found in this event"));
        String old = race.getName();
        String oldSourceCode = race.getSourceCode();
        boolean oldPublicVisible = race.isPublicVisible();
        String slug = request.slug() == null || request.slug().isBlank()
                ? race.getSlug()
                : request.slug().strip();
        String sourceCode = normalizeSourceCode(request.sourceCode());
        applyFields(race, request, slug, sourceCode == null ? race.getSourceCode() : sourceCode);
        try {
            Race saved = raceRepository.saveAndFlush(race);
            boolean categories = awardPolicyRepository.findByRaceId(raceId).map(AwardPolicy::isCategoryEnabled).orElse(false);
            audit(actor, raceId, "updated", old, saved.getName());
            if (oldPublicVisible != saved.isPublicVisible()) {
                audit(actor, raceId, "publicVisible", oldPublicVisible, saved.isPublicVisible());
            }
            if (!java.util.Objects.equals(oldSourceCode, saved.getSourceCode())) {
                mutationGuard.bump(event);
            }
            return toDto(saved, categories);
        } catch (DataIntegrityViolationException exception) {
            throw new RequestConflictException("RACE_EXISTS", "Race source code or slug already exists in this event");
        }
    }

    @Transactional
    public void delete(Long eventId, Long raceId, String actor) {
        Event event = mutationGuard.lock(eventId);
        Race race = raceRepository.findByIdAndEventIdForUpdate(raceId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("RACE_NOT_FOUND", "Race not found in this event"));
        if (race.getResultsPublicationStatus() == ResultsPublicationStatus.PUBLISHED) {
            throw new RequestConflictException(
                    "RACE_DELETE_PUBLISHED", "Published Race results cannot be deleted"
            );
        }
        if (resultRepository.existsByRegistrationRaceId(raceId)) {
            throw new RequestConflictException(
                    "RACE_DELETE_HAS_RESULTS", "Race with results cannot be deleted"
            );
        }
        if (registrationRepository.existsByRaceId(raceId)) {
            throw new RequestConflictException(
                    "RACE_DELETE_HAS_REGISTRATIONS", "Race with registrations cannot be deleted"
            );
        }
        if (raceRepository.hasDeleteBlockingDependencies(raceId)) {
            throw new RequestConflictException(
                    "RACE_DELETE_HAS_DEPENDENCIES", "Race has import, publication, or configuration history"
            );
        }
        String deletedName = race.getName();
        audit(actor, raceId, "deleted", deletedName, null);
        try {
            raceRepository.delete(race);
            raceRepository.flush();
            normalizeDisplayOrder(eventId);
            mutationGuard.bump(event);
        } catch (DataIntegrityViolationException exception) {
            throw new RequestConflictException(
                    "RACE_DELETE_HAS_DEPENDENCIES", "Race has dependent data and cannot be deleted"
            );
        }
    }

    private void normalizeDisplayOrder(Long eventId) {
        java.util.List<Race> races = raceRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId);
        for (int index = 0; index < races.size(); index++) {
            races.get(index).setDisplayOrder(index);
        }
        raceRepository.saveAll(races);
    }

    private static void applyFields(Race race, UpsertRaceRequest request, String slug, String sourceCode) {
        race.setSourceCode(sourceCode);
        race.setName(request.name().strip());
        race.setSlug(slug);
        race.setDistanceMeters(request.distanceMeters());
        race.setStartsAt(request.startsAt());
        race.setDisplayOrder(request.displayOrder());
        if (request.publicVisible() != null) {
            race.setPublicVisible(request.publicVisible());
        }
    }

    private String uniqueInternalSourceCode(Long eventId, String slug) {
        String base = "internal-" + slug;
        String candidate = base;
        int suffix = 2;
        while (raceRepository.existsByEventIdAndSourceCode(eventId, candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static String normalizeSourceCode(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static RaceDto toDto(Race race, boolean categoryEnabled) {
        return new RaceDto(race.getId(), race.getEvent().getId(), race.getSourceCode(), race.getName(), race.getSlug(),
                race.getDistanceMeters(), race.getStartsAt(), race.getDisplayOrder(),
                race.getPublicRankingBasis(), categoryEnabled, race.isPublicVisible(),
                race.getResultsPublicationStatus(),
                race.getResultsPublicationStatus() == ResultsPublicationStatus.PUBLISHED,
                race.isResultRecalculationRequired());
    }

    private void audit(String actor, Long id, String field, Object oldValue, Object newValue) {
        AdminChangeLog log = new AdminChangeLog();
        log.setActor(actor);
        log.setEntityType(AuditEntityType.RACE);
        log.setEntityId(id);
        log.setFieldName(field);
        log.setOldValue(oldValue == null ? null : oldValue.toString());
        log.setNewValue(newValue == null ? null : newValue.toString());
        auditRepository.save(log);
    }
}
