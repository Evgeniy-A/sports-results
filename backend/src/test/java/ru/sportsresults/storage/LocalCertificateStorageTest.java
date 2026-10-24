package ru.sportsresults.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCertificateStorageTest {
    @TempDir Path directory;

    @Test
    void storesAndDeletesCertificateTemplateInDedicatedNamespace() throws Exception {
        LocalFileStorageService storage = new LocalFileStorageService(directory.toString());
        byte[] pdf = "%PDF-1.4\nsynthetic".getBytes(StandardCharsets.US_ASCII);
        String key = storage.storeCertificateTemplate(42L, pdf);
        assertThat(key).matches("events/42/certificates/[0-9a-f-]{36}\\.pdf");
        assertThat(storage.open(key).readAllBytes()).isEqualTo(pdf);
        assertThat(storage.exists(key)).isTrue();
        storage.delete(key);
        assertThat(Files.exists(directory.resolve(key))).isFalse();
    }
}
