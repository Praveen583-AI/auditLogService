package com.praveen.auditlog.application;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;

@Component
public class AppendRetryPolicy {

    private static final int MAX_ATTEMPTS = 3;

    public int maxAttempts() {
        return MAX_ATTEMPTS;
    }

    public void backoff(int failedAttempt) {
        long baseMillis = failedAttempt == 1 ? 10 : 25;
        long jitterMillis = ThreadLocalRandom.current().nextLong(0, 16);
        LockSupport.parkNanos(
                java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(
                        baseMillis + jitterMillis
                )
        );
        if (Thread.currentThread().isInterrupted()) {
            throw new ChainBusyException(
                    new InterruptedException("Append retry interrupted")
            );
        }
    }
}
