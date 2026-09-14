package ru.sportsresults.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public record ResultIssueJournalExportArtifact(
        Path path,
        String filename,
        long sizeBytes,
        long issueCount,
        long attachmentCount,
        long grantCount,
        UUID shareBatchId,
        Instant shareExpiresAt
) {
    public void delete() throws IOException {
        Files.deleteIfExists(path);
    }
}
