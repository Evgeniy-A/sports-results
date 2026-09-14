package ru.sportsresults.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.result-issues.attachments")
public record ResultIssueAttachmentProperties(
        @Positive long maxFileSizeBytes,
        @Positive int maxAttachmentsPerIssue,
        @NotNull Duration issueTokenTtl,
        @NotNull Duration uploadAuthorizationTtl,
        @NotNull Duration downloadAuthorizationTtl,
        @Positive int retentionDays
) {
}
