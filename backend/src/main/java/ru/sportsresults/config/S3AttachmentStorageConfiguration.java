package ru.sportsresults.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "app.result-issues.attachments",
        name = "storage-provider",
        havingValue = "s3"
)
@EnableConfigurationProperties(S3AttachmentStorageProperties.class)
public class S3AttachmentStorageConfiguration {

    @Bean
    S3Configuration attachmentS3ServiceConfiguration(S3AttachmentStorageProperties properties) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyleAccess())
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Client attachmentS3Client(
            S3AttachmentStorageProperties properties,
            S3Configuration serviceConfiguration
    ) {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(serviceConfiguration)
                .httpClient(UrlConnectionHttpClient.builder().build())
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner attachmentS3Presigner(
            S3AttachmentStorageProperties properties,
            S3Configuration serviceConfiguration
    ) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    private static StaticCredentialsProvider credentials(S3AttachmentStorageProperties properties) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
        );
    }
}
