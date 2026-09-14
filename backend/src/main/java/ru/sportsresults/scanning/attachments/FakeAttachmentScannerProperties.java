package ru.sportsresults.scanning.attachments;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.result-issues.attachments.fake-scanner")
public record FakeAttachmentScannerProperties(
        @NotNull FakeAttachmentScanResult result
) {
}
