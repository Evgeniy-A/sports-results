package ru.sportsresults.repository;

import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueArchiveReason;
import ru.sportsresults.domain.ResultIssueSnapshotOrigin;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public record GlobalResultIssueExportProjection(
        Long issueId,
        String eventName,
        String eventLocation,
        Instant eventStartsAt,
        String sportFormatName,
        String raceName,
        BigDecimal raceDistanceMeters,
        String bib,
        String displayName,
        String categoryName,
        String sourceCategory,
        ResultIssueType issueType,
        ResultCorrectionReason correctionReason,
        ResultIssueStatus status,
        Instant queueArchivedAt,
        ResultIssueArchiveReason queueArchiveReason,
        Instant createdAt,
        Instant updatedAt,
        String observedResultStatus,
        Duration observedGunTime,
        Duration observedChipTime,
        String contactEmail,
        String message,
        Duration claimedGunTime,
        Duration claimedChipTime,
        Instant estimatedStartAt,
        Instant estimatedFinishAt,
        ResultIssueSnapshotOrigin snapshotOrigin,
        String currentRaceName,
        String currentCategoryName,
        Instant registrationRetiredAt
) {
}
