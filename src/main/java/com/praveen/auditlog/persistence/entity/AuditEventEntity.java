package com.praveen.auditlog.persistence.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable persistence representation of one audit event.
 *
 * <p>{@code payloadJson} is written to PostgreSQL JSONB by the persistence
 * adapter. Hashes are raw digest bytes; hexadecimal encoding belongs at the API
 * boundary.</p>
 */
public record AuditEventEntity(
        UUID eventId,
        String chainId,
        String tenantId,
        long sequenceNumber,
        String eventType,
        int eventSchemaVersion,
        String producerId,
        String actorId,
        String actorType,
        String actorIdentitySource,
        String resourceType,
        String resourceId,
        Instant occurredAt,
        Instant recordedAt,
        String payloadJson,
        byte[] previousHash,
        byte[] contentHash,
        String hashAlgorithm,
        int canonicalizationVersion
) {
    public AuditEventEntity {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(chainId, "chainId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(producerId, "producerId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(actorIdentitySource, "actorIdentitySource");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(payloadJson, "payloadJson");
        previousHash = Objects.requireNonNull(previousHash, "previousHash").clone();
        contentHash = Objects.requireNonNull(contentHash, "contentHash").clone();
        Objects.requireNonNull(hashAlgorithm, "hashAlgorithm");
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be positive");
        }
        if (eventSchemaVersion < 1 || canonicalizationVersion < 1) {
            throw new IllegalArgumentException("version numbers must be positive");
        }
    }

    @Override
    public byte[] previousHash() {
        return previousHash.clone();
    }

    @Override
    public byte[] contentHash() {
        return contentHash.clone();
    }
}
