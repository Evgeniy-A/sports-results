package ru.sportsresults.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.sportsresults.domain.EventDocumentStorageProvider;
import ru.sportsresults.service.InvalidRequestException;
import ru.sportsresults.service.ResourceNotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class LocalFileStorageService implements DocumentStorage {
    private final Path root;

    public LocalFileStorageService(@Value("${app.documents.storage-root:./data/uploads/events}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot initialize document storage", exception);
        }
    }

    @Override
    public EventDocumentStorageProvider provider() {
        return EventDocumentStorageProvider.LOCAL;
    }

    @Override
    public String store(Long eventId, byte[] content) {
        String key = UUID.randomUUID() + ".pdf";
        Path target = resolve(key);
        try {
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            return key;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot store document", exception);
        }
    }

    @Override
    public String storeCertificateTemplate(Long eventId, byte[] content) {
        String key = "events/" + eventId + "/certificates/" + UUID.randomUUID() + ".pdf";
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            return key;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot store certificate template", exception);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        try {
            return Files.newInputStream(resolve(storageKey), StandardOpenOption.READ);
        } catch (java.nio.file.NoSuchFileException exception) {
            throw new ResourceNotFoundException("DOCUMENT_CONTENT_NOT_FOUND", "Document content not found");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read document", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot delete document", exception);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolve(storageKey));
    }

    private Path resolve(String storageKey) {
        String safeKey = DocumentStorageKey.requireSafeRelative(storageKey);
        Path resolved = root.resolve(safeKey).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new InvalidRequestException("INVALID_STORAGE_KEY", "Invalid document storage key");
        }
        return resolved;
    }
}
