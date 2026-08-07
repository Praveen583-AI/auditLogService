
package com.praveen.auditlog.application;

import java.util.Objects;

public record VerificationResult(
        Status status,
        Boolean valid,
        FailureReason failureReason,
        Long failureSequence,
        long verifiedCount,
        Long firstSequence,
        Long lastVerifiedSequence,
        String message
) {
    public VerificationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
        if (status == Status.VALID && !Boolean.TRUE.equals(valid)) {
            throw new IllegalArgumentException("VALID result must have valid=true");
        }
        if (status == Status.INVALID && !Boolean.FALSE.equals(valid)) {
            throw new IllegalArgumentException("INVALID result must have valid=false");
        }
        if (status == Status.INDETERMINATE && valid != null) {
            throw new IllegalArgumentException(
                    "INDETERMINATE result must have valid=null"
            );
        }
        if (status != Status.VALID && failureReason == null) {
            throw new IllegalArgumentException(
                    "Non-valid result requires a failure reason"
            );
        }
    }

    public static VerificationResult valid(
            long verifiedCount,
            Long firstSequence,
            Long lastSequence
    ) {
        return new VerificationResult(
                Status.VALID, true, null, null, verifiedCount,
                firstSequence, lastSequence,
                "The audit chain is valid."
        );
    }

    public static VerificationResult invalid(
            FailureReason reason,
            long failureSequence,
            long verifiedCount,
            Long firstSequence,
            Long lastSequence,
            String message
    ) {
        return new VerificationResult(
                Status.INVALID, false, reason, failureSequence, verifiedCount,
                firstSequence, lastSequence, message
        );
    }

    public static VerificationResult indeterminate(
            FailureReason reason,
            long failureSequence,
            long verifiedCount,
            Long firstSequence,
            Long lastSequence,
            String message
    ) {
        return new VerificationResult(
                Status.INDETERMINATE, null, reason, failureSequence,
                verifiedCount, firstSequence, lastSequence, message
        );
    }

    public enum Status {
        VALID,
        INVALID,
        INDETERMINATE
    }

    public enum FailureReason {
        UNEXPECTED_FIRST_SEQUENCE,
        SEQUENCE_GAP,
        PREVIOUS_HASH_MISMATCH,
        CONTENT_HASH_MISMATCH,
        UNSUPPORTED_CANONICALIZATION_VERSION,
        UNSUPPORTED_HASH_ALGORITHM,
        CHAIN_HEAD_MISMATCH,
        MISSING_ARCHIVE_PROOF,
        ARCHIVE_CHECKSUM_MISMATCH,
        ARCHIVE_SIGNATURE_INVALID,
        ARCHIVE_RANGE_INVALID,
        ARCHIVE_CHAIN_INVALID,
        ARCHIVE_BOUNDARY_MISMATCH
    }
}

