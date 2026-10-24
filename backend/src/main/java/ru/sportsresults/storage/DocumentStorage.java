package ru.sportsresults.storage;

import ru.sportsresults.domain.EventDocumentStorageProvider;

import java.io.InputStream;

public interface DocumentStorage {
    EventDocumentStorageProvider provider();
    String store(Long eventId, byte[] content);
    default String storeCertificateTemplate(Long eventId, byte[] content) {
        return store(eventId, content);
    }
    InputStream open(String storageKey);
    void delete(String storageKey);
    boolean exists(String storageKey);

    default void validateConfiguration() {
    }
}
