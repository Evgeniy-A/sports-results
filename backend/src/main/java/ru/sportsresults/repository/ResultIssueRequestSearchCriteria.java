package ru.sportsresults.repository;

import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;
import ru.sportsresults.domain.ResultIssueQueueScope;

public record ResultIssueRequestSearchCriteria(
        Long eventId,
        ResultIssueStatus status,
        ResultIssueType issueType,
        Long issueIdFrom,
        Long issueIdTo,
        String bib,
        ResultCorrectionReason correctionReason,
        ResultIssueQueueScope queueScope
) {
}
