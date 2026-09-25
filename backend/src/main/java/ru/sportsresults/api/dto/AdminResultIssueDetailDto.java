package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;

import java.time.Instant;
import java.util.List;

public record AdminResultIssueDetailDto(
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
        Long observedGunTimeMs,
        Long observedChipTimeMs,
        String observedResultStatus,
        AdminResultIssueArchiveDto archive,
        AdminResultIssueSnapshotDto snapshot,
        AdminResultIssueRegistrationDto registration,
        AdminResultIssueResultDto result,
        List<AdminResultIssueAttachmentDto> attachments,
        List<GlobalResultIssueHistoryDto> history
) {
    public AdminResultIssueDetailDto {
        attachments = List.copyOf(attachments);
        history = List.copyOf(history);
    }
}
