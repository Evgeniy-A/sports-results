package ru.sportsresults.storage.attachments;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.sportsresults.config.S3AttachmentStorageConfiguration;
import ru.sportsresults.config.YandexDiskAttachmentStorageConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentStorageSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StorageTestConfiguration.class);

    @Test
    void memoryProviderKeepsOnlyTheDevelopmentAdapter() {
        contextRunner
                .withPropertyValues("app.result-issues.attachments.storage-provider=memory")
                .run(context -> {
                    assertThat(context).hasSingleBean(AttachmentObjectStorage.class);
                    assertThat(context).hasSingleBean(InMemoryAttachmentObjectStorage.class);
                    assertThat(context).doesNotHaveBean(S3AttachmentObjectStorage.class);
                    assertThat(context).doesNotHaveBean(YandexDiskAttachmentObjectStorage.class);
                });
    }

    @Test
    void s3ProviderBuildsOnlyTheGenericS3AdapterFromExternalConfiguration() {
        contextRunner
                .withPropertyValues(
                        "app.result-issues.attachments.storage-provider=s3",
                        "app.result-issues.attachments.s3.endpoint=https://objects.example.test",
                        "app.result-issues.attachments.s3.region=region-1",
                        "app.result-issues.attachments.s3.bucket=private-bucket",
                        "app.result-issues.attachments.s3.access-key=test-access",
                        "app.result-issues.attachments.s3.secret-key=test-secret",
                        "app.result-issues.attachments.s3.path-style-access=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AttachmentObjectStorage.class);
                    assertThat(context).hasSingleBean(S3AttachmentObjectStorage.class);
                    assertThat(context).doesNotHaveBean(InMemoryAttachmentObjectStorage.class);
                    assertThat(context).doesNotHaveBean(YandexDiskAttachmentObjectStorage.class);
                });
    }

    @Test
    void yandexDiskProviderBuildsOnlyTheAppFolderAdapter() {
        contextRunner
                .withPropertyValues(
                        "app.result-issues.attachments.storage-provider=yandex-disk",
                        "app.result-issues.attachments.yandex-disk.api-base-url=https://cloud-api.example.test/v1/disk",
                        "app.result-issues.attachments.yandex-disk.root-path=app:/",
                        "app.result-issues.attachments.yandex-disk.token=test-token"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AttachmentObjectStorage.class);
                    assertThat(context).hasSingleBean(YandexDiskAttachmentObjectStorage.class);
                    assertThat(context).doesNotHaveBean(InMemoryAttachmentObjectStorage.class);
                    assertThat(context).doesNotHaveBean(S3AttachmentObjectStorage.class);
                });
    }

    @Test
    void yandexDiskProviderFailsClosedWhenTokenIsMissing() {
        contextRunner
                .withPropertyValues(
                        "app.result-issues.attachments.storage-provider=yandex-disk",
                        "app.result-issues.attachments.yandex-disk.api-base-url=https://cloud-api.example.test/v1/disk",
                        "app.result-issues.attachments.yandex-disk.root-path=app:/"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void s3ProviderFailsClosedWhenCredentialsAreMissing() {
        contextRunner
                .withPropertyValues(
                        "app.result-issues.attachments.storage-provider=s3",
                        "app.result-issues.attachments.s3.endpoint=https://objects.example.test",
                        "app.result-issues.attachments.s3.region=region-1",
                        "app.result-issues.attachments.s3.bucket=private-bucket"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            S3AttachmentStorageConfiguration.class,
            YandexDiskAttachmentStorageConfiguration.class,
            S3AttachmentObjectStorage.class,
            InMemoryAttachmentObjectStorage.class
    })
    static class StorageTestConfiguration {
    }
}
