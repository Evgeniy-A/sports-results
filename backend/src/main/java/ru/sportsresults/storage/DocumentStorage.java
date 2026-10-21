package ru.sportsresults.storage;

import ru.sportsresults.domain.EventDocumentStorageProvider;

import java.io.InputStream;

public interface DocumentStorage {
    EventDocumentStorageProvider provider();
    String store(Long eventId, byte[] content);
    InputStream open(String storageKey);
    void delete(String storageKey);
    boolean exists(String storageKey);

    default void validateConfiguration() {
    }
}
