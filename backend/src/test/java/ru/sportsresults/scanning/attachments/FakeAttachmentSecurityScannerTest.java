package ru.sportsresults.scanning.attachments;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class FakeAttachmentSecurityScannerTest {

    @ParameterizedTest
    @EnumSource(FakeAttachmentScanResult.class)
    void returnsOnlyTheExplicitlyConfiguredDevelopmentResult(FakeAttachmentScanResult configuredResult) {
        var scanner = new FakeAttachmentSecurityScanner(
                new FakeAttachmentScannerProperties(configuredResult)
        );

        var result = scanner.scan(new AttachmentScanCandidate(
                17L, "result-issues/1/2/object", "video/mp4", 125_000_000L
        ));

        assertThat(result.status().name()).isEqualTo(configuredResult.name());
        assertThat(result.detectedContentType()).isNull();
    }
}
