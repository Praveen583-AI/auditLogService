package com.praveen.auditlog.persistence;

import com.praveen.auditlog.api.dto.AuditEventResponse;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository {

    boolean claim(
            UUID id,
            String tenantId,
            String producerId,
            String operation,
            byte[] keyHash,
            byte[] requestFingerprint,
            Instant createdAt,
            Instant expiresAt
    );

    Optional<Record> find(
            String tenantId,
            String producerId,
            String operation,
            byte[] keyHash
    );

    void complete(UUID id, AuditEventResponse response, Instant completedAt);

    record Record(
            UUID id,
            byte[] requestFingerprint,
            String status,
            AuditEventResponse response
    ) {
        public Record {
            requestFingerprint = requestFingerprint.clone();
        }

        @Override
        public byte[] requestFingerprint() {
            return requestFingerprint.clone();
        }
    }
}
