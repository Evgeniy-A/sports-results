package ru.sportsresults.scanning.attachments;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.sportsresults.config.FakeAttachmentScannerConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class FakeAttachmentScannerSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ScannerTestConfiguration.class);

    @Test
    void explicitLocalProfileAndFakeProviderEnableTheSimulator() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "app.result-issues.attachments.scanner-provider=fake",
                        "app.result-issues.attachments.fake-scanner.result=CLEAN"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AttachmentSecurityScanner.class);
                    assertThat(context).hasSingleBean(FakeAttachmentSecurityScanner.class);
                });
    }

    @Test
    void productionProfileCannotEnableTheFakeScanner() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=production",
                        "app.result-issues.attachments.scanner-provider=fake",
                        "app.result-issues.attachments.fake-scanner.result=CLEAN"
                )
                .run(context -> assertThat(context).doesNotHaveBean(AttachmentSecurityScanner.class));
    }

    @Test
    void localProfileDoesNotEnableTheFakeScannerWithoutTheProviderSwitch() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "app.result-issues.attachments.scanner-provider=none"
                )
                .run(context -> assertThat(context).doesNotHaveBean(AttachmentSecurityScanner.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(FakeAttachmentScannerConfiguration.class)
    static class ScannerTestConfiguration {
    }
}
