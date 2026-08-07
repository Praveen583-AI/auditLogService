package com.praveen.auditlog.persistence.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.auditlog.integrity.CanonicalAuditEvent;
import com.praveen.auditlog.persistence.ChainVerificationRepository;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Repository
public class JdbcChainVerificationRepository
        implements ChainVerificationRepository {

    private static final int FETCH_SIZE = 256;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcChainVerificationRepository(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
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
                while (rows.next()) {
                    CanonicalAuditEvent event = new CanonicalAuditEvent(
                            rows.getObject("event_id", java.util.UUID.class),
                            rows.getString("tenant_id"),
                            rows.getString("chain_id"),
                            rows.getLong("sequence_number"),
                            rows.getString("event_type"),
                            rows.getInt("event_schema_version"),
                            rows.getString("producer_id"),
                            rows.getString("actor_id"),
                            rows.getString("actor_type"),
                            rows.getString("actor_identity_source"),
                            rows.getString("resource_type"),
                            rows.getString("resource_id"),
                            rows.getTimestamp("occurred_at").toInstant(),
                            rows.getTimestamp("recorded_at").toInstant(),
                            readPayload(rows.getString("payload")),
                            rows.getBytes("previous_hash"),
                            rows.getString("hash_algorithm"),
                            rows.getInt("canonicalization_version")
                    );
                    if (!visitor.visit(new StoredEvent(
                            event, rows.getBytes("content_hash")
                    ))) {
                        break;
                    }
                }
            }
        }
        return boundary;
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
