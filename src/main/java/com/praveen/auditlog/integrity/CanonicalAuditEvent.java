package com.praveen.auditlog.integrity;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Complete immutable input to event canonicalization and hashing.
 */
public record CanonicalAuditEvent(
        UUID eventId,
        String tenantId,
        String chainId,
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
        JsonNode payload,
        byte[] previousHash,
        String hashAlgorithm,
        int canonicalizationVersion
) {
    public CanonicalAuditEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(chainId, "chainId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(producerId, "producerId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(actorIdentitySource, "actorIdentitySource");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(payload, "payload");
        previousHash = Objects.requireNonNull(previousHash, "previousHash").clone();
        Objects.requireNonNull(hashAlgorithm, "hashAlgorithm");

        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be positive");
        }
        if (eventSchemaVersion < 1 || canonicalizationVersion < 1) {
            throw new IllegalArgumentException("version numbers must be positive");
        }
        if (!payload.isObject()) {
            throw new IllegalArgumentException("payload must be a JSON object");
        }
        if (previousHash.length != 32) {
            throw new IllegalArgumentException("previousHash must contain 32 bytes");
        }
    }

    @Override
    public byte[] previousHash() {
        return previousHash.clone();
    }
}
