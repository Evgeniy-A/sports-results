package ru.sportsresults.repository;

import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueArchiveReason;
import ru.sportsresults.domain.ResultIssueSnapshotOrigin;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record GlobalResultIssueJournalProjection(
        Long issueId,
        ResultIssueType issueType,
        ResultCorrectionReason correctionReason,
        ResultIssueStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant queueArchivedAt,
        String queueArchivedBy,
        ResultIssueArchiveReason queueArchiveReason,
        UUID queueArchivedImportOperationId,
        Long eventId,
        String eventName,
        String eventLocation,
        Instant eventStartsAt,
        Long sportFormatId,
        String sportFormatName,
        String sportFormatCode,
        Long raceId,
        String raceName,
        String raceCode,
        BigDecimal raceDistanceMeters,
        Long registrationId,
        String bib,
        String displayName,
        String categoryName,
        String observedResultStatus,
        Duration observedGunTime,
        Duration observedChipTime,
        ResultIssueSnapshotOrigin snapshotOrigin,
        Long attachmentCount
) {
}
