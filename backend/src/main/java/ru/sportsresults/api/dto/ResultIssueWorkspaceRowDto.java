package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;
import ru.sportsresults.domain.ResultIssueWorkSyncStatus;

import java.time.Instant;

public record ResultIssueWorkspaceRowDto(
        Long issueId,
        Long seriesId,
        String seriesName,
        Long eventId,
        String eventName,
        int eventYear,
        String location,
        Long raceId,
        String raceName,
        Long registrationId,
        boolean registrationCurrent,
        String bib,
        String participantName,
        String categoryName,
        ResultIssueType issueType,
        ResultCorrectionReason correctionReason,
        ResultIssueStatus issueStatus,
        long attachmentCount,
        Long currentResultId,
        String currentResultStatus,
        Long currentGunTimeMs,
        Long currentChipTimeMs,
        Long claimedGunTimeMs,
        Long claimedChipTimeMs,
        WorkItem workItem,
        Instant updatedAt
) {
    public record WorkItem(
            Long workItemId,
            String desiredResultStatus,
            Long desiredGunTimeMs,
            Long desiredChipTimeMs,
            Long assigneeUserId,
            String assigneeName,
            String internalComment,
            ResultIssueWorkSyncStatus syncStatus,
            String syncErrorCode,
            long version,
            Instant updatedAt,
            String updatedBy,
            Instant syncedAt,
            String syncedBy
    ) {
    }
}
