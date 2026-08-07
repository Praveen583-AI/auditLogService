package com.praveen.auditlog.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalMetricsTest {
    @Test
    void exposesSuccessFailureReplayRetryAndVerificationWithoutSensitiveLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetrics metrics = new OperationalMetrics(registry);

        metrics.write(metrics.start(), "SUCCESS", true);
        metrics.write(metrics.start(), "DATABASE_FAILURE", false);
        metrics.lockWait(metrics.start(), "TIMEOUT");
        metrics.retry("CHAIN_LOCK_TIMEOUT");
        metrics.verification(metrics.start(), VerificationResult.invalid(
                VerificationResult.FailureReason.CONTENT_HASH_MISMATCH,
                3, 2, 1L, 2L, "invalid"));

        assertThat(registry.get("audit.write.total").tag("outcome", "SUCCESS")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("audit.idempotent.replay.total").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("audit.chain.lock.retry.total").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("audit.verification.total")
                .tag("reason", "CONTENT_HASH_MISMATCH").counter().count())
                .isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).allSatisfy(tag ->
                        assertThat(tag.getKey()).isNotIn(
                                "payload", "actorId", "eventId", "chainId", "correlationId")));
    }
}
