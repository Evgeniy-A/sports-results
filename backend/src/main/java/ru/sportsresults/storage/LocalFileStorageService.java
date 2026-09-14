package ru.sportsresults.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.sportsresults.service.InvalidRequestException;
import ru.sportsresults.service.ResourceNotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {
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
    public String store(byte[] content) {
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
        if (storageKey == null || storageKey.isBlank() || Path.of(storageKey).isAbsolute()) {
            throw new InvalidRequestException("INVALID_STORAGE_KEY", "Invalid document storage key");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new InvalidRequestException("INVALID_STORAGE_KEY", "Invalid document storage key");
        }
        return resolved;
    }
}
