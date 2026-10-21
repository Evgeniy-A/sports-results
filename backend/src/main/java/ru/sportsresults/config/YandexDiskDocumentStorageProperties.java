package ru.sportsresults.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "app.documents.yandex-disk")
public record YandexDiskDocumentStorageProperties(
        @NotNull URI apiBaseUrl,
        @NotBlank String rootPath,
        String token
) {
}
