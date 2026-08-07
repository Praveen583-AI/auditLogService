package com.praveen.auditlog.persistence.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.auditlog.api.dto.AuditEventResponse;
import com.praveen.auditlog.persistence.IdempotencyRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcIdempotencyRepository implements IdempotencyRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcIdempotencyRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean claim(
            UUID id,
            String tenantId,
            String producerId,
            String operation,
            byte[] keyHash,
            byte[] requestFingerprint,
            Instant createdAt,
            Instant expiresAt
    ) {
        return jdbc.update("""
                INSERT INTO idempotency_record (
                    idempotency_id, tenant_id, producer_id, operation,
                    idempotency_key_hash, request_fingerprint, status,
                    created_at, updated_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'PROCESSING', ?, ?, ?)
                ON CONFLICT (
                    tenant_id, producer_id, operation, idempotency_key_hash
                ) DO NOTHING
                """,
                id, tenantId, producerId, operation, keyHash,
                requestFingerprint, Timestamp.from(createdAt), Timestamp.from(createdAt),
                Timestamp.from(expiresAt)
        ) == 1;
    }

    @Override
    public Optional<Record> find(
            String tenantId,
            String producerId,
            String operation,
            byte[] keyHash
    ) {
        List<Record> records = jdbc.query("""
                SELECT idempotency_id, request_fingerprint, status, response_json
                FROM idempotency_record
                WHERE tenant_id = ?
                  AND producer_id = ?
                  AND operation = ?
                  AND idempotency_key_hash = ?
                """, (row, ignored) -> new Record(
                row.getObject("idempotency_id", UUID.class),
                row.getBytes("request_fingerprint"),
                row.getString("status"),
                readResponse(row.getString("response_json"))
        ), tenantId, producerId, operation, keyHash);
        return records.stream().findFirst();
    }

    @Override
    public void complete(UUID id, AuditEventResponse response, Instant completedAt) {
        int rows = jdbc.update("""
                UPDATE idempotency_record
                SET status = 'COMPLETED',
                    event_id = ?,
                    response_json = ?::jsonb,
                    response_status = 201,
                    updated_at = ?
                WHERE idempotency_id = ?
                  AND status = 'PROCESSING'
                """,
                response.eventId(), writeResponse(response), Timestamp.from(completedAt), id
        );
        if (rows != 1) {
            throw new IllegalStateException("Idempotency completion affected " + rows + " rows");
        }
    }

    private AuditEventResponse readResponse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AuditEventResponse.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Stored idempotency response is invalid", error);
        }
    }

    private String writeResponse(AuditEventResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot serialize idempotency response", error);
        }
    }
}
