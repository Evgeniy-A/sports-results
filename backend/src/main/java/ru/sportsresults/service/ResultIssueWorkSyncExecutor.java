package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.ResultIssueWorkItem;
import ru.sportsresults.domain.ResultIssueWorkSyncStatus;
import ru.sportsresults.repository.ResultIssueWorkItemRepository;
import ru.sportsresults.repository.RegistrationRepository;
import ru.sportsresults.repository.ResultRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
public class ResultIssueWorkSyncExecutor {

    private final ResultIssueWorkItemRepository workItemRepository;
    private final ResultRepository resultRepository;
    private final RegistrationRepository registrationRepository;
    private final EventResultDataMutationGuard mutationGuard;
    private final ResultFactualMutationService factualMutationService;
    private final RegistrationFactualMutationService registrationMutationService;
    private final ResultIssueWorkItemAuditService auditService;

    public ResultIssueWorkSyncExecutor(
            ResultIssueWorkItemRepository workItemRepository,
            ResultRepository resultRepository,
            RegistrationRepository registrationRepository,
            EventResultDataMutationGuard mutationGuard,
            ResultFactualMutationService factualMutationService,
            RegistrationFactualMutationService registrationMutationService,
            ResultIssueWorkItemAuditService auditService
    ) {
        this.workItemRepository = workItemRepository;
        this.resultRepository = resultRepository;
        this.registrationRepository = registrationRepository;
        this.mutationGuard = mutationGuard;
        this.factualMutationService = factualMutationService;
        this.registrationMutationService = registrationMutationService;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncOutcome sync(Long workItemId, String actor) {
        Long eventId = workItemRepository.findEventIdById(workItemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "JOURNAL_WORK_ITEM_NOT_FOUND", "Result issue work item not found"));
        var event = mutationGuard.lock(eventId);
        ResultIssueWorkItem item = workItemRepository.findByIdForUpdate(workItemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "JOURNAL_WORK_ITEM_NOT_FOUND", "Result issue work item not found"));
        if (item.getSyncStatus() != ResultIssueWorkSyncStatus.PENDING) {
            return SyncOutcome.SKIPPED;
        }

        Registration registration = registrationRepository.findByIdForUpdate(
                        item.getIssueRequest().getRegistration().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REGISTRATION_NOT_FOUND", "Registration not found"));
        if (!registration.isCurrent()) {
            throw new RequestConflictException(
                    "JOURNAL_REGISTRATION_RETIRED", "Current registration is retired");
        }
        ensureRegistrationSnapshot(item, registration);
        Result result = resultRepository.findByRegistrationId(registration.getId()).orElse(null);
        boolean resultChanges = hasResultChanges(item);
        if (resultChanges && (result == null || item.getResult() == null
                || !Objects.equals(item.getResult().getId(), result.getId()))) {
            return conflict(item, actor, "JOURNAL_RESULT_RELATION_CHANGED");
        }
        if (resultChanges && !sameRelevantResultState(item, result)) {
            return conflict(item, actor, "JOURNAL_OFFICIAL_RESULT_CHANGED");
        }
        if (!sameRelevantRegistrationState(item, registration)) {
            return conflict(item, actor, "JOURNAL_OFFICIAL_REGISTRATION_CHANGED");
        }

        var desiredRegistration = registrationMutationService.validate(
                registration,
                registrationFieldChanged(item.getBaseDisplayName(), item.getDesiredDisplayName())
                        ? item.getDesiredDisplayName() : registration.getDisplayName(),
                registrationFieldChanged(item.getBaseGender(), item.getDesiredGender())
                        ? item.getDesiredGender() : registration.getGender(),
                registrationFieldChanged(item.getBaseBirthDate(), item.getDesiredBirthDate())
                        ? item.getDesiredBirthDate() : registration.getBirthDate(),
                registrationFieldChanged(item.getBaseBib(), item.getDesiredBib())
                        ? item.getDesiredBib() : registration.getBib(),
                registrationFieldChanged(id(item.getBaseRace()), id(item.getDesiredRace()))
                        ? item.getDesiredRace().getId() : registration.getRace().getId()
        );

        boolean registrationChanged = registrationMutationService.apply(registration, desiredRegistration, actor);
        boolean resultChanged = resultChanges && factualMutationService.apply(
                result, item.getDesiredResultStatus(), milliseconds(item.getDesiredGunTime()),
                milliseconds(item.getDesiredChipTime()), actor);
        boolean changed = registrationChanged || resultChanged;
        if (changed) {
            mutationGuard.bump(event);
        }
        ResultIssueWorkSyncStatus previous = item.getSyncStatus();
        item.setBaseResultStatus(result == null ? null : result.getStatus());
        item.setBaseGunTime(result == null ? null : result.getGunTime());
        item.setBaseChipTime(result == null ? null : result.getChipTime());
        item.setDesiredResultStatus(result == null ? null : result.getStatus());
        item.setDesiredGunTime(result == null ? null : result.getGunTime());
        item.setDesiredChipTime(result == null ? null : result.getChipTime());
        item.setResult(result);
        rebaseRegistration(item, registration);
        item.setSyncStatus(ResultIssueWorkSyncStatus.SYNCED);
        item.setSyncErrorCode(null);
        item.setUpdatedBy(actor);
        item.setSyncedAt(Instant.now());
        item.setSyncedBy(actor);
        item = workItemRepository.saveAndFlush(item);
        auditService.save(item, actor,
                auditService.change("syncStatus", previous, ResultIssueWorkSyncStatus.SYNCED));
        return SyncOutcome.APPLIED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long workItemId, String code, String actor) {
        ResultIssueWorkItem item = workItemRepository.findByIdForUpdate(workItemId).orElse(null);
        if (item == null || item.getSyncStatus() != ResultIssueWorkSyncStatus.PENDING) {
            return;
        }
        ResultIssueWorkSyncStatus previous = item.getSyncStatus();
        String previousCode = item.getSyncErrorCode();
        item.setSyncStatus(ResultIssueWorkSyncStatus.FAILED);
        item.setSyncErrorCode(code);
        item.setUpdatedBy(actor);
        item = workItemRepository.saveAndFlush(item);
        auditService.save(item, actor,
                auditService.change("syncStatus", previous, ResultIssueWorkSyncStatus.FAILED),
                auditService.change("syncErrorCode", previousCode, code));
    }

    private SyncOutcome conflict(ResultIssueWorkItem item, String actor, String code) {
        ResultIssueWorkSyncStatus previous = item.getSyncStatus();
        String previousCode = item.getSyncErrorCode();
        item.setSyncStatus(ResultIssueWorkSyncStatus.CONFLICT);
        item.setSyncErrorCode(code);
        item.setUpdatedBy(actor);
        item = workItemRepository.saveAndFlush(item);
        auditService.save(item, actor,
                auditService.change("syncStatus", previous, ResultIssueWorkSyncStatus.CONFLICT),
                auditService.change("syncErrorCode", previousCode, code));
        return SyncOutcome.CONFLICT;
    }

    private static boolean hasResultChanges(ResultIssueWorkItem item) {
        return !Objects.equals(item.getBaseResultStatus(), item.getDesiredResultStatus())
                || !Objects.equals(milliseconds(item.getBaseGunTime()), milliseconds(item.getDesiredGunTime()))
                || !Objects.equals(milliseconds(item.getBaseChipTime()), milliseconds(item.getDesiredChipTime()));
    }

    private static boolean sameRelevantResultState(ResultIssueWorkItem item, Result result) {
        return result != null
                && (Objects.equals(item.getBaseResultStatus(), item.getDesiredResultStatus())
                    || Objects.equals(item.getBaseResultStatus(), result.getStatus()))
                && (Objects.equals(milliseconds(item.getBaseGunTime()), milliseconds(item.getDesiredGunTime()))
                    || Objects.equals(milliseconds(item.getBaseGunTime()), milliseconds(result.getGunTime())))
                && (Objects.equals(milliseconds(item.getBaseChipTime()), milliseconds(item.getDesiredChipTime()))
                    || Objects.equals(milliseconds(item.getBaseChipTime()), milliseconds(result.getChipTime())));
    }

    private static boolean sameRelevantRegistrationState(ResultIssueWorkItem item, Registration registration) {
        return (Objects.equals(item.getBaseDisplayName(), item.getDesiredDisplayName())
                    || Objects.equals(item.getBaseDisplayName(), registration.getDisplayName()))
                && (Objects.equals(item.getBaseGender(), item.getDesiredGender())
                    || Objects.equals(item.getBaseGender(), registration.getGender()))
                && (Objects.equals(item.getBaseBirthDate(), item.getDesiredBirthDate())
                    || Objects.equals(item.getBaseBirthDate(), registration.getBirthDate()))
                && (Objects.equals(item.getBaseBib(), item.getDesiredBib())
                    || Objects.equals(item.getBaseBib(), registration.getBib()))
                && (Objects.equals(id(item.getBaseRace()), id(item.getDesiredRace()))
                    || Objects.equals(id(item.getBaseRace()), registration.getRace().getId()));
    }

    private static boolean registrationFieldChanged(Object base, Object desired) {
        return !Objects.equals(base, desired);
    }

    private static void ensureRegistrationSnapshot(ResultIssueWorkItem item, Registration registration) {
        if (item.getBaseRace() != null) {
            return;
        }
        rebaseRegistration(item, registration);
    }

    private static void rebaseRegistration(ResultIssueWorkItem item, Registration registration) {
        item.setBaseDisplayName(registration.getDisplayName());
        item.setDesiredDisplayName(registration.getDisplayName());
        item.setBaseGender(registration.getGender());
        item.setDesiredGender(registration.getGender());
        item.setBaseBirthDate(registration.getBirthDate());
        item.setDesiredBirthDate(registration.getBirthDate());
        item.setBaseBib(registration.getBib());
        item.setDesiredBib(registration.getBib());
        item.setBaseRace(registration.getRace());
        item.setDesiredRace(registration.getRace());
    }

    private static Long id(ru.sportsresults.domain.Race race) {
        return race == null ? null : race.getId();
    }

    private static Long milliseconds(Duration value) {
        return value == null ? null : value.toMillis();
    }

    public enum SyncOutcome {
        APPLIED,
        CONFLICT,
        SKIPPED
    }
}
