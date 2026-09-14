package ru.sportsresults.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.sportsresults.scanning.attachments.AttachmentSecurityScanner;
import ru.sportsresults.scanning.attachments.FakeAttachmentScannerProperties;
import ru.sportsresults.scanning.attachments.FakeAttachmentSecurityScanner;

@Configuration(proxyBeanMethods = false)
@Profile("local")
@ConditionalOnProperty(
        prefix = "app.result-issues.attachments",
        name = "scanner-provider",
        havingValue = "fake"
)
@EnableConfigurationProperties(FakeAttachmentScannerProperties.class)
public class FakeAttachmentScannerConfiguration {

    @Bean
    AttachmentSecurityScanner fakeAttachmentSecurityScanner(FakeAttachmentScannerProperties properties) {
        return new FakeAttachmentSecurityScanner(properties);
    }
}
