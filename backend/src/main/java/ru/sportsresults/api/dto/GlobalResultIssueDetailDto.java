package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueArchiveReason;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GlobalResultIssueDetailDto(
        Long issueId,
        Long eventId,
        ResultIssueType issueType,
        ResultCorrectionReason correctionReason,
        ResultIssueStatus status,
        String contactEmail,
        String message,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Long claimedGunTimeMs,
        Long claimedChipTimeMs,
        Instant estimatedStartAt,
        Instant estimatedFinishAt,
        Instant queueArchivedAt,
        String queueArchivedBy,
        ResultIssueArchiveReason queueArchiveReason,
        UUID queueArchivedImportOperationId,
        AdminResultIssueSnapshotDto historicalSnapshot,
        GlobalResultIssueCurrentContextDto currentContext,
        long attachmentCount,
        List<AdminResultIssueAttachmentDto> attachments,
        List<GlobalResultIssueHistoryDto> history
) {
    public GlobalResultIssueDetailDto {
        attachments = List.copyOf(attachments);
        history = List.copyOf(history);
    }
}
