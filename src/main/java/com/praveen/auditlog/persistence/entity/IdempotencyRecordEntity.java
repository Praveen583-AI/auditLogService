package com.praveen.auditlog.persistence.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable Java snapshot of mutable request-processing state.
 *
 * <p>The raw Idempotency-Key and original payload are deliberately not stored.
 * A completed record retains the small successful response for stable replay.</p>
 */
public record IdempotencyRecordEntity(
        UUID idempotencyId,
        String tenantId,
        String producerId,
        String operation,
        byte[] idempotencyKeyHash,
        byte[] requestFingerprint,
        Status status,
        UUID eventId,
        String responseJson,
        Integer responseStatus,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {
    public IdempotencyRecordEntity {
        Objects.requireNonNull(idempotencyId, "idempotencyId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(producerId, "producerId");
        Objects.requireNonNull(operation, "operation");
        idempotencyKeyHash =
                Objects.requireNonNull(idempotencyKeyHash, "idempotencyKeyHash").clone();
        requestFingerprint =
                Objects.requireNonNull(requestFingerprint, "requestFingerprint").clone();
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");

        boolean hasCompletedResult =
                eventId != null && responseJson != null && responseStatus != null;
        if (status == Status.PROCESSING && hasCompletedResult) {
            throw new IllegalArgumentException("processing record cannot have a completed result");
        }
        if (status == Status.COMPLETED && !hasCompletedResult) {
            throw new IllegalArgumentException("completed record requires the original result");
        }
    }

    @Override
    public byte[] idempotencyKeyHash() {
        return idempotencyKeyHash.clone();
    }

    @Override
    public byte[] requestFingerprint() {
        return requestFingerprint.clone();
    }

    public enum Status {
        PROCESSING,
        COMPLETED
    }
}
