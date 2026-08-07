
package com.praveen.auditlog.retention;

import com.praveen.auditlog.integrity.CanonicalAuditEvent;
import com.praveen.auditlog.integrity.CanonicalEventSerializer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Coordinates a recoverable archive transition; ports provide durable boundaries. */
public final class RetentionService {
    public static final String CHECKSUM_ALGORITHM = "SHA-256";
    public static final int MANIFEST_VERSION = 1;
    public static final int BUNDLE_FORMAT_VERSION = 1;

    private final RetentionRepository repository;
    private final ArchiveStore store;
    private final ManifestSigner signer;
    private final CanonicalEventSerializer serializer;
    private final Clock clock;

    public RetentionService(RetentionRepository repository, ArchiveStore store,
                            ManifestSigner signer, CanonicalEventSerializer serializer,
                            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.store = Objects.requireNonNull(store);
        this.signer = Objects.requireNonNull(signer);
        this.serializer = Objects.requireNonNull(serializer);
        this.clock = Objects.requireNonNull(clock);
    }

    public ArchiveManifest archive(ArchiveRequest request) {
        ArchiveRange range = repository.selectClosedRange(request);
        validateRange(request, range);
        rejectLegalHold(request, range);

        ArchiveBundle bundle = new ArchiveBundle(BUNDLE_FORMAT_VERSION, range.events());
        byte[] checksum = checksum(bundle);
        UUID manifestId = UUID.randomUUID();
        repository.appendAction(manifestId, LifecycleAction.SELECTED,
                Instant.now(clock));
        String location = "archive/" + request.tenantId() + "/" + request.chainId()
                + "/" + range.startSequence() + "-" + range.endSequence()
                + "/" + manifestId;
        StoredObject stored = store.putIfAbsent(location, bundle);
        repository.appendAction(manifestId, LifecycleAction.STORED,
                Instant.now(clock));

        ArchiveBundle storedCopy = store.read(stored.location(), stored.version());
        if (!Arrays.equals(checksum, checksum(storedCopy))) {
            repository.appendAction(manifestId, LifecycleAction.STORED_COPY_INVALID,
                    Instant.now(clock));
            throw new ArchiveFailure(ArchiveFailureReason.BUNDLE_CHECKSUM_MISMATCH);
        }
        repository.appendAction(manifestId, LifecycleAction.VERIFIED,
                Instant.now(clock));

        Instant now = Instant.now(clock);
        ManifestDraft draft = new ManifestDraft(
                manifestId, request.tenantId(), request.chainId(),
                range.startSequence(), range.endSequence(), range.events().size(),
                range.events().get(0).event().previousHash(),
                range.events().get(0).contentHash(),
                range.events().get(range.events().size() - 1).contentHash(),
                checksum, request.policyId(), now, stored.location(), stored.version()
        );
        SignedValue signed = signer.sign(manifestBytes(draft));
        ArchiveManifest manifest = new ArchiveManifest(
                manifestId, MANIFEST_VERSION, request.tenantId(), request.chainId(),
                draft.startSequence(), draft.endSequence(), draft.recordCount(),
                draft.predecessorHash(), draft.firstEventHash(), draft.lastEventHash(),
                checksum, CHECKSUM_ALGORITHM, BUNDLE_FORMAT_VERSION,
                request.policyId(), now, stored.location(), stored.version(),
                signed.algorithm(), signed.keyId(), signed.version(), now, signed.signature()
        );

        // The port must commit manifest insertion, the second legal-hold check,
        // hot removal and lifecycle actions atomically.
        repository.publishAndRemoveHot(manifest, request);
        return manifest;
    }

    public ArchiveVerification verify(ArchiveManifest manifest,
                                      byte[] precedingActiveHash,
                                      ArchivedEvent followingActiveEvent) {
        final ArchiveBundle bundle;
        try {
            bundle = store.read(manifest.storageLocation(), manifest.storageVersion());
        } catch (RuntimeException missing) {
            return ArchiveVerification.failed(ArchiveFailureReason.ARCHIVE_OBJECT_MISSING,
                    manifest.startSequence());
        }
        if (!Arrays.equals(manifest.bundleChecksum(), checksum(bundle))) {
            return ArchiveVerification.failed(ArchiveFailureReason.BUNDLE_CHECKSUM_MISMATCH,
                    manifest.startSequence());
        }
        if (!signer.verify(manifestBytes(ManifestDraft.from(manifest)),
                manifest.signature(), manifest.signingKeyId(), manifest.signatureVersion())) {
            return ArchiveVerification.failed(ArchiveFailureReason.MANIFEST_SIGNATURE_INVALID,
                    manifest.startSequence());
        }
        List<ArchivedEvent> events = bundle.events();
        if (events.size() != manifest.recordCount()) {
            return ArchiveVerification.failed(ArchiveFailureReason.ARCHIVE_RANGE_INVALID,
                    manifest.startSequence());
        }
        byte[] expected = precedingActiveHash.clone();
        long sequence = manifest.startSequence();
        for (ArchivedEvent archived : events) {
            if (archived.event().sequenceNumber() != sequence
                    || !Arrays.equals(archived.event().previousHash(), expected)
                    || !Arrays.equals(archived.contentHash(), sha256(serializer.serialize(archived.event())))) {
                return ArchiveVerification.failed(ArchiveFailureReason.ARCHIVE_CHAIN_INVALID, sequence);
            }
            expected = archived.contentHash();
            sequence++;
        }
        if (!Arrays.equals(manifest.predecessorHash(), precedingActiveHash)
                || !Arrays.equals(manifest.firstEventHash(), events.get(0).contentHash())
                || !Arrays.equals(manifest.lastEventHash(), expected)) {
            return ArchiveVerification.failed(ArchiveFailureReason.ARCHIVE_BOUNDARY_MISMATCH,
                    manifest.startSequence());
        }
        if (followingActiveEvent != null
                && (followingActiveEvent.event().sequenceNumber() != manifest.endSequence() + 1
                || !Arrays.equals(followingActiveEvent.event().previousHash(), expected))) {
            return ArchiveVerification.failed(ArchiveFailureReason.ARCHIVE_BOUNDARY_MISMATCH,
                    manifest.endSequence() + 1);
        }
        return ArchiveVerification.valid(events.size(), manifest.endSequence(), expected);
    }

    public VerifiedArchive verifiedArchive(
            ArchiveManifest manifest,
            byte[] precedingActiveHash,
            ArchivedEvent followingActiveEvent
    ) {
        ArchiveVerification verification = verify(
                manifest, precedingActiveHash, followingActiveEvent
        );
        if (!verification.valid()) {
            throw new ArchiveProofException(
                    verification.reason(), verification.failureSequence()
            );
        }
        ArchiveBundle bundle;
        try {
            bundle = store.read(
                    manifest.storageLocation(), manifest.storageVersion()
            );
        } catch (RuntimeException missing) {
            throw new ArchiveProofException(
                    ArchiveFailureReason.ARCHIVE_OBJECT_MISSING,
                    manifest.startSequence()
            );
        }
        return new VerifiedArchive(manifest, bundle.events());
    }

    private void rejectLegalHold(ArchiveRequest request, ArchiveRange range) {
        if (repository.hasEffectiveLegalHold(request.tenantId(), request.chainId(),
                range.startSequence(), range.endSequence())) {
            throw new ArchiveFailure(ArchiveFailureReason.LEGAL_HOLD_ACTIVE);
        }
    }

    private void validateRange(ArchiveRequest request, ArchiveRange range) {
        if (!request.tenantId().equals(range.tenantId())
                || !request.chainId().equals(range.chainId()) || range.events().isEmpty()) {
            throw new ArchiveFailure(ArchiveFailureReason.PARTIAL_OR_INVALID_RANGE);
        }
        long expected = range.startSequence();
        for (ArchivedEvent event : range.events()) {
            if (event.event().sequenceNumber() != expected++) {
                throw new ArchiveFailure(ArchiveFailureReason.PARTIAL_OR_INVALID_RANGE);
            }
        }
        if (expected - 1 != range.endSequence()) {
            throw new ArchiveFailure(ArchiveFailureReason.PARTIAL_OR_INVALID_RANGE);
        }
    }

    private byte[] checksum(ArchiveBundle bundle) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(bundle.formatVersion());
            for (ArchivedEvent event : bundle.events()) {
                byte[] canonical = serializer.serialize(event.event());
                out.writeInt(canonical.length); out.write(canonical);
                out.write(event.contentHash());
            }
            return sha256(bytes.toByteArray());
        } catch (Exception error) {
            throw new IllegalStateException("Unable to create deterministic archive bundle", error);
        }
    }

    private static byte[] manifestBytes(ManifestDraft d) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MANIFEST_VERSION); write(out, d.manifestId().toString());
            write(out, d.tenantId()); write(out, d.chainId());
            out.writeLong(d.startSequence()); out.writeLong(d.endSequence()); out.writeLong(d.recordCount());
            out.write(d.predecessorHash()); out.write(d.firstEventHash()); out.write(d.lastEventHash());
            out.write(d.bundleChecksum()); write(out, CHECKSUM_ALGORITHM);
            out.writeInt(BUNDLE_FORMAT_VERSION); write(out, d.policyId());
            write(out, d.archivedAt().toString()); write(out, d.storageLocation()); write(out, d.storageVersion());
            return bytes.toByteArray();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static void write(DataOutputStream out, String value) throws Exception {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static byte[] sha256(byte[] value) {
        try { return MessageDigest.getInstance(CHECKSUM_ALGORITHM).digest(value); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    public record ArchiveRequest(String tenantId, String chainId, long startSequence,
                                 long endSequence, String policyId) {}
    public record ArchiveRange(String tenantId, String chainId, long startSequence,
                               long endSequence, List<ArchivedEvent> events) {
        public ArchiveRange { events = List.copyOf(events); }
    }
    public record ArchivedEvent(CanonicalAuditEvent event, byte[] contentHash) {
        public ArchivedEvent { contentHash = contentHash.clone(); }
        @Override public byte[] contentHash() { return contentHash.clone(); }
    }
    public record ArchiveBundle(int formatVersion, List<ArchivedEvent> events) {
        public ArchiveBundle { events = List.copyOf(events); }
    }
    public record StoredObject(String location, String version) {}
    public record SignedValue(String algorithm, String keyId, int version, byte[] signature) {
        public SignedValue { signature = signature.clone(); }
        @Override public byte[] signature() { return signature.clone(); }
    }
    public record ArchiveVerification(boolean valid, ArchiveFailureReason reason,
                                      Long failureSequence, long verifiedCount,
                                      long lastSequence, byte[] lastHash) {
        public ArchiveVerification { lastHash = lastHash.clone(); }
        @Override public byte[] lastHash() { return lastHash.clone(); }
        static ArchiveVerification valid(long count, long sequence, byte[] hash) {
            return new ArchiveVerification(true, null, null, count, sequence, hash);
        }
        static ArchiveVerification failed(ArchiveFailureReason reason, long sequence) {
            return new ArchiveVerification(false, reason, sequence, 0, 0, new byte[32]);
        }
    }
    public record VerifiedArchive(
            ArchiveManifest manifest,
            List<ArchivedEvent> events
    ) {
        public VerifiedArchive {
            events = List.copyOf(events);
        }
    }
    public enum ArchiveFailureReason {
        PARTIAL_OR_INVALID_RANGE, LEGAL_HOLD_ACTIVE, ARCHIVE_WRITE_FAILED,
        ARCHIVE_OBJECT_MISSING,
        BUNDLE_CHECKSUM_MISMATCH, MANIFEST_SIGNATURE_INVALID,
        ARCHIVE_RANGE_INVALID, ARCHIVE_CHAIN_INVALID, ARCHIVE_BOUNDARY_MISMATCH
    }
    public enum LifecycleAction { SELECTED, STORED, VERIFIED, MANIFEST_PUBLISHED,
        HOT_DATA_REMOVED, STORED_COPY_INVALID, FAILED }
    public static final class ArchiveFailure extends RuntimeException {
        private final ArchiveFailureReason reason;
        public ArchiveFailure(ArchiveFailureReason reason) { super(reason.name()); this.reason = reason; }
        public ArchiveFailureReason reason() { return reason; }
    }
    public interface RetentionRepository {
        ArchiveRange selectClosedRange(ArchiveRequest request);
        boolean hasEffectiveLegalHold(String tenantId, String chainId, long start, long end);
        void appendAction(UUID manifestId, LifecycleAction action, Instant at);
        void publishAndRemoveHot(ArchiveManifest manifest, ArchiveRequest request);
    }
    public interface ArchiveStore {
        StoredObject putIfAbsent(String location, ArchiveBundle bundle);
        ArchiveBundle read(String location, String version);
    }
    public interface ManifestSigner {
        SignedValue sign(byte[] canonicalManifest);
        boolean verify(byte[] canonicalManifest, byte[] signature, String keyId, int version);
    }
    private record ManifestDraft(UUID manifestId, String tenantId, String chainId,
                                 long startSequence, long endSequence, long recordCount,
                                 byte[] predecessorHash, byte[] firstEventHash, byte[] lastEventHash,
                                 byte[] bundleChecksum, String policyId, Instant archivedAt,
                                 String storageLocation, String storageVersion) {
        static ManifestDraft from(ArchiveManifest m) {
            return new ManifestDraft(m.manifestId(), m.tenantId(), m.chainId(), m.startSequence(),
                    m.endSequence(), m.recordCount(), m.predecessorHash(), m.firstEventHash(),
                    m.lastEventHash(), m.bundleChecksum(), m.policyId(), m.archivedAt(),
                    m.storageLocation(), m.storageVersion());
        }
    }
}

