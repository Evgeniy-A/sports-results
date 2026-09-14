package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.AwardPolicyDto;
import ru.sportsresults.api.dto.UpdateAwardPolicyRequest;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.AuditEntityType;
import ru.sportsresults.domain.AwardPolicy;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.PrimaryStandingMode;
import ru.sportsresults.domain.RankingBasis;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.AwardPolicyRepository;
import ru.sportsresults.repository.RaceRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AwardPolicyService {
    private final AwardPolicyRepository repository;
    private final RaceRepository raceRepository;
    private final AdminChangeLogRepository auditRepository;
    private final ResultConfigurationChangeGuard configurationGuard;
    private final EventResultDataMutationGuard mutationGuard;

    public AwardPolicyService(
            AwardPolicyRepository repository,
            RaceRepository raceRepository,
            AdminChangeLogRepository auditRepository,
            ResultConfigurationChangeGuard configurationGuard,
            EventResultDataMutationGuard mutationGuard
    ) {
        this.repository = repository;
        this.raceRepository = raceRepository;
        this.auditRepository = auditRepository;
        this.configurationGuard = configurationGuard;
        this.mutationGuard = mutationGuard;
    }

    @Transactional(readOnly = true)
    public AwardPolicyDto get(Long raceId) {
        return repository.findByRaceId(raceId).map(AwardPolicyService::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("AWARD_POLICY_NOT_FOUND", "Award policy not configured"));
    }

    @Transactional
    public AwardPolicyDto upsert(Long raceId, UpdateAwardPolicyRequest request, String actor) {
        validate(request);
        Long eventId = raceRepository.findEventIdByRaceId(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("RACE_NOT_FOUND", "Race not found"));
        Event event = mutationGuard.lock(eventId);
        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("RACE_NOT_FOUND", "Race not found"));
        AwardPolicy policy = repository.findByRaceId(raceId).orElseGet(() -> {
            AwardPolicy created = new AwardPolicy();
            created.setRace(race);
            return created;
        });
        boolean existing = policy.getId() != null;
        List<AdminChangeLog> changes = new ArrayList<>();
        if (existing) {
            change(changes, actor, policy.getId(), "rankingBasis", policy.getRankingBasis(), request.rankingBasis());
            change(changes, actor, policy.getId(), "primaryStandingMode", policy.getPrimaryStandingMode(), request.primaryStandingMode());
            change(changes, actor, policy.getId(), "absolutePrizePlaces", policy.getAbsolutePrizePlaces(), request.absolutePrizePlaces());
            change(changes, actor, policy.getId(), "categoryEnabled", policy.isCategoryEnabled(), request.categoryEnabled());
            change(changes, actor, policy.getId(), "ageCalculationMode",
                    policy.getAgeCalculationMode(), request.ageCalculationMode());
            change(changes, actor, policy.getId(), "categoryPrizePlaces", policy.getCategoryPrizePlaces(), request.categoryPrizePlaces());
            change(changes, actor, policy.getId(), "excludeAbsoluteWinnersFromCategory",
                    policy.isExcludeAbsoluteWinnersFromCategory(), request.excludeAbsoluteWinnersFromCategory());
        }
        boolean changed = !existing || !changes.isEmpty();
        if (changed) {
            configurationGuard.requireDraft(List.of(race));
        }
        apply(policy, request);
        // AwardPolicy is authoritative. Keep the legacy Race field as a compatibility mirror.
        race.setPublicRankingBasis(request.rankingBasis());
        raceRepository.save(race);
        AwardPolicy saved = repository.saveAndFlush(policy);
        if (!existing) {
            changes.add(log(actor, saved.getId(), "created", null, toDto(saved).toString()));
        }
        auditRepository.saveAll(changes);
        if (changed) {
            configurationGuard.markRecalculationRequiredForCurrentData(List.of(race));
            mutationGuard.bump(event);
        }
        return toDto(saved);
    }

    private static void apply(AwardPolicy policy, UpdateAwardPolicyRequest request) {
        policy.setRankingBasis(request.rankingBasis());
        policy.setPrimaryStandingMode(request.primaryStandingMode());
        policy.setAbsolutePrizePlaces(request.absolutePrizePlaces());
        policy.setCategoryEnabled(request.categoryEnabled());
        policy.setAgeCalculationMode(request.ageCalculationMode());
        policy.setCategoryPrizePlaces(request.categoryPrizePlaces());
        policy.setExcludeAbsoluteWinnersFromCategory(request.excludeAbsoluteWinnersFromCategory());
    }

    static void validate(UpdateAwardPolicyRequest request) {
        if (request.rankingBasis() != RankingBasis.NONE) {
            return;
        }
        if (request.primaryStandingMode() != PrimaryStandingMode.NONE
                || request.absolutePrizePlaces() != 0
                || request.categoryEnabled()
                || request.categoryPrizePlaces() != 0
                || request.excludeAbsoluteWinnersFromCategory()) {
            throw new InvalidRequestException(
                    "INVALID_NONE_AWARD_POLICY",
                    "NONE requires no primary/category standing and zero prize places"
            );
        }
    }

    private static void change(List<AdminChangeLog> logs, String actor, Long id, String field, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) logs.add(log(actor, id, field, oldValue, newValue));
    }

    private static AdminChangeLog log(String actor, Long id, String field, Object oldValue, Object newValue) {
        AdminChangeLog log = new AdminChangeLog();
        log.setActor(actor);
        log.setEntityType(AuditEntityType.AWARD_POLICY);
        log.setEntityId(id);
        log.setFieldName(field);
        log.setOldValue(oldValue == null ? null : oldValue.toString());
        log.setNewValue(newValue == null ? null : newValue.toString());
        return log;
    }

    public static AwardPolicyDto toDto(AwardPolicy policy) {
        return new AwardPolicyDto(
                policy.getId(), policy.getRace().getId(), policy.getRankingBasis(), policy.getPrimaryStandingMode(),
                policy.getAbsolutePrizePlaces(), policy.isCategoryEnabled(), policy.getAgeCalculationMode(),
                policy.getCategoryPrizePlaces(),
                policy.isExcludeAbsoluteWinnersFromCategory()
        );
    }
}
