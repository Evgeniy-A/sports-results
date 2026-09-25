package ru.sportsresults.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import ru.sportsresults.storage.attachments.YandexDiskAttachmentObjectStorage;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "app.result-issues.attachments",
        name = "storage-provider",
        havingValue = "yandex-disk"
)
@EnableConfigurationProperties(YandexDiskAttachmentStorageProperties.class)
public class YandexDiskAttachmentStorageConfiguration {

    @Bean("yandexDiskRestClient")
    RestClient yandexDiskRestClient(YandexDiskAttachmentStorageProperties properties) {
        return RestClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "OAuth " + properties.token())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    YandexDiskAttachmentObjectStorage yandexDiskAttachmentObjectStorage(
            @Qualifier("yandexDiskRestClient") RestClient restClient,
            YandexDiskAttachmentStorageProperties properties
    ) {
        return new YandexDiskAttachmentObjectStorage(restClient, properties);
    }
}
