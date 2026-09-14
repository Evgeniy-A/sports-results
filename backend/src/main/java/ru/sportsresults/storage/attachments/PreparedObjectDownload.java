package ru.sportsresults.storage.attachments;

import java.net.URI;
import java.time.Instant;

public record PreparedObjectDownload(URI url, Instant expiresAt) {
}
