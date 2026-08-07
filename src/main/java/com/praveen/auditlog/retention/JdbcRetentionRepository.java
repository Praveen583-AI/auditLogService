
package com.praveen.auditlog.retention;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.auditlog.integrity.CanonicalAuditEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcRetentionRepository
        implements RetentionService.RetentionRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcRetentionRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public RetentionService.ArchiveRange selectClosedRange(
            RetentionService.ArchiveRequest request
    ) {
        Long latest = jdbc.queryForObject("""
                SELECT latest_sequence FROM chain_head
                WHERE tenant_id = ? AND chain_id = ?
                """, Long.class, request.tenantId(), request.chainId());
        if (latest == null || request.endSequence() >= latest) {
            throw new RetentionService.ArchiveFailure(
                    RetentionService.ArchiveFailureReason.PARTIAL_OR_INVALID_RANGE
            );
        }
        List<RetentionService.ArchivedEvent> events = jdbc.query("""
                SELECT event_id, tenant_id, chain_id, sequence_number,
                       event_type, event_schema_version, producer_id,
                       actor_id, actor_type, actor_identity_source,
                       resource_type, resource_id, occurred_at, recorded_at,
                       payload, previous_hash, content_hash, hash_algorithm,
                       canonicalization_version
                FROM audit_event
                WHERE tenant_id = ? AND chain_id = ?
                  AND sequence_number BETWEEN ? AND ?
                ORDER BY sequence_number
                """, this::mapEvent, request.tenantId(), request.chainId(),
                request.startSequence(), request.endSequence());
        return new RetentionService.ArchiveRange(
                request.tenantId(), request.chainId(), request.startSequence(),
                request.endSequence(), events
        );
    }

    @Override
    public boolean hasEffectiveLegalHold(
            String tenantId, String chainId, long start, long end
    ) {
        Boolean held = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM (
                        SELECT DISTINCT ON (hold_id)
                               action_type, start_sequence, end_sequence
                        FROM legal_hold_action
                        WHERE tenant_id = ? AND chain_id = ?
                        ORDER BY hold_id, recorded_at DESC, action_id DESC
                    ) effective
                    WHERE action_type <> 'RELEASED'
                      AND start_sequence <= ?
                      AND COALESCE(end_sequence, 9223372036854775807) >= ?
                )
                """, Boolean.class, tenantId, chainId, end, start);
        return Boolean.TRUE.equals(held);
    }

    @Override
    public void appendAction(UUID manifestId,
                             RetentionService.LifecycleAction action,
                             Instant at) {
        jdbc.update("""
                INSERT INTO archive_lifecycle_action (
                    action_id, manifest_id, action_type, recorded_at, details
                ) VALUES (?, ?, ?, ?, '{}'::jsonb)
                """, UUID.randomUUID(), manifestId, action.name(),
                java.sql.Timestamp.from(at));
    }

    @Override
    @Transactional
    public void publishAndRemoveHot(
            ArchiveManifest manifest,
            RetentionService.ArchiveRequest request
    ) {
        jdbc.execute("LOCK TABLE legal_hold_action IN SHARE MODE");
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                result -> null, request.chainId()
        );
        if (hasEffectiveLegalHold(request.tenantId(), request.chainId(),
                request.startSequence(), request.endSequence())) {
            throw new RetentionService.ArchiveFailure(
                    RetentionService.ArchiveFailureReason.LEGAL_HOLD_ACTIVE
            );
        }
        Boolean overlaps = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM archive_manifest
                    WHERE tenant_id = ? AND chain_id = ?
                      AND start_sequence <= ? AND end_sequence >= ?
                )
                """, Boolean.class, request.tenantId(), request.chainId(),
                request.endSequence(), request.startSequence());
        if (Boolean.TRUE.equals(overlaps)) {
            throw new RetentionService.ArchiveFailure(
                    RetentionService.ArchiveFailureReason.PARTIAL_OR_INVALID_RANGE
            );
        }
        insertManifest(manifest);
        appendAction(manifest.manifestId(),
                RetentionService.LifecycleAction.MANIFEST_PUBLISHED,
                manifest.archivedAt());
        jdbc.update("""
                DELETE FROM idempotency_record
                WHERE event_id IN (
                    SELECT event_id FROM audit_event
                    WHERE tenant_id = ? AND chain_id = ?
                      AND sequence_number BETWEEN ? AND ?
                )
                """, request.tenantId(), request.chainId(),
                request.startSequence(), request.endSequence());
        int removed = jdbc.update("""
                DELETE FROM audit_event
                WHERE tenant_id = ? AND chain_id = ?
                  AND sequence_number BETWEEN ? AND ?
                """, request.tenantId(), request.chainId(),
                request.startSequence(), request.endSequence());
        if (removed != manifest.recordCount()) {
            throw new RetentionService.ArchiveFailure(
                    RetentionService.ArchiveFailureReason.PARTIAL_OR_INVALID_RANGE
            );
        }
        appendAction(manifest.manifestId(),
                RetentionService.LifecycleAction.HOT_DATA_REMOVED,
                manifest.archivedAt());
    }

    private void insertManifest(ArchiveManifest m) {
        jdbc.update("""
                INSERT INTO archive_manifest (
                    manifest_id, manifest_version, tenant_id, chain_id,
                    start_sequence, end_sequence, record_count,
                    predecessor_hash, first_event_hash, last_event_hash,
                    bundle_checksum, checksum_algorithm, bundle_format_version,
                    policy_id, archived_at, storage_location, storage_version,
                    signature_algorithm, signing_key_id, signature_version,
                    signed_at, signature
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, m.manifestId(), m.manifestVersion(), m.tenantId(), m.chainId(),
                m.startSequence(), m.endSequence(), m.recordCount(),
                m.predecessorHash(), m.firstEventHash(), m.lastEventHash(),
                m.bundleChecksum(), m.checksumAlgorithm(), m.bundleFormatVersion(),
                m.policyId(), java.sql.Timestamp.from(m.archivedAt()),
                m.storageLocation(), m.storageVersion(),
                m.signatureAlgorithm(), m.signingKeyId(), m.signatureVersion(),
                java.sql.Timestamp.from(m.signedAt()), m.signature());
    }

    private RetentionService.ArchivedEvent mapEvent(ResultSet rows, int ignored)
            throws SQLException {
        try {
            CanonicalAuditEvent event = new CanonicalAuditEvent(
                    rows.getObject("event_id", UUID.class),
                    rows.getString("tenant_id"), rows.getString("chain_id"),
                    rows.getLong("sequence_number"), rows.getString("event_type"),
                    rows.getInt("event_schema_version"), rows.getString("producer_id"),
                    rows.getString("actor_id"), rows.getString("actor_type"),
                    rows.getString("actor_identity_source"), rows.getString("resource_type"),
                    rows.getString("resource_id"), rows.getTimestamp("occurred_at").toInstant(),
                    rows.getTimestamp("recorded_at").toInstant(),
                    objectMapper.readTree(rows.getString("payload")),
                    rows.getBytes("previous_hash"), rows.getString("hash_algorithm"),
                    rows.getInt("canonicalization_version")
            );
            return new RetentionService.ArchivedEvent(
                    event, rows.getBytes("content_hash")
            );
        } catch (SQLException error) {
            throw error;
        } catch (Exception error) {
            throw new SQLException("Stored audit event cannot be archived", error);
        }
    }
}

