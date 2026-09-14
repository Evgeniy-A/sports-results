package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueArchiveReason;
import ru.sportsresults.domain.ResultIssueSnapshotOrigin;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GlobalResultIssueJournalItemDto(
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
        HistoricalEvent event,
        HistoricalSportFormat sportFormat,
        HistoricalRace race,
        HistoricalParticipant participant,
        HistoricalResult observedResult,
        ResultIssueSnapshotOrigin snapshotOrigin,
        String categoryName,
        long attachmentCount
) {
    public record HistoricalEvent(Long eventId, String eventName, String location, Instant startsAt) {
    }

    public record HistoricalSportFormat(Long sportFormatId, String name, String code) {
    }

    public record HistoricalRace(
            Long raceId,
            String name,
            String code,
            BigDecimal distanceMeters
    ) {
    }

    public record HistoricalParticipant(Long registrationId, String bib, String displayName) {
    }

    public record HistoricalResult(String status, Long gunTimeMs, Long chipTimeMs) {
    }
}
