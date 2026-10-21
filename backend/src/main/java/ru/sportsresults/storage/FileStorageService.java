package ru.sportsresults.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.sportsresults.domain.EventDocumentStorageProvider;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class FileStorageService {
    private final Map<EventDocumentStorageProvider, DocumentStorage> providers;
    private final EventDocumentStorageProvider defaultProvider;

    public FileStorageService(
            List<DocumentStorage> storages,
            @Value("${app.documents.storage-provider:local}") String defaultProvider
    ) {
        this.providers = new EnumMap<>(EventDocumentStorageProvider.class);
        for (DocumentStorage storage : storages) {
            if (providers.put(storage.provider(), storage) != null) {
                throw new IllegalStateException("Duplicate document storage provider: " + storage.provider());
            }
        }
        this.defaultProvider = EventDocumentStorageProvider.fromConfiguration(defaultProvider);
        provider(this.defaultProvider).validateConfiguration();
    }

    public StoredDocument store(Long eventId, byte[] content) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("Event id must be positive");
        }
        DocumentStorage storage = provider(defaultProvider);
        return new StoredDocument(storage.provider(), storage.store(eventId, content));
    }

    public InputStream open(EventDocumentStorageProvider storageProvider, String storageKey) {
        return provider(storageProvider).open(storageKey);
    }

    public void delete(EventDocumentStorageProvider storageProvider, String storageKey) {
        provider(storageProvider).delete(storageKey);
    }

    public boolean exists(EventDocumentStorageProvider storageProvider, String storageKey) {
        return provider(storageProvider).exists(storageKey);
    }

    public EventDocumentStorageProvider defaultProvider() {
        return defaultProvider;
    }

    private DocumentStorage provider(EventDocumentStorageProvider storageProvider) {
        if (storageProvider == null) {
            throw new IllegalStateException("Document storage provider is missing");
        }
        DocumentStorage storage = providers.get(storageProvider);
        if (storage == null) {
            throw new IllegalStateException("Document storage provider is unavailable: " + storageProvider);
        }
        storage.validateConfiguration();
        return storage;
    }

    public record StoredDocument(EventDocumentStorageProvider provider, String storageKey) {
    }
}
