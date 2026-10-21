package ru.sportsresults.storage;

import org.junit.jupiter.api.Test;
import ru.sportsresults.domain.EventDocumentStorageProvider;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageServiceRoutingTest {

    @Test
    void defaultOnlyControlsNewUploadsWhilePersistedProviderControlsReadsAndDeletes() throws Exception {
        RecordingStorage local = new RecordingStorage(EventDocumentStorageProvider.LOCAL);
        RecordingStorage yandex = new RecordingStorage(EventDocumentStorageProvider.YANDEX_DISK);
        FileStorageService router = new FileStorageService(List.of(local, yandex), "yandex-disk");

        FileStorageService.StoredDocument stored = router.store(123L, "pdf".getBytes(StandardCharsets.UTF_8));
        assertThat(stored.provider()).isEqualTo(EventDocumentStorageProvider.YANDEX_DISK);
        assertThat(yandex.storedEvents).containsExactly(123L);
        assertThat(local.storedEvents).isEmpty();

        assertThat(router.open(EventDocumentStorageProvider.LOCAL, "legacy.pdf").readAllBytes())
                .isEqualTo("LOCAL:legacy.pdf".getBytes(StandardCharsets.UTF_8));
        router.delete(EventDocumentStorageProvider.LOCAL, "legacy.pdf");
        router.delete(EventDocumentStorageProvider.YANDEX_DISK, "events/123/documents/new.pdf");

        assertThat(local.deletedKeys).containsExactly("legacy.pdf");
        assertThat(yandex.deletedKeys).containsExactly("events/123/documents/new.pdf");
    }

    @Test
    void existingYandexDocumentsRemainRoutableWhenNewUploadsDefaultToLocal() throws Exception {
        RecordingStorage local = new RecordingStorage(EventDocumentStorageProvider.LOCAL);
        RecordingStorage yandex = new RecordingStorage(EventDocumentStorageProvider.YANDEX_DISK);
        FileStorageService router = new FileStorageService(List.of(local, yandex), "local");

        assertThat(router.store(124L, new byte[]{1}).provider())
                .isEqualTo(EventDocumentStorageProvider.LOCAL);
        assertThat(router.open(EventDocumentStorageProvider.YANDEX_DISK,
                "events/123/documents/existing.pdf").readAllBytes())
                .isEqualTo("YANDEX_DISK:events/123/documents/existing.pdf".getBytes(StandardCharsets.UTF_8));
        router.delete(EventDocumentStorageProvider.YANDEX_DISK, "events/123/documents/existing.pdf");

        assertThat(yandex.deletedKeys).containsExactly("events/123/documents/existing.pdf");
    }

    private static final class RecordingStorage implements DocumentStorage {
        private final EventDocumentStorageProvider provider;
        private final List<Long> storedEvents = new ArrayList<>();
        private final List<String> deletedKeys = new ArrayList<>();

        private RecordingStorage(EventDocumentStorageProvider provider) {
            this.provider = provider;
        }

        @Override
        public EventDocumentStorageProvider provider() {
            return provider;
        }

        @Override
        public String store(Long eventId, byte[] content) {
            storedEvents.add(eventId);
            return provider + ".pdf";
        }

        @Override
        public InputStream open(String storageKey) {
            return new ByteArrayInputStream((provider + ":" + storageKey).getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void delete(String storageKey) {
            deletedKeys.add(storageKey);
        }

        @Override
        public boolean exists(String storageKey) {
            return true;
        }
    }
}
