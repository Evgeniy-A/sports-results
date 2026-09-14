package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import ru.sportsresults.domain.ResultIssueHistory;
import ru.sportsresults.domain.ResultIssueHistoryAction;
import ru.sportsresults.domain.ResultIssueRequest;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.repository.ResultIssueHistoryRepository;

import java.time.Instant;

@Service
public class ResultIssueHistoryService {

    private final ResultIssueHistoryRepository repository;

    public ResultIssueHistoryService(ResultIssueHistoryRepository repository) {
        this.repository = repository;
    }

    public void created(ResultIssueRequest issue, String actor, Instant at) {
        append(issue, ResultIssueHistoryAction.CREATED, null, ResultIssueStatus.NEW, actor, null, at);
    }

    public void statusChanged(
            ResultIssueRequest issue,
            ResultIssueStatus from,
            ResultIssueStatus to,
            String actor,
            Instant at
    ) {
        if (from != to) {
            append(issue, ResultIssueHistoryAction.STATUS_CHANGED, from, to, actor, null, at);
        }
    }

    public void queueArchived(ResultIssueRequest issue, String actor, String reason, Instant at) {
        append(issue, ResultIssueHistoryAction.QUEUE_ARCHIVED, null, null, actor, reason, at);
    }

    private void append(
            ResultIssueRequest issue,
            ResultIssueHistoryAction action,
            ResultIssueStatus from,
            ResultIssueStatus to,
            String actor,
            String reason,
            Instant at
    ) {
        ResultIssueHistory history = new ResultIssueHistory();
        history.setIssueRequest(issue);
        history.setAction(action);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setActor(actor);
        history.setReason(reason);
        history.setCreatedAt(at);
        repository.save(history);
    }
}
