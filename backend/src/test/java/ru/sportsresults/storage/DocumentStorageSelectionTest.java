package ru.sportsresults.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.sportsresults.config.YandexDiskDocumentStorageConfiguration;
import ru.sportsresults.domain.EventDocumentStorageProvider;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStorageSelectionTest {
    @TempDir
    Path storageRoot;

    @Test
    void localIsTheDefaultWithoutAYandexToken() {
        contextRunner()
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageService.class);
                    assertThat(context.getBean(FileStorageService.class).defaultProvider())
                            .isEqualTo(EventDocumentStorageProvider.LOCAL);
                });
    }

    @Test
    void yandexDiskCanBeSelectedForNewDocuments() {
        contextRunner()
                .withPropertyValues(
                        "app.documents.storage-provider=yandex-disk",
                        "app.documents.yandex-disk.token=test-token"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FileStorageService.class).defaultProvider())
                            .isEqualTo(EventDocumentStorageProvider.YANDEX_DISK);
                });
    }

    @Test
    void yandexDiskSelectionFailsClosedWithoutAToken() {
        contextRunner()
                .withPropertyValues("app.documents.storage-provider=yandex-disk")
                .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withPropertyValues(
                        "app.documents.storage-root=" + storageRoot.toAbsolutePath(),
                        "app.documents.yandex-disk.api-base-url=https://cloud-api.example.test/v1/disk",
                        "app.documents.yandex-disk.root-path=app:/"
                )
                .withUserConfiguration(StorageTestConfiguration.class);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            FileStorageService.class,
            LocalFileStorageService.class,
            YandexDiskDocumentStorageConfiguration.class
    })
    static class StorageTestConfiguration {
    }
}
