package com.praveen.auditlog.integrity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HashChainTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Sha256AuditEventHasher hasher = new Sha256AuditEventHasher(
            new CanonicalJsonAuditEventCanonicalizerV1());

    @Test void eachEventLinksToTheExactPreviousDigest() throws Exception {
        CanonicalAuditEvent first = event(1, new byte[32], "opened");
        byte[] firstHash = hasher.digest(first);
        CanonicalAuditEvent second = event(2, firstHash, "approved");

        assertThat(second.previousHash()).isEqualTo(firstHash);
        assertThat(hasher.digest(first)).isEqualTo(firstHash);
        assertThat(hasher.digest(second)).hasSize(32).isNotEqualTo(firstHash);
    }

    @Test void changingHistoryBreaksTheExistingSuccessorLink() throws Exception {
        CanonicalAuditEvent original = event(1, new byte[32], "opened");
        byte[] originalHash = hasher.digest(original);
        CanonicalAuditEvent successor = event(2, originalHash, "approved");
        CanonicalAuditEvent changed = event(1, new byte[32], "changed");

        assertThat(hasher.digest(changed)).isNotEqualTo(originalHash);
        assertThat(successor.previousHash()).isNotEqualTo(hasher.digest(changed));
    }

    @Test void digestIsDeterministicAcrossRepeatedCalls() throws Exception {
        CanonicalAuditEvent event = event(1, new byte[32], "stable");
        assertThat(hasher.digestHex(event)).isEqualTo(hasher.digestHex(event));
    }

    private CanonicalAuditEvent event(long sequence, byte[] previous, String state) throws Exception {
        Instant time = Instant.parse("2026-08-07T12:00:00.123456Z").plusSeconds(sequence);
        return new CanonicalAuditEvent(new UUID(0, sequence), "tenant-1", "tenant:tenant-1",
                sequence, "ACCOUNT_UPDATED", 1, "producer-1", "actor-1", "USER",
                "JWT", "ACCOUNT", "account-1", time, time,
                json.readTree("{\"state\":\"" + state + "\"}"), previous, "SHA-256", 1);
    }
}
