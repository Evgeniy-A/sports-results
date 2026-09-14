package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultIssueStatus;

import java.time.Instant;

public record AdminResultIssueStatusDto(
        Long issueId,
        ResultIssueStatus status,
        Instant updatedAt,
        Instant resolvedAt
) {
}
