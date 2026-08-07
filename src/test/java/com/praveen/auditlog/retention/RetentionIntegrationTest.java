
package com.praveen.auditlog.retention;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.auditlog.integrity.CanonicalAuditEvent;
import com.praveen.auditlog.integrity.CanonicalEventSerializer;
import com.praveen.auditlog.integrity.CanonicalJsonAuditEventCanonicalizerV1;
import com.praveen.auditlog.integrity.Sha256AuditEventHasher;
import com.praveen.auditlog.retention.RetentionService.*;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetentionIntegrationTest {
    private static final byte[] GENESIS = new byte[32];
    private final ObjectMapper json = new ObjectMapper();
    private final CanonicalJsonAuditEventCanonicalizerV1 canonicalizer =
            new CanonicalJsonAuditEventCanonicalizerV1();
    private final CanonicalEventSerializer serializer = new CanonicalEventSerializer(canonicalizer);
    private final Sha256AuditEventHasher hasher = new Sha256AuditEventHasher(canonicalizer);

    @Test
    void archiveSmallRangeAndCrossItWithoutFalseSequenceGap() throws Exception {
        List<ArchivedEvent> chain = chain(3);
        InMemoryRepository repository = new InMemoryRepository(chain);
        InMemoryStore store = new InMemoryStore();
        RetentionService service = service(repository, store);

        ArchiveManifest manifest = service.archive(
                new ArchiveRequest("tenant-1", "tenant:tenant-1", 1, 2, "policy-7y")
        );

        assertThat(repository.hot).extracting(e -> e.event().sequenceNumber())
                .containsExactly(3L);
        ArchiveVerification verification = service.verify(
                manifest, GENESIS, chain.get(2)
        );
        assertThat(verification.valid()).isTrue();
        assertThat(verification.verifiedCount()).isEqualTo(2);
        assertThat(verification.lastSequence()).isEqualTo(2);
        assertThat(verification.lastHash()).isEqualTo(chain.get(1).contentHash());
    }

    @Test
    void corruptedStoredBundleFailsWithArchiveSpecificReason() throws Exception {
        List<ArchivedEvent> chain = chain(3);
        InMemoryRepository repository = new InMemoryRepository(chain);
        InMemoryStore store = new InMemoryStore();
        RetentionService service = service(repository, store);
        ArchiveManifest manifest = service.archive(
                new ArchiveRequest("tenant-1", "tenant:tenant-1", 1, 2, "policy-7y")
        );

        store.corrupt(manifest.storageLocation(), manifest.storageVersion(), chain.get(2));

        ArchiveVerification verification = service.verify(manifest, GENESIS, chain.get(2));
        assertThat(verification.valid()).isFalse();
        assertThat(verification.reason())
                .isEqualTo(ArchiveFailureReason.BUNDLE_CHECKSUM_MISMATCH);
    }

    @Test
    void legalHoldOverridesRetentionAndLeavesHotDataUntouched() throws Exception {
        List<ArchivedEvent> chain = chain(2);
        InMemoryRepository repository = new InMemoryRepository(chain);
        repository.legalHold = true;
        RetentionService service = service(repository, new InMemoryStore());

        assertThatThrownBy(() -> service.archive(
                new ArchiveRequest("tenant-1", "tenant:tenant-1", 1, 2, "expired-policy")
        )).isInstanceOf(ArchiveFailure.class)
                .extracting(error -> ((ArchiveFailure) error).reason())
                .isEqualTo(ArchiveFailureReason.LEGAL_HOLD_ACTIVE);
        assertThat(repository.hot).hasSize(2);
    }

    private RetentionService service(InMemoryRepository repository, InMemoryStore store) {
        return new RetentionService(repository, store, new HmacSigner(), serializer,
                Clock.fixed(Instant.parse("2026-08-07T18:30:00Z"), ZoneOffset.UTC));
    }

    private List<ArchivedEvent> chain(int count) throws Exception {
        List<ArchivedEvent> result = new ArrayList<>();
        byte[] previous = GENESIS;
        for (int sequence = 1; sequence <= count; sequence++) {
            CanonicalAuditEvent event = new CanonicalAuditEvent(
                    new UUID(0, sequence), "tenant-1", "tenant:tenant-1", sequence,
                    "ACCOUNT_UPDATED", 1, "producer-1", "actor-1", "USER",
                    "VERIFIED_JWT", "ACCOUNT", "account-1",
                    Instant.parse("2026-08-07T18:00:00Z").plusSeconds(sequence),
                    Instant.parse("2026-08-07T18:01:00Z").plusSeconds(sequence),
                    json.readTree("{\"sequence\":" + sequence + "}"), previous,
                    "SHA-256", 1
            );
            byte[] hash = hasher.digest(event);
            result.add(new ArchivedEvent(event, hash));
            previous = hash;
        }
        return result;
    }

    private static final class InMemoryRepository implements RetentionRepository {
        private final List<ArchivedEvent> hot;
        private boolean legalHold;
        private ArchiveManifest manifest;
        private InMemoryRepository(List<ArchivedEvent> events) { hot = new ArrayList<>(events); }
        @Override public ArchiveRange selectClosedRange(ArchiveRequest request) {
            List<ArchivedEvent> selected = hot.stream()
                    .filter(e -> e.event().sequenceNumber() >= request.startSequence()
                            && e.event().sequenceNumber() <= request.endSequence()).toList();
            return new ArchiveRange(request.tenantId(), request.chainId(),
                    request.startSequence(), request.endSequence(), selected);
        }
        @Override public boolean hasEffectiveLegalHold(String tenant, String chain, long start, long end) {
            return legalHold;
        }
        @Override public void appendAction(UUID id, LifecycleAction action, Instant at) { }
        @Override public void publishAndRemoveHot(ArchiveManifest value, ArchiveRequest request) {
            if (legalHold) throw new ArchiveFailure(ArchiveFailureReason.LEGAL_HOLD_ACTIVE);
            manifest = value;
            hot.removeIf(e -> e.event().sequenceNumber() >= request.startSequence()
                    && e.event().sequenceNumber() <= request.endSequence());
        }
    }

    private static final class InMemoryStore implements ArchiveStore {
        private final Map<String, ArchiveBundle> values = new HashMap<>();
        @Override public StoredObject putIfAbsent(String location, ArchiveBundle bundle) {
            values.putIfAbsent(location + "@1", bundle);
            return new StoredObject(location, "1");
        }
        @Override public ArchiveBundle read(String location, String version) {
            ArchiveBundle value = values.get(location + "@" + version);
            if (value == null) throw new IllegalStateException("missing archive object");
            return value;
        }
        void corrupt(String location, String version, ArchivedEvent extra) {
            ArchiveBundle current = read(location, version);
            List<ArchivedEvent> changed = new ArrayList<>(current.events());
            changed.add(extra);
            values.put(location + "@" + version, new ArchiveBundle(current.formatVersion(), changed));
        }
    }

    private static final class HmacSigner implements ManifestSigner {
        private static final byte[] KEY = "test-key-not-for-production".getBytes();
        @Override public SignedValue sign(byte[] value) {
            return new SignedValue("HMAC-SHA256", "test-key-1", 1, mac(value));
        }
        @Override public boolean verify(byte[] value, byte[] signature, String keyId, int version) {
            return "test-key-1".equals(keyId) && version == 1
                    && java.security.MessageDigest.isEqual(signature, mac(value));
        }
        private byte[] mac(byte[] value) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
                return mac.doFinal(value);
            } catch (Exception error) { throw new IllegalStateException(error); }
        }
    }
}

