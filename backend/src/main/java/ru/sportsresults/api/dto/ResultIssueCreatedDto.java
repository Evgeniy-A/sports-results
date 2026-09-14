package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;

import java.time.Instant;

public record ResultIssueCreatedDto(
        Long issueId,
        ResultIssueType type,
        ResultIssueStatus status,
        Instant createdAt,
        String attachmentUploadToken,
        Instant attachmentUploadTokenExpiresAt
) {
}
