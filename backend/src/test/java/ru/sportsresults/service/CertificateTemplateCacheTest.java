package ru.sportsresults.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateTemplateCacheTest {
    @Test
    void isBoundedAndInvalidatesByTemplateAndHash() {
        CertificateTemplateCache cache = new CertificateTemplateCache(2, 6);
        AtomicInteger loads = new AtomicInteger();
        assertThat(cache.get(1L, "a", () -> bytes(loads, 1))).containsExactly(1);
        assertThat(cache.get(1L, "a", () -> bytes(loads, 9))).containsExactly(1);
        assertThat(loads).hasValue(1);
        cache.get(2L, "b", () -> bytes(loads, 2, 2));
        cache.get(3L, "c", () -> bytes(loads, 3, 3, 3));
        assertThat(cache.get(1L, "a", () -> bytes(loads, 4))).containsExactly(4);
        cache.invalidate(1L);
        assertThat(cache.get(1L, "a", () -> bytes(loads, 5))).containsExactly(5);
    }

    private static byte[] bytes(AtomicInteger loads, int... values) {
        loads.incrementAndGet(); byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }
}
