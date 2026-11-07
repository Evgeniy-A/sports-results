package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.ResetResultIssueWorkItemRequest;
import ru.sportsresults.api.dto.ResultIssueWorkItemDto;
import ru.sportsresults.api.dto.UpdateResultIssueWorkItemRequest;
import ru.sportsresults.domain.AdminUser;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.ResultIssueRequest;
import ru.sportsresults.domain.ResultIssueWorkItem;
import ru.sportsresults.domain.ResultIssueWorkSyncStatus;
import ru.sportsresults.repository.AdminUserRepository;
import ru.sportsresults.repository.ResultIssueRequestRepository;
import ru.sportsresults.repository.ResultIssueWorkItemRepository;
import ru.sportsresults.repository.ResultRepository;

import java.time.Duration;
import java.util.Objects;

@Service
public class ResultIssueWorkItemService {

    private final ResultIssueRequestRepository issueRepository;
    private final ResultIssueWorkItemRepository workItemRepository;
    private final ResultRepository resultRepository;
    private final AdminUserRepository userRepository;
    private final EventResultDataMutationGuard mutationGuard;
    private final ResultFactualMutationService factualMutationService;
    private final ResultIssueWorkItemAuditService auditService;

    public ResultIssueWorkItemService(
            ResultIssueRequestRepository issueRepository,
            ResultIssueWorkItemRepository workItemRepository,
            ResultRepository resultRepository,
            AdminUserRepository userRepository,
            EventResultDataMutationGuard mutationGuard,
            ResultFactualMutationService factualMutationService,
            ResultIssueWorkItemAuditService auditService
    ) {
        this.issueRepository = issueRepository;
        this.workItemRepository = workItemRepository;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.mutationGuard = mutationGuard;
        this.factualMutationService = factualMutationService;
        this.auditService = auditService;
    }

    @Transactional
    public ResultIssueWorkItemDto update(
            Long issueId,
            UpdateResultIssueWorkItemRequest request,
            String actor
    ) {
        ResultIssueRequest issue = lockIssue(issueId);
        Result currentResult = currentResult(issue);
        validateDesiredState(currentResult, request.desiredResultStatus(),
                request.desiredGunTimeMs(), request.desiredChipTimeMs());
        String desiredStatus = currentResult == null
                ? null : factualMutationService.normalizeStatus(request.desiredResultStatus());
        AdminUser assignee = assignee(request.assigneeUserId());
        String comment = normalizeComment(request.internalComment());

        ResultIssueWorkItem item = workItemRepository.findByIssueIdForUpdate(issueId).orElse(null);
        validateVersion(item, request.expectedVersion());
        if (item == null && !requiresPersistence(
                currentResult, desiredStatus, request.desiredGunTimeMs(), request.desiredChipTimeMs(), assignee, comment)) {
            return transientState(issue, currentResult, actor);
        }

        boolean created = item == null;
        if (created) {
            item = create(issue, currentResult, actor);
            // Persist the base snapshot before applying the requested edit. This makes the first
            // successful edit advance the optimistic version from the public "not created" token
            // (0), so another client that also loaded an absent work item receives a controlled
            // version conflict instead of silently overwriting the first edit.
            item = workItemRepository.saveAndFlush(item);
        } else if (item.getSyncStatus() == ResultIssueWorkSyncStatus.SYNCED) {
            rebase(item, currentResult);
        }

        var statusChange = auditService.change("desiredResultStatus", item.getDesiredResultStatus(), desiredStatus);
        var gunChange = auditService.change("desiredGunTimeMs", milliseconds(item.getDesiredGunTime()),
                request.desiredGunTimeMs());
        var chipChange = auditService.change("desiredChipTimeMs", milliseconds(item.getDesiredChipTime()),
                request.desiredChipTimeMs());
        var assigneeChange = auditService.change("assigneeUserId", id(item.getAssignee()), id(assignee));
        var commentChange = auditService.change("internalComment", item.getInternalComment(), comment);
        ResultIssueWorkSyncStatus nextSyncStatus = pending(
                currentResult, desiredStatus, request.desiredGunTimeMs(), request.desiredChipTimeMs())
                ? ResultIssueWorkSyncStatus.PENDING : ResultIssueWorkSyncStatus.SYNCED;
        var syncChange = auditService.change("syncStatus", item.getSyncStatus(), nextSyncStatus);

        item.setResult(currentResult);
        item.setDesiredResultStatus(desiredStatus);
        item.setDesiredGunTime(duration(request.desiredGunTimeMs()));
        item.setDesiredChipTime(duration(request.desiredChipTimeMs()));
        item.setAssignee(assignee);
        item.setInternalComment(comment);
        item.setSyncStatus(nextSyncStatus);
        item.setSyncErrorCode(null);
        item.setUpdatedBy(actor);
        if (nextSyncStatus == ResultIssueWorkSyncStatus.SYNCED) {
            rebase(item, currentResult);
        }

        boolean changed = created || statusChange != null || gunChange != null || chipChange != null
                || assigneeChange != null || commentChange != null || syncChange != null;
        if (!changed) {
            return toDto(item);
        }
        item = workItemRepository.saveAndFlush(item);
        auditService.save(item, actor, statusChange, gunChange, chipChange, assigneeChange, commentChange, syncChange);
        return toDto(item);
    }

    @Transactional
    public ResultIssueWorkItemDto reset(
            Long issueId,
            ResetResultIssueWorkItemRequest request,
            String actor
    ) {
        ResultIssueRequest issue = lockIssue(issueId);
        Result currentResult = currentResult(issue);
        ResultIssueWorkItem item = workItemRepository.findByIssueIdForUpdate(issueId).orElse(null);
        validateVersion(item, request.expectedVersion());
        if (item == null) {
            return transientState(issue, currentResult, actor);
        }

        var statusChange = auditService.change("desiredResultStatus", item.getDesiredResultStatus(),
                currentResult == null ? null : currentResult.getStatus());
        var gunChange = auditService.change("desiredGunTimeMs", milliseconds(item.getDesiredGunTime()),
                currentResult == null ? null : milliseconds(currentResult.getGunTime()));
        var chipChange = auditService.change("desiredChipTimeMs", milliseconds(item.getDesiredChipTime()),
                currentResult == null ? null : milliseconds(currentResult.getChipTime()));
        var syncChange = auditService.change("syncStatus", item.getSyncStatus(), ResultIssueWorkSyncStatus.SYNCED);
        item.setResult(currentResult);
        rebase(item, currentResult);
        item.setSyncStatus(ResultIssueWorkSyncStatus.SYNCED);
        item.setSyncErrorCode(null);
        item.setUpdatedBy(actor);
        item = workItemRepository.saveAndFlush(item);
        auditService.save(item, actor, statusChange, gunChange, chipChange, syncChange);
        return toDto(item);
    }

    private ResultIssueRequest lockIssue(Long issueId) {
        Long eventId = issueRepository.findEventIdByIssueId(issueId)
                .orElseThrow(ResultIssueWorkItemService::notFound);
        mutationGuard.lock(eventId);
        return issueRepository.findByIdAndEventIdForUpdate(issueId, eventId)
                .orElseThrow(ResultIssueWorkItemService::notFound);
    }

    private Result currentResult(ResultIssueRequest issue) {
        Registration registration = issue.getRegistration();
        if (!registration.isCurrent()) {
            return null;
        }
        return resultRepository.findByRegistrationId(registration.getId()).orElse(null);
    }

    private void validateDesiredState(Result result, String status, Long gunTime, Long chipTime) {
        factualMutationService.validateTime(gunTime, "desiredGunTimeMs");
        factualMutationService.validateTime(chipTime, "desiredChipTimeMs");
        if (result == null) {
            if (status != null || gunTime != null || chipTime != null) {
                throw new RequestConflictException(
                        "RESULT_ISSUE_CURRENT_RESULT_MISSING",
                        "Current result is missing; factual result fields cannot be edited"
                );
            }
            return;
        }
        if (status == null || status.isBlank()) {
            throw new InvalidRequestException("RESULT_STATUS_REQUIRED", "desiredResultStatus is required");
        }
    }

    private AdminUser assignee(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new InvalidRequestException(
                        "INVALID_JOURNAL_ASSIGNEE", "Assignee must be an active administrative user"));
    }

    private static void validateVersion(ResultIssueWorkItem item, long expectedVersion) {
        long actual = item == null ? 0 : item.getVersion();
        if (actual != expectedVersion) {
            throw new RequestConflictException(
                    "JOURNAL_WORK_ITEM_VERSION_CONFLICT",
                    "Result issue work item was changed by another employee"
            );
        }
    }

    private static boolean requiresPersistence(
            Result result,
            String status,
            Long gunTime,
            Long chipTime,
            AdminUser assignee,
            String comment
    ) {
        return pending(result, status, gunTime, chipTime) || assignee != null || comment != null;
    }

    private static boolean pending(Result result, String status, Long gunTime, Long chipTime) {
        return result != null && (!Objects.equals(result.getStatus(), status)
                || !Objects.equals(milliseconds(result.getGunTime()), gunTime)
                || !Objects.equals(milliseconds(result.getChipTime()), chipTime));
    }

    private static ResultIssueWorkItem create(ResultIssueRequest issue, Result result, String actor) {
        ResultIssueWorkItem item = new ResultIssueWorkItem();
        item.setIssueRequest(issue);
        item.setResult(result);
        item.setUpdatedBy(actor);
        rebase(item, result);
        item.setSyncStatus(ResultIssueWorkSyncStatus.SYNCED);
        return item;
    }

    private static void rebase(ResultIssueWorkItem item, Result result) {
        String status = result == null ? null : result.getStatus();
        Duration gun = result == null ? null : result.getGunTime();
        Duration chip = result == null ? null : result.getChipTime();
        item.setBaseResultStatus(status);
        item.setBaseGunTime(gun);
        item.setBaseChipTime(chip);
        item.setDesiredResultStatus(status);
        item.setDesiredGunTime(gun);
        item.setDesiredChipTime(chip);
    }

    private static ResultIssueWorkItemDto transientState(
            ResultIssueRequest issue,
            Result result,
            String actor
    ) {
        return new ResultIssueWorkItemDto(
                null,
                issue.getId(),
                result == null ? null : result.getId(),
                result == null ? null : result.getStatus(),
                result == null ? null : milliseconds(result.getGunTime()),
                result == null ? null : milliseconds(result.getChipTime()),
                null,
                null,
                null,
                ResultIssueWorkSyncStatus.SYNCED,
                null,
                0,
                issue.getUpdatedAt(),
                actor,
                null,
                null
        );
    }

    public static ResultIssueWorkItemDto toDto(ResultIssueWorkItem item) {
        return new ResultIssueWorkItemDto(
                item.getId(),
                item.getIssueRequest().getId(),
                id(item.getResult()),
                item.getDesiredResultStatus(),
                milliseconds(item.getDesiredGunTime()),
                milliseconds(item.getDesiredChipTime()),
                id(item.getAssignee()),
                item.getAssignee() == null ? null : item.getAssignee().getDisplayName(),
                item.getInternalComment(),
                item.getSyncStatus(),
                item.getSyncErrorCode(),
                item.getVersion(),
                item.getUpdatedAt(),
                item.getUpdatedBy(),
                item.getSyncedAt(),
                item.getSyncedBy()
        );
    }

    private static String normalizeComment(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Long id(Object value) {
        if (value instanceof AdminUser user) return user.getId();
        if (value instanceof Result result) return result.getId();
        return null;
    }

    private static Long milliseconds(Duration value) {
        return value == null ? null : value.toMillis();
    }

    private static Duration duration(Long value) {
        return value == null ? null : Duration.ofMillis(value);
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("RESULT_ISSUE_NOT_FOUND", "Result issue request not found");
    }
}
