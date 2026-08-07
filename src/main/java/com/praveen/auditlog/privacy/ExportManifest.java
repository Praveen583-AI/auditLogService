package com.praveen.auditlog.privacy;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ExportManifest(
        UUID exportId,
        int manifestVersion,
        String tenantId,
        String selectorType,
        String selectorValue,
        Instant snapshotAt,
        Instant expiresAt,
        long recordCount,
        List<ChainBoundary> chainBoundaries,
        byte[] recordsChecksum,
        byte[] redactionProofsChecksum,
        byte[] archiveManifestsChecksum,
        String checksumAlgorithm,
        String signatureAlgorithm,
        String signingKeyId,
        int signatureVersion,
        byte[] signature
) {
    public ExportManifest {
        Objects.requireNonNull(exportId); Objects.requireNonNull(snapshotAt);
        Objects.requireNonNull(expiresAt); Objects.requireNonNull(chainBoundaries);
        tenantId = required(tenantId); selectorType = required(selectorType);
        selectorValue = required(selectorValue); checksumAlgorithm = required(checksumAlgorithm);
        signatureAlgorithm = required(signatureAlgorithm); signingKeyId = required(signingKeyId);
        chainBoundaries = List.copyOf(chainBoundaries);
        recordsChecksum = hash(recordsChecksum); redactionProofsChecksum = hash(redactionProofsChecksum);
        archiveManifestsChecksum = hash(archiveManifestsChecksum);
        signature = Objects.requireNonNull(signature).clone();
        if (manifestVersion < 1 || signatureVersion < 1 || recordCount < 0
                || !expiresAt.isAfter(snapshotAt)) throw new IllegalArgumentException("Invalid export manifest");
    }
    @Override public byte[] recordsChecksum() { return recordsChecksum.clone(); }
    @Override public byte[] redactionProofsChecksum() { return redactionProofsChecksum.clone(); }
    @Override public byte[] archiveManifestsChecksum() { return archiveManifestsChecksum.clone(); }
    @Override public byte[] signature() { return signature.clone(); }
    public record ChainBoundary(String chainId, long snapshotSequence, byte[] snapshotHeadHash) {
        public ChainBoundary { chainId = required(chainId); snapshotHeadHash = hash(snapshotHeadHash); }
        @Override public byte[] snapshotHeadHash() { return snapshotHeadHash.clone(); }
    }
    private static byte[] hash(byte[] value) {
        byte[] copy = Objects.requireNonNull(value).clone();
        if (copy.length != 32) throw new IllegalArgumentException("Expected SHA-256 value");
        return copy;
    }
    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required manifest value missing");
        return value;
    }
}
