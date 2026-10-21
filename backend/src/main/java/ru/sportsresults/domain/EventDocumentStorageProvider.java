package ru.sportsresults.domain;

import java.util.Locale;

public enum EventDocumentStorageProvider {
    LOCAL,
    YANDEX_DISK;

    public static EventDocumentStorageProvider fromConfiguration(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Document storage provider must not be blank");
        }
        try {
            return valueOf(value.strip().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unsupported document storage provider: " + value, exception);
        }
    }
}
