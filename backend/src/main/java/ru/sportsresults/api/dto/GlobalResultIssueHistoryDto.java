package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultIssueHistoryAction;
import ru.sportsresults.domain.ResultIssueStatus;

import java.time.Instant;

public record GlobalResultIssueHistoryDto(
        Long historyId,
        ResultIssueHistoryAction action,
        ResultIssueStatus fromStatus,
        ResultIssueStatus toStatus,
        String actor,
        String reason,
        Instant createdAt
) {
}
