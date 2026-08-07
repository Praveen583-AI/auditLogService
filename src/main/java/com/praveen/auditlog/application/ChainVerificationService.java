package com.praveen.auditlog.application;

import com.praveen.auditlog.integrity.CanonicalizationException;
import com.praveen.auditlog.integrity.CanonicalEventSerializer;
import com.praveen.auditlog.integrity.HashService;
import com.praveen.auditlog.persistence.ChainVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
public class ChainVerificationService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ChainVerificationService.class);
    private static final byte[] GENESIS_HASH = new byte[32];

    private final AuditRequestContextProvider contextProvider;
    private final ChainVerificationRepository repository;
    private final HashService hashes;
    private final CanonicalEventSerializer serializer;

    public ChainVerificationService(
            AuditRequestContextProvider contextProvider,
            ChainVerificationRepository repository,
            HashService hashes,
            CanonicalEventSerializer serializer
    ) {
        this.contextProvider = contextProvider;
        this.repository = repository;
        this.hashes = hashes;
        this.serializer = serializer;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public VerificationResult verify(String chainId) {
        if (chainId == null || chainId.isBlank()) {
            throw new IllegalArgumentException("chainId is required");
        }

        AuditRequestContext context = contextProvider.currentContext();
        VerificationState state = new VerificationState(
                hashes, serializer.version()
        );
        ChainVerificationRepository.ChainBoundary boundary = repository.scan(
                context.tenantId(), chainId, state::accept
        );

        if (!boundary.exists()) {
            throw new ChainNotFoundException();
        }

        VerificationResult result = state.finish(boundary);
        if (result.status() == VerificationResult.Status.VALID) {
            LOGGER.info(
                    "chain_verification_completed chainId={} status={} verifiedCount={}",
                    chainId, result.status(), result.verifiedCount()
            );
        } else {
            LOGGER.warn(
                    "chain_verification_completed chainId={} status={} failureReason={} failureSequence={} verifiedCount={}",
                    chainId, result.status(), result.failureReason(),
                    result.failureSequence(), result.verifiedCount()
            );
        }
        return result;
    }

    /**
     * Framework-independent, constant-memory chain algorithm. It deliberately
     * retains only the expected sequence/hash and the first failure.
     */
    static final class VerificationState {

        private final HashService hashes;
        private final int canonicalizationVersion;
        private long expectedSequence = 1;
        private byte[] expectedPreviousHash = GENESIS_HASH.clone();
        private long verifiedCount;
        private Long firstSequence;
        private Long lastVerifiedSequence;
        private byte[] lastVerifiedHash;
        private VerificationResult failure;

        VerificationState(
                HashService hashes,
                int canonicalizationVersion
        ) {
            this.hashes = hashes;
            this.canonicalizationVersion = canonicalizationVersion;
        }

        boolean accept(ChainVerificationRepository.StoredEvent stored) {
            if (failure != null) {
                return false;
            }

            var event = stored.event();
            long sequence = event.sequenceNumber();
            if (event.canonicalizationVersion() != canonicalizationVersion) {
                failure = VerificationResult.indeterminate(
                        VerificationResult.FailureReason
                                .UNSUPPORTED_CANONICALIZATION_VERSION,
                        sequence, verifiedCount, firstSequence,
                        lastVerifiedSequence,
                        "The event canonicalization version is not supported."
                );
                return false;
            }
            if (!HashService.ALGORITHM.equals(event.hashAlgorithm())) {
                failure = VerificationResult.indeterminate(
                        VerificationResult.FailureReason
                                .UNSUPPORTED_HASH_ALGORITHM,
                        sequence, verifiedCount, firstSequence,
                        lastVerifiedSequence,
                        "The event hash algorithm is not supported."
                );
                return false;
            }
            if (verifiedCount == 0 && sequence != 1) {
                failure = VerificationResult.invalid(
                        VerificationResult.FailureReason
                                .UNEXPECTED_FIRST_SEQUENCE,
                        sequence, 0, sequence, null,
                        "The chain does not begin at sequence 1."
                );
                return false;
            }
            if (sequence != expectedSequence) {
                failure = VerificationResult.invalid(
                        VerificationResult.FailureReason.SEQUENCE_GAP,
                        sequence, verifiedCount, firstSequence,
                        lastVerifiedSequence,
                        "The next expected sequence was not present."
                );
                return false;
            }
            if (!Arrays.equals(event.previousHash(), expectedPreviousHash)) {
                failure = VerificationResult.invalid(
                        VerificationResult.FailureReason
                                .PREVIOUS_HASH_MISMATCH,
                        sequence, verifiedCount, firstSequence,
                        lastVerifiedSequence,
                        "The event does not link to the preceding verified event."
                );
                return false;
            }

            final byte[] calculated;
            try {
                calculated = hashes.hash(event);
            } catch (CanonicalizationException error) {
                failure = VerificationResult.indeterminate(
                        VerificationResult.FailureReason
                                .UNSUPPORTED_CANONICALIZATION_VERSION,
                        sequence, verifiedCount, firstSequence,
                        lastVerifiedSequence,
                        "The event cannot be canonicalized by this verifier."
                );
                return false;
            }
            if (!Arrays.equals(calculated, stored.contentHash())) {
                failure = VerificationResult.invalid(
                        VerificationResult.FailureReason.CONTENT_HASH_MISMATCH,
                        sequence, verifiedCount, firstSequence,
                        lastVerifiedSequence,
                        "The stored content hash does not match the recalculated hash."
                );
                return false;
            }

            if (firstSequence == null) {
                firstSequence = sequence;
            }
            verifiedCount++;
            lastVerifiedSequence = sequence;
            lastVerifiedHash = calculated;
            expectedSequence = sequence + 1;
            expectedPreviousHash = calculated;
            return true;
        }

        VerificationResult finish(
                ChainVerificationRepository.ChainBoundary boundary
        ) {
            if (failure != null) {
                return failure;
            }
            if (verifiedCount == 0 && boundary.latestSequence() > 0) {
                return VerificationResult.invalid(
                        VerificationResult.FailureReason
                                .UNEXPECTED_FIRST_SEQUENCE,
                        1, 0, null, null,
                        "The chain head references events that are not present."
                );
            }
            if (verifiedCount != boundary.latestSequence()) {
                return VerificationResult.invalid(
                        VerificationResult.FailureReason.SEQUENCE_GAP,
                        expectedSequence, verifiedCount, firstSequence,
                        lastVerifiedSequence,
                        "The chain ends before its recorded head."
                );
            }
            if (verifiedCount > 0
                    && !Arrays.equals(lastVerifiedHash, boundary.latestHash())) {
                return VerificationResult.invalid(
                        VerificationResult.FailureReason.CHAIN_HEAD_MISMATCH,
                        lastVerifiedSequence, verifiedCount, firstSequence,
                        lastVerifiedSequence,
                        "The final event does not match the recorded chain head."
                );
            }
            return VerificationResult.valid(
                    verifiedCount, firstSequence, lastVerifiedSequence
            );
        }
    }
}
