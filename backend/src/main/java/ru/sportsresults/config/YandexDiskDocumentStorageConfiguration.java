package ru.sportsresults.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import ru.sportsresults.storage.YandexDiskDocumentStorage;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(YandexDiskDocumentStorageProperties.class)
public class YandexDiskDocumentStorageConfiguration {

    @Bean("yandexDiskDocumentApiRestClient")
    RestClient yandexDiskDocumentApiRestClient(YandexDiskDocumentStorageProperties properties) {
        return RestClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "OAuth " + nullToEmpty(properties.token()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean("yandexDiskDocumentTransferRestClient")
    RestClient yandexDiskDocumentTransferRestClient() {
        return RestClient.builder().build();
    }

    @Bean
    YandexDiskDocumentStorage yandexDiskDocumentStorage(
            @Qualifier("yandexDiskDocumentApiRestClient") RestClient apiRestClient,
            @Qualifier("yandexDiskDocumentTransferRestClient") RestClient transferRestClient,
            YandexDiskDocumentStorageProperties properties
    ) {
        return new YandexDiskDocumentStorage(apiRestClient, transferRestClient, properties);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
