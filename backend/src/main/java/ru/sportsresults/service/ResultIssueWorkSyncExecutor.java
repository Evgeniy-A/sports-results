package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.ResultIssueWorkItem;
import ru.sportsresults.domain.ResultIssueWorkSyncStatus;
import ru.sportsresults.repository.ResultIssueWorkItemRepository;
import ru.sportsresults.repository.ResultRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
public class ResultIssueWorkSyncExecutor {

    private final ResultIssueWorkItemRepository workItemRepository;
    private final ResultRepository resultRepository;
    private final EventResultDataMutationGuard mutationGuard;
    private final ResultFactualMutationService factualMutationService;
    private final ResultIssueWorkItemAuditService auditService;

    public ResultIssueWorkSyncExecutor(
            ResultIssueWorkItemRepository workItemRepository,
            ResultRepository resultRepository,
            EventResultDataMutationGuard mutationGuard,
            ResultFactualMutationService factualMutationService,
            ResultIssueWorkItemAuditService auditService
    ) {
        this.workItemRepository = workItemRepository;
        this.resultRepository = resultRepository;
        this.mutationGuard = mutationGuard;
        this.factualMutationService = factualMutationService;
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

        Registration registration = item.getIssueRequest().getRegistration();
        if (!registration.isCurrent()) {
            throw new RequestConflictException(
                    "JOURNAL_REGISTRATION_RETIRED", "Current registration is retired");
        }
        Result result = resultRepository.findByRegistrationId(registration.getId())
                .orElseThrow(() -> new RequestConflictException(
                        "JOURNAL_RESULT_MISSING", "Current result is missing"));
        if (item.getResult() == null || !Objects.equals(item.getResult().getId(), result.getId())) {
            return conflict(item, actor, "JOURNAL_RESULT_RELATION_CHANGED");
        }
        if (!sameOfficialState(item, result)) {
            return conflict(item, actor, "JOURNAL_OFFICIAL_RESULT_CHANGED");
        }

        boolean changed = factualMutationService.apply(
                result,
                item.getDesiredResultStatus(),
                milliseconds(item.getDesiredGunTime()),
                milliseconds(item.getDesiredChipTime()),
                actor
        );
        if (changed) {
            mutationGuard.bump(event);
        }
        ResultIssueWorkSyncStatus previous = item.getSyncStatus();
        item.setBaseResultStatus(result.getStatus());
        item.setBaseGunTime(result.getGunTime());
        item.setBaseChipTime(result.getChipTime());
        item.setDesiredResultStatus(result.getStatus());
        item.setDesiredGunTime(result.getGunTime());
        item.setDesiredChipTime(result.getChipTime());
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

    private static boolean sameOfficialState(ResultIssueWorkItem item, Result result) {
        return Objects.equals(item.getBaseResultStatus(), result.getStatus())
                && Objects.equals(milliseconds(item.getBaseGunTime()), milliseconds(result.getGunTime()))
                && Objects.equals(milliseconds(item.getBaseChipTime()), milliseconds(result.getChipTime()));
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
