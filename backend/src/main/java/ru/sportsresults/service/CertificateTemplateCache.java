package ru.sportsresults.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class CertificateTemplateCache {
    private final int maxEntries;
    private final long maxBytes;
    private final Map<Long, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long currentBytes;

    public CertificateTemplateCache(
            @Value("${app.certificates.cache-max-entries:8}") int maxEntries,
            @Value("${app.certificates.cache-max-bytes:52428800}") long maxBytes
    ) {
        if (maxEntries < 1 || maxBytes < 1) throw new IllegalArgumentException("Invalid certificate cache settings");
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
    }

    public synchronized byte[] get(Long templateId, String sha256, Supplier<byte[]> loader) {
        Entry cached = entries.get(templateId);
        if (cached != null && cached.sha256.equals(sha256)) return cached.bytes;
        if (cached != null) remove(templateId);
        byte[] loaded = loader.get();
        if (loaded.length <= maxBytes) {
            entries.put(templateId, new Entry(sha256, loaded));
            currentBytes += loaded.length;
            trim();
        }
        return loaded;
    }

    public synchronized void invalidate(Long templateId) { remove(templateId); }

    private void trim() {
        while (entries.size() > maxEntries || currentBytes > maxBytes) {
            Long eldest = entries.keySet().iterator().next();
            remove(eldest);
        }
    }

    private void remove(Long templateId) {
        Entry removed = entries.remove(templateId);
        if (removed != null) currentBytes -= removed.bytes.length;
    }

    private record Entry(String sha256, byte[] bytes) { }
}
