package ru.sportsresults.storage.attachments;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record PreparedObjectUpload(
        URI url,
        String method,
        Map<String, String> requiredHeaders,
        Instant expiresAt
) {
    public PreparedObjectUpload {
        requiredHeaders = Map.copyOf(requiredHeaders);
    }
}
