package ru.sportsresults.storage;

import java.io.InputStream;

public interface FileStorageService {
    String store(byte[] content);
    InputStream open(String storageKey);
    void delete(String storageKey);
    boolean exists(String storageKey);
}
