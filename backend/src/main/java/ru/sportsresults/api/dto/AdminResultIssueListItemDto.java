package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;

import java.time.Instant;

public record AdminResultIssueListItemDto(
        Long issueId,
        ResultIssueStatus status,
        ResultIssueType issueType,
        ResultCorrectionReason correctionReason,
        Instant createdAt,
        Instant queueArchivedAt,
        AdminResultIssueRegistrationSummaryDto registration,
        AdminResultIssueRaceSummaryDto race,
        Long resultId,
        long attachmentCount
) {
}
