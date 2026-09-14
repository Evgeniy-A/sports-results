package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultIssueArchiveReason;
import ru.sportsresults.domain.ResultIssueStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminResultIssueArchiveDto(
        Long issueId,
        ResultIssueStatus status,
        Instant queueArchivedAt,
        String queueArchivedBy,
        ResultIssueArchiveReason queueArchiveReason,
        UUID queueArchivedImportOperationId
) {
}
