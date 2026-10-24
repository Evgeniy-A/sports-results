package ru.sportsresults.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificateGenerationGuardTest {
    @Test
    void rejectsWhenConfiguredCapacityIsExhausted() throws Exception {
        CertificateGenerationGuard guard = new CertificateGenerationGuard(1, 10);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> guard.execute(() -> { entered.countDown(); release.await(); return true; }));
            entered.await();
            assertThatThrownBy(() -> guard.execute(() -> true)).isInstanceOf(CertificateCapacityException.class);
            release.countDown(); first.get();
        }
    }
}
