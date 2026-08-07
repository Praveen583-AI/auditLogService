package com.praveen.auditlog.persistence.jdbc;

import com.praveen.auditlog.integrity.CanonicalAuditEvent;
import com.praveen.auditlog.persistence.AuditEventRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
public class JdbcAuditEventRepository implements AuditEventRepository {

    private final JdbcTemplate jdbc;

    public JdbcAuditEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(CanonicalAuditEvent event, byte[] contentHash) {
        jdbc.update("""
                INSERT INTO audit_event (
                    event_id, chain_id, tenant_id, sequence_number, event_type,
                    event_schema_version, producer_id, actor_id, actor_type,
                    actor_identity_source, resource_type, resource_id,
                    occurred_at, recorded_at, payload, previous_hash,
                    content_hash, hash_algorithm, canonicalization_version
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb,
                    ?, ?, ?, ?
                )
                """,
                event.eventId(), event.chainId(), event.tenantId(),
                event.sequenceNumber(), event.eventType(),
                event.eventSchemaVersion(), event.producerId(), event.actorId(),
                event.actorType(), event.actorIdentitySource(),
                event.resourceType(), event.resourceId(), Timestamp.from(event.occurredAt()),
                Timestamp.from(event.recordedAt()), event.payload().toString(),
                event.previousHash(), contentHash, event.hashAlgorithm(),
                event.canonicalizationVersion()
        );
    }
}
