package com.praveen.auditlog.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.auditlog.integrity.CanonicalAuditEvent;
import com.praveen.auditlog.integrity.CanonicalEventSerializer;
import com.praveen.auditlog.integrity.CanonicalJsonAuditEventCanonicalizerV1;
import com.praveen.auditlog.integrity.HashService;
import com.praveen.auditlog.persistence.ChainVerificationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CanonicalEventSerializer serializer =
            new CanonicalEventSerializer(
                    new CanonicalJsonAuditEventCanonicalizerV1()
            );
    private final HashService hashes = new HashService(serializer);

    @Test
    void productionHasherMatchesGoldenVectorWithoutSpring() throws Exception {
        CanonicalAuditEvent event = event(
                1,
                new byte[32],
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Instant.parse("2026-08-07T14:30:12.456789Z")
        );

        assertThat(hashes.hex(hashes.hash(event))).isEqualTo(
                "64a3bbd50aa3b778dd9abfbebde2176a858ff952b689814e6181f20279cba1eb"
        );
    }

    @Test
    void verificationAlgorithmRunsWithoutSpring() throws Exception {
        CanonicalAuditEvent first = event(
                1, new byte[32],
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Instant.parse("2026-08-07T14:30:12.456789Z")
        );
        byte[] firstHash = hashes.hash(first);
        CanonicalAuditEvent second = event(
                2, firstHash,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                Instant.parse("2026-08-07T14:30:13.456789Z")
        );
        byte[] secondHash = hashes.hash(second);

        ChainVerificationService.VerificationState state =
                new ChainVerificationService.VerificationState(
                        hashes, serializer.version()
                );
        assertThat(state.accept(stored(first, firstHash))).isTrue();
        assertThat(state.accept(stored(second, secondHash))).isTrue();

        VerificationResult result = state.finish(
                new ChainVerificationRepository.ChainBoundary(
                        true, 2, secondHash
                )
        );
        assertThat(result.status()).isEqualTo(
                VerificationResult.Status.VALID
        );
        assertThat(result.verifiedCount()).isEqualTo(2);
    }

    @Test
    void verificationStopsAtFirstChangedHashWithoutSpring() throws Exception {
        CanonicalAuditEvent event = event(
                1, new byte[32],
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Instant.parse("2026-08-07T14:30:12.456789Z")
        );
        byte[] changedHash = hashes.hash(event);
        changedHash[0] ^= 1;

        ChainVerificationService.VerificationState state =
                new ChainVerificationService.VerificationState(
                        hashes, serializer.version()
                );
        assertThat(state.accept(stored(event, changedHash))).isFalse();

        VerificationResult result = state.finish(
                new ChainVerificationRepository.ChainBoundary(
                        true, 1, changedHash
                )
        );
        assertThat(result.failureReason()).isEqualTo(
                VerificationResult.FailureReason.CONTENT_HASH_MISMATCH
        );
        assertThat(result.failureSequence()).isEqualTo(1);
    }

    private ChainVerificationRepository.StoredEvent stored(
            CanonicalAuditEvent event,
            byte[] contentHash
    ) {
        return new ChainVerificationRepository.StoredEvent(event, contentHash);
    }

    private CanonicalAuditEvent event(
            long sequence,
            byte[] previousHash,
            UUID eventId,
            Instant recordedAt
    ) throws Exception {
        return new CanonicalAuditEvent(
                eventId,
                "tenant-1",
                "tenant:tenant-1",
                sequence,
                "ACCOUNT_UPDATED",
                1,
                "producer-1",
                "actor-1",
                "USER",
                "PRODUCER_ASSERTED",
                "ACCOUNT",
                "account-1",
                Instant.parse("2026-08-07T14:30:12.123000Z"),
                recordedAt,
                objectMapper.readTree("{\"result\":\"accepted\"}"),
                previousHash,
                HashService.ALGORITHM,
                serializer.version()
        );
    }
}
