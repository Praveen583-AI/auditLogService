
package com.praveen.auditlog.persistence.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.auditlog.integrity.CanonicalAuditEvent;
import com.praveen.auditlog.persistence.ChainVerificationRepository;
import com.praveen.auditlog.retention.ArchiveManifest;
import com.praveen.auditlog.retention.ArchiveProofException;
import com.praveen.auditlog.retention.RetentionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

@Repository
public class JdbcChainVerificationRepository
        implements ChainVerificationRepository {

    private static final int FETCH_SIZE = 256;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RetentionService> retentionServices;

    public JdbcChainVerificationRepository(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            ObjectProvider<RetentionService> retentionServices
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.retentionServices = retentionServices;
    }

    @Override
    public ChainBoundary scan(
            String tenantId,
            String chainId,
            EventVisitor visitor
    ) {
        return jdbc.execute(
                (ConnectionCallback<ChainBoundary>) connection ->
                        scan(connection, tenantId, chainId, visitor)
        );
    }

    private ChainBoundary scan(
            Connection connection,
            String tenantId,
            String chainId,
            EventVisitor visitor
    ) throws java.sql.SQLException {
        ChainBoundary boundary = boundary(connection, tenantId, chainId);
        if (!boundary.exists()) {
            return boundary;
        }

        List<ArchiveManifest> manifests = manifests(
                connection, tenantId, chainId
        );
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT event_id, tenant_id, chain_id, sequence_number,
                       event_type, event_schema_version, producer_id,
                       actor_id, actor_type, actor_identity_source,
                       resource_type, resource_id, occurred_at, recorded_at,
                       payload, previous_hash, content_hash, hash_algorithm,
                       canonicalization_version
                FROM audit_event
                WHERE tenant_id = ? AND chain_id = ?
                ORDER BY sequence_number ASC
                """, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            statement.setString(1, tenantId);
            statement.setString(2, chainId);
            statement.setFetchSize(FETCH_SIZE);

            try (ResultSet rows = statement.executeQuery()) {
                StoredEvent active = rows.next() ? mapEvent(rows) : null;
                int manifestIndex = 0;
                long expected = 1;
                byte[] precedingHash = new byte[32];
                while (expected <= boundary.latestSequence()) {
                    ArchiveManifest manifest = manifestIndex < manifests.size()
                            ? manifests.get(manifestIndex) : null;
                    if (manifest != null && manifest.startSequence() == expected) {
                        RetentionService retention = retentionServices.getIfAvailable();
                        if (retention == null) {
                            throw new ArchiveProofException(
                                    RetentionService.ArchiveFailureReason
                                            .ARCHIVE_OBJECT_MISSING,
                                    expected
                            );
                        }
                        RetentionService.ArchivedEvent following = active != null
                                && active.event().sequenceNumber()
                                == manifest.endSequence() + 1
                                ? new RetentionService.ArchivedEvent(
                                        active.event(), active.contentHash())
                                : null;
                        RetentionService.VerifiedArchive archive =
                                retention.verifiedArchive(
                                        manifest, precedingHash, following
                                );
                        for (RetentionService.ArchivedEvent event
                                : archive.events()) {
                            StoredEvent stored = new StoredEvent(
                                    event.event(), event.contentHash()
                            );
                            if (!visitor.visit(stored)) return boundary;
                            precedingHash = stored.contentHash();
                            expected++;
                        }
                        manifestIndex++;
                        continue;
                    }
                    if (manifest != null && manifest.startSequence() < expected) {
                        throw new ArchiveProofException(
                                RetentionService.ArchiveFailureReason
                                        .ARCHIVE_RANGE_INVALID,
                                expected
                        );
                    }
                    if (active == null) break;
                    if (!visitor.visit(active)) break;
                    precedingHash = active.contentHash();
                    expected = active.event().sequenceNumber() + 1;
                    active = rows.next() ? mapEvent(rows) : null;
                }
            }
        }
        return boundary;
    }

    private StoredEvent mapEvent(ResultSet rows) throws java.sql.SQLException {
        CanonicalAuditEvent event = new CanonicalAuditEvent(
                rows.getObject("event_id", java.util.UUID.class),
                rows.getString("tenant_id"), rows.getString("chain_id"),
                rows.getLong("sequence_number"), rows.getString("event_type"),
                rows.getInt("event_schema_version"), rows.getString("producer_id"),
                rows.getString("actor_id"), rows.getString("actor_type"),
                rows.getString("actor_identity_source"), rows.getString("resource_type"),
                rows.getString("resource_id"), rows.getTimestamp("occurred_at").toInstant(),
                rows.getTimestamp("recorded_at").toInstant(),
                readPayload(rows.getString("payload")), rows.getBytes("previous_hash"),
                rows.getString("hash_algorithm"), rows.getInt("canonicalization_version")
        );
        return new StoredEvent(event, rows.getBytes("content_hash"));
    }

    private List<ArchiveManifest> manifests(
            Connection connection, String tenantId, String chainId
    ) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT manifest_id, manifest_version, tenant_id, chain_id,
                       start_sequence, end_sequence, record_count,
                       predecessor_hash, first_event_hash, last_event_hash,
                       bundle_checksum, checksum_algorithm, bundle_format_version,
                       policy_id, archived_at, storage_location, storage_version,
                       signature_algorithm, signing_key_id, signature_version,
                       signed_at, signature
                FROM archive_manifest
                WHERE tenant_id = ? AND chain_id = ?
                ORDER BY start_sequence
                """)) {
            statement.setString(1, tenantId);
            statement.setString(2, chainId);
            try (ResultSet rows = statement.executeQuery()) {
                java.util.ArrayList<ArchiveManifest> result =
                        new java.util.ArrayList<>();
                while (rows.next()) {
                    result.add(new ArchiveManifest(
                            rows.getObject("manifest_id", java.util.UUID.class),
                            rows.getInt("manifest_version"), rows.getString("tenant_id"),
                            rows.getString("chain_id"), rows.getLong("start_sequence"),
                            rows.getLong("end_sequence"), rows.getLong("record_count"),
                            rows.getBytes("predecessor_hash"),
                            rows.getBytes("first_event_hash"),
                            rows.getBytes("last_event_hash"),
                            rows.getBytes("bundle_checksum"),
                            rows.getString("checksum_algorithm"),
                            rows.getInt("bundle_format_version"),
                            rows.getString("policy_id"),
                            rows.getTimestamp("archived_at").toInstant(),
                            rows.getString("storage_location"),
                            rows.getString("storage_version"),
                            rows.getString("signature_algorithm"),
                            rows.getString("signing_key_id"),
                            rows.getInt("signature_version"),
                            rows.getTimestamp("signed_at").toInstant(),
                            rows.getBytes("signature")
                    ));
                }
                return result;
            }
        }
    }

    private ChainBoundary boundary(
            Connection connection,
            String tenantId,
            String chainId
    ) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT latest_sequence, latest_hash
                FROM chain_head
                WHERE tenant_id = ? AND chain_id = ?
                """)) {
            statement.setString(1, tenantId);
            statement.setString(2, chainId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return ChainBoundary.missing();
                }
                return new ChainBoundary(
                        true,
                        row.getLong("latest_sequence"),
                        row.getBytes("latest_hash")
                );
            }
        }
    }

    private JsonNode readPayload(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception error) {
            throw new IllegalStateException(
                    "Stored audit payload is not valid JSON", error
            );
        }
    }
}

