package ru.sportsresults.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "app.result-issues.attachments.yandex-disk")
public record YandexDiskAttachmentStorageProperties(
        @NotNull URI apiBaseUrl,
        @NotBlank String rootPath,
        @NotBlank String token
) {
}
