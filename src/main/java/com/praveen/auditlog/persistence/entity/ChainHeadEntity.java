package com.praveen.auditlog.persistence.entity;

import java.time.Instant;
import java.util.Objects;

/**
 * Mutable database state represented as an immutable Java snapshot.
 *
 * <p>The row coordinates appends; it is not independent audit evidence. An
 * empty chain uses sequence and version zero with the configured genesis hash.</p>
 */
public record ChainHeadEntity(
        String chainId,
        String tenantId,
        long latestSequence,
        byte[] latestHash,
        long version,
        Instant updatedAt
) {
    public ChainHeadEntity {
        Objects.requireNonNull(chainId, "chainId");
        Objects.requireNonNull(tenantId, "tenantId");
        latestHash = Objects.requireNonNull(latestHash, "latestHash").clone();
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (latestSequence < 0 || version < 0) {
            throw new IllegalArgumentException("latestSequence and version must not be negative");
        }
    }

    @Override
    public byte[] latestHash() {
        return latestHash.clone();
    }
}
