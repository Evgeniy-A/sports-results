package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateResultIssueAttachmentRequest(
        @NotBlank @Size(max = 512) String originalFileName,
        @Size(max = 255) String contentType,
        @Positive long sizeBytes
) {
}
