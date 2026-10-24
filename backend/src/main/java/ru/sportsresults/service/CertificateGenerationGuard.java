package ru.sportsresults.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class CertificateGenerationGuard {
    private final Semaphore permits;
    private final long timeoutMs;

    public CertificateGenerationGuard(
            @Value("${app.certificates.max-concurrent-generations:2}") int maximum,
            @Value("${app.certificates.acquire-timeout-ms:250}") long timeoutMs
    ) {
        if (maximum < 1 || timeoutMs < 0) throw new IllegalArgumentException("Invalid certificate concurrency settings");
        this.permits = new Semaphore(maximum, true);
        this.timeoutMs = timeoutMs;
    }

    public <T> T execute(CheckedSupplier<T> operation) {
        boolean acquired = false;
        try {
            acquired = permits.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            if (!acquired) throw new CertificateCapacityException();
            return operation.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CertificateCapacityException();
        } catch (CertificateCapacityException exception) {
            throw exception;
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Certificate generation failed", exception);
        } finally {
            if (acquired) permits.release();
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> { T get() throws Exception; }
}
