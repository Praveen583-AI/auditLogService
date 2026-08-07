
package com.praveen.auditlog.retention;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ArchiveManifest(
        UUID manifestId,
        int manifestVersion,
        String tenantId,
        String chainId,
        long startSequence,
        long endSequence,
        long recordCount,
        byte[] predecessorHash,
        byte[] firstEventHash,
        byte[] lastEventHash,
        byte[] bundleChecksum,
        String checksumAlgorithm,
        int bundleFormatVersion,
        String policyId,
        Instant archivedAt,
        String storageLocation,
        String storageVersion,
        String signatureAlgorithm,
        String signingKeyId,
        int signatureVersion,
        Instant signedAt,
        byte[] signature
) {
    public ArchiveManifest {
        Objects.requireNonNull(manifestId, "manifestId");
        tenantId = required(tenantId, "tenantId");
        chainId = required(chainId, "chainId");
        policyId = required(policyId, "policyId");
        storageLocation = required(storageLocation, "storageLocation");
        storageVersion = required(storageVersion, "storageVersion");
        checksumAlgorithm = required(checksumAlgorithm, "checksumAlgorithm");
        signatureAlgorithm = required(signatureAlgorithm, "signatureAlgorithm");
        signingKeyId = required(signingKeyId, "signingKeyId");
        Objects.requireNonNull(archivedAt, "archivedAt");
        Objects.requireNonNull(signedAt, "signedAt");
        predecessorHash = hash(predecessorHash, "predecessorHash");
        firstEventHash = hash(firstEventHash, "firstEventHash");
        lastEventHash = hash(lastEventHash, "lastEventHash");
        bundleChecksum = hash(bundleChecksum, "bundleChecksum");
        signature = Objects.requireNonNull(signature, "signature").clone();
        if (manifestVersion < 1 || bundleFormatVersion < 1 || signatureVersion < 1) {
            throw new IllegalArgumentException("manifest versions must be positive");
        }
        if (startSequence < 1 || endSequence < startSequence
                || recordCount != endSequence - startSequence + 1) {
            throw new IllegalArgumentException("archive range must be positive and contiguous");
        }
        if (signature.length == 0) {
            throw new IllegalArgumentException("signature is required");
        }
    }

    @Override public byte[] predecessorHash() { return predecessorHash.clone(); }
    @Override public byte[] firstEventHash() { return firstEventHash.clone(); }
    @Override public byte[] lastEventHash() { return lastEventHash.clone(); }
    @Override public byte[] bundleChecksum() { return bundleChecksum.clone(); }
    @Override public byte[] signature() { return signature.clone(); }

    private static byte[] hash(byte[] value, String field) {
        byte[] copy = Objects.requireNonNull(value, field).clone();
        if (copy.length != 32) throw new IllegalArgumentException(field + " must be SHA-256");
        return copy;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}

