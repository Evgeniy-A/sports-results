package ru.sportsresults.api.dto;

public record ResultIssueAttachmentCapabilitiesDto(
        boolean directUploadAvailable,
        long maxFileSizeBytes,
        int maxAttachmentsPerIssue
) {
}
