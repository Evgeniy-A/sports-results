package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.AdminResultIssueArchiveDto;
import ru.sportsresults.domain.ResultIssueArchiveReason;
import ru.sportsresults.domain.ResultIssueRequest;
import ru.sportsresults.repository.ResultIssueRequestRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class ResultIssueLifecycleService {

    private final ResultIssueRequestRepository issueRepository;
    private final ResultIssueHistoryService historyService;
    private final EventResultDataMutationGuard mutationGuard;

    public ResultIssueLifecycleService(
            ResultIssueRequestRepository issueRepository,
            ResultIssueHistoryService historyService,
            EventResultDataMutationGuard mutationGuard
    ) {
        this.issueRepository = issueRepository;
        this.historyService = historyService;
        this.mutationGuard = mutationGuard;
    }

    @Transactional
    public AdminResultIssueArchiveDto archiveManual(
            Long eventId,
            Long issueId,
            ResultIssueArchiveReason reason,
            String actor
    ) {
        mutationGuard.lock(eventId);
        if (reason == ResultIssueArchiveReason.EMERGENCY_REPLACEMENT) {
            throw new InvalidRequestException(
                    "INVALID_RESULT_ISSUE_ARCHIVE_REASON",
                    "Emergency replacement archive requires an import operation"
            );
        }
        ResultIssueRequest issue = issueRepository.findByIdAndEventIdForUpdate(issueId, eventId)
                .orElseThrow(ResultIssueLifecycleService::notFound);
        archive(issue, reason, null, actor, Instant.now());
        return toDto(issue);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean archive(
            ResultIssueRequest issue,
            ResultIssueArchiveReason reason,
            UUID importOperationId,
            String actor,
            Instant at
    ) {
        if (issue.getQueueArchivedAt() != null) {
            return false;
        }
        issue.setQueueArchivedAt(at);
        issue.setQueueArchivedBy(actor);
        issue.setQueueArchiveReason(reason);
        issue.setQueueArchivedImportOperationId(importOperationId);
        issueRepository.save(issue);
        historyService.queueArchived(issue, actor, reason.name(), at);
        return true;
    }

    private static AdminResultIssueArchiveDto toDto(ResultIssueRequest issue) {
        return new AdminResultIssueArchiveDto(
                issue.getId(),
                issue.getStatus(),
                issue.getQueueArchivedAt(),
                issue.getQueueArchivedBy(),
                issue.getQueueArchiveReason(),
                issue.getQueueArchivedImportOperationId()
        );
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException(
                "RESULT_ISSUE_NOT_FOUND", "Result issue request not found in this event"
        );
    }
}
