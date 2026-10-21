package ru.sportsresults.storage;

import ru.sportsresults.service.InvalidRequestException;

import java.util.regex.Pattern;

final class DocumentStorageKey {
    private static final Pattern WINDOWS_DRIVE = Pattern.compile("^[A-Za-z]:.*");

    private DocumentStorageKey() {
    }

    static String requireSafeRelative(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw invalid();
        }
        String normalized = storageKey.strip();
        if (normalized.startsWith("/") || normalized.endsWith("/") || normalized.contains("\\")
                || WINDOWS_DRIVE.matcher(normalized).matches() || normalized.contains(":")) {
            throw invalid();
        }
        for (String segment : normalized.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw invalid();
            }
        }
        return normalized;
    }

    private static InvalidRequestException invalid() {
        return new InvalidRequestException("INVALID_STORAGE_KEY", "Invalid document storage key");
    }
}
