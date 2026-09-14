package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.RacePublicSettingsDto;
import ru.sportsresults.api.dto.UpdateRacePublicSettingsRequest;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.AuditEntityType;
import ru.sportsresults.domain.PrimaryStandingMode;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.RankingBasis;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.AwardPolicyRepository;
import ru.sportsresults.domain.AwardPolicy;
import ru.sportsresults.domain.Event;

@Service
public class RacePublicSettingsService {

    private final RaceRepository raceRepository;
    private final AdminChangeLogRepository auditRepository;
    private final AwardPolicyRepository awardPolicyRepository;
    private final EventResultDataMutationGuard mutationGuard;
    private final ResultConfigurationChangeGuard configurationGuard;

    public RacePublicSettingsService(
            RaceRepository raceRepository,
            AdminChangeLogRepository auditRepository,
            AwardPolicyRepository awardPolicyRepository,
            EventResultDataMutationGuard mutationGuard,
            ResultConfigurationChangeGuard configurationGuard
    ) {
        this.raceRepository = raceRepository;
        this.auditRepository = auditRepository;
        this.awardPolicyRepository = awardPolicyRepository;
        this.mutationGuard = mutationGuard;
        this.configurationGuard = configurationGuard;
    }

    @Transactional(readOnly = true)
    public RacePublicSettingsDto get(Long raceId) {
        Race race = requireRace(raceId);
        RankingBasis basis = awardPolicyRepository.findByRaceId(raceId)
                .map(AwardPolicy::getRankingBasis)
                .orElse(race.getPublicRankingBasis());
        return new RacePublicSettingsDto(raceId, basis);
    }

    @Transactional
    public RacePublicSettingsDto update(
            Long raceId,
            UpdateRacePublicSettingsRequest request,
            String actor
    ) {
        Long eventId = raceRepository.findEventIdByRaceId(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("RACE_NOT_FOUND", "Race not found"));
        Event event = mutationGuard.lock(eventId);
        Race race = requireRace(raceId);
        AwardPolicy policy = awardPolicyRepository.findByRaceId(raceId).orElse(null);
        if (request.publicRankingBasis() == RankingBasis.NONE && policy != null
                && (policy.getPrimaryStandingMode() != PrimaryStandingMode.NONE
                || policy.getAbsolutePrizePlaces() != 0
                || policy.isCategoryEnabled()
                || policy.getCategoryPrizePlaces() != 0
                || policy.isExcludeAbsoluteWinnersFromCategory())) {
            throw new InvalidRequestException(
                    "INVALID_NONE_AWARD_POLICY",
                    "Configure AwardPolicy without standings or prizes before selecting NONE"
            );
        }
        RankingBasis previous = race.getPublicRankingBasis();
        boolean changed = previous != request.publicRankingBasis();
        if (policy != null && policy.getRankingBasis() != request.publicRankingBasis()) {
            changed = true;
        }
        if (changed) {
            configurationGuard.requireDraft(java.util.List.of(race));
        }
        if (previous != request.publicRankingBasis()) {
            race.setPublicRankingBasis(request.publicRankingBasis());
            raceRepository.saveAndFlush(race);
            auditRepository.save(changeLog(raceId, previous, request.publicRankingBasis(), actor));
        }
        if (policy != null) {
            if (policy.getRankingBasis() != request.publicRankingBasis()) {
                policy.setRankingBasis(request.publicRankingBasis());
                awardPolicyRepository.save(policy);
            }
        }
        if (changed) {
            configurationGuard.markRecalculationRequiredForCurrentData(java.util.List.of(race));
            mutationGuard.bump(event);
        }
        return toDto(race);
    }

    private Race requireRace(Long raceId) {
        return raceRepository.findById(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("RACE_NOT_FOUND", "Race not found"));
    }

    private static RacePublicSettingsDto toDto(Race race) {
        return new RacePublicSettingsDto(race.getId(), race.getPublicRankingBasis());
    }

    private static AdminChangeLog changeLog(
            Long raceId,
            RankingBasis previous,
            RankingBasis current,
            String actor
    ) {
        AdminChangeLog log = new AdminChangeLog();
        log.setActor(actor);
        log.setEntityType(AuditEntityType.RACE);
        log.setEntityId(raceId);
        log.setFieldName("publicRankingBasis");
        log.setOldValue(previous.name());
        log.setNewValue(current.name());
        return log;
    }
}
