package ru.sportsresults.repository;

import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;

import java.time.Instant;

public record AdminResultIssueListProjection(
        Long issueId,
        ResultIssueStatus status,
        ResultIssueType issueType,
        ResultCorrectionReason correctionReason,
        Instant createdAt,
        Instant queueArchivedAt,
        Long registrationId,
        String bib,
        String displayName,
        Long raceId,
        String raceName,
        Long sportFormatId,
        String sportFormatName,
        Long resultId,
        Long attachmentCount
) {
}
