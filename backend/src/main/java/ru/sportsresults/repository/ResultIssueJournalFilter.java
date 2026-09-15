package ru.sportsresults.repository;

import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueArchiveReason;
import ru.sportsresults.domain.ResultIssueQueueScope;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Typed, reusable filter for historical Result Issue reads. All participant,
 * event presentation and race fields refer to the immutable issue snapshot.
 */
public record ResultIssueJournalFilter(
        Long issueId,
        Long eventId,
        String location,
        Instant eventStartsAtFrom,
        Instant eventStartsAtToExclusive,
        Instant createdFrom,
        Instant createdTo,
        Long raceId,
        String raceCode,
        String bib,
        String participant,
        ResultIssueType issueType,
        ResultCorrectionReason correctionReason,
        Set<ResultIssueStatus> statuses,
        ResultIssueQueueScope queueScope,
        ResultIssueArchiveReason queueArchiveReason,
        UUID queueArchivedImportOperationId
) {
    public ResultIssueJournalFilter {
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
    }
}
