package com.praveen.auditlog.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public final class OperationalMetrics {
    private final MeterRegistry registry;

    public OperationalMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public long start() { return System.nanoTime(); }

    public void write(long started, String outcome, boolean replayed) {
        Timer.builder("audit.write.duration").tag("outcome", outcome)
                .tag("replayed", Boolean.toString(replayed)).register(registry)
                .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        Counter.builder("audit.write.total").tag("outcome", outcome)
                .register(registry).increment();
        if (replayed) Counter.builder("audit.idempotent.replay.total")
                .tag("outcome", outcome).register(registry).increment();
    }

    public void lockWait(long started, String outcome) {
        Timer.builder("audit.chain.lock.wait").tag("outcome", outcome)
                .register(registry).record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
    }

    public void retry(String reason) {
        Counter.builder("audit.chain.lock.retry.total").tag("reason", reason)
                .register(registry).increment();
    }

    public void verification(long started, VerificationResult result) {
        String status = result.status().name();
        Timer.builder("audit.verification.duration").tag("status", status)
                .register(registry).record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        Counter.builder("audit.verification.total").tag("status", status)
                .tag("reason", result.failureReason() == null ? "NONE" : result.failureReason().name())
                .register(registry).increment();
    }
}
