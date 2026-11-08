package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;
import ru.sportsresults.domain.ResultIssueWorkSyncStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;

public record ResultIssueWorkspaceRowDto(
        Long issueId,
        Long seriesId,
        String seriesName,
        Long eventId,
        String eventName,
        int eventYear,
        String location,
        Long currentRaceId,
        String currentRaceName,
        BigDecimal currentDistanceMeters,
        Long registrationId,
        boolean registrationCurrent,
        String currentBib,
        String currentDisplayName,
        String currentGender,
        LocalDate currentBirthDate,
        String currentCategoryName,
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
            String desiredDisplayName,
            String desiredGender,
            LocalDate desiredBirthDate,
            String desiredBib,
            Long desiredRaceId,
            String desiredRaceName,
            BigDecimal desiredDistanceMeters,
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
