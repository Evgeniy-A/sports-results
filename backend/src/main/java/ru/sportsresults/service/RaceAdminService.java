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

@Service
public class RaceAdminService {
    private final RaceRepository raceRepository;
    private final AwardPolicyRepository awardPolicyRepository;
    private final AdminChangeLogRepository auditRepository;
    private final EventResultDataMutationGuard mutationGuard;
    private final SlugGenerator slugGenerator;

    public RaceAdminService(RaceRepository raceRepository,
                            AwardPolicyRepository awardPolicyRepository, AdminChangeLogRepository auditRepository,
                            EventResultDataMutationGuard mutationGuard,
                            SlugGenerator slugGenerator) {
        this.raceRepository = raceRepository;
        this.awardPolicyRepository = awardPolicyRepository;
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
        applyFields(race, request, slug);
        try {
            raceRepository.saveAndFlush(race);
            AwardPolicy policy = new AwardPolicy();
            policy.setRace(race);
            policy.setRankingBasis(RankingBasis.CHIP_TIME);
            policy.setPrimaryStandingMode(PrimaryStandingMode.ALL);
            policy.setAbsolutePrizePlaces(0);
            policy.setCategoryEnabled(false);
            policy.setAgeCalculationMode(AgeCalculationMode.EVENT_DATE);
            policy.setCategoryPrizePlaces(0);
            policy.setExcludeAbsoluteWinnersFromCategory(false);
            awardPolicyRepository.save(policy);
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
        applyFields(race, request, slug);
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

    private static void applyFields(Race race, UpsertRaceRequest request, String slug) {
        race.setSourceCode(request.sourceCode().strip());
        race.setName(request.name().strip());
        race.setSlug(slug);
        race.setDistanceMeters(request.distanceMeters());
        race.setStartsAt(request.startsAt());
        race.setDisplayOrder(request.displayOrder());
        if (request.publicVisible() != null) {
            race.setPublicVisible(request.publicVisible());
        }
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
