package com.praveen.auditlog.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuditQueryService {

    public static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CursorCodec cursors;

    public AuditQueryService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            CursorCodec cursors
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.cursors = cursors;
    }

    public Page search(
            String tenantId,
            AuditEventSpecification specification,
            int requestedPageSize,
            String encodedCursor
    ) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (specification == null) {
            throw new IllegalArgumentException("specification is required");
        }
        if (requestedPageSize < 1 || requestedPageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "pageSize must be between 1 and " + MAX_PAGE_SIZE
            );
        }

        String fingerprint = fingerprint(tenantId, specification);
        CursorCodec.Cursor cursor = cursors.decode(encodedCursor);
        validateCursor(cursor, fingerprint, specification);

        Query query = buildQuery(
                tenantId, specification, requestedPageSize + 1, cursor
        );
        List<AuditEventView> rows = jdbc.query(
                query.sql(),
                (result, ignored) -> new AuditEventView(
                        result.getObject("event_id", UUID.class),
                        result.getString("chain_id"),
                        result.getLong("sequence_number"),
                        result.getString("event_type"),
                        result.getString("actor_id"),
                        result.getString("resource_type"),
                        result.getString("resource_id"),
                        result.getTimestamp("occurred_at").toInstant(),
                        result.getTimestamp("recorded_at").toInstant(),
                        readJson(result.getString("payload")),
                        HexFormat.of().formatHex(result.getBytes("content_hash"))
                ),
                query.arguments().toArray()
        );

        boolean hasMore = rows.size() > requestedPageSize;
        List<AuditEventView> items = hasMore
                ? List.copyOf(rows.subList(0, requestedPageSize))
                : List.copyOf(rows);
        String nextCursor = hasMore
                ? cursorFor(items.get(items.size() - 1), specification, fingerprint)
                : null;
        return new Page(items, nextCursor);
    }

    private Query buildQuery(
            String tenantId,
            AuditEventSpecification specification,
            int limit,
            CursorCodec.Cursor cursor
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT event_id, chain_id, sequence_number, event_type,
                       actor_id, resource_type, resource_id, occurred_at,
                       recorded_at, payload, content_hash
                FROM audit_event
                WHERE tenant_id = ?
                """);
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);

        addEqual(sql, arguments, "chain_id", specification.chainId());
        addEqual(sql, arguments, "actor_id", specification.actorId());
        addEqual(sql, arguments, "resource_type", specification.resourceType());
        addEqual(sql, arguments, "resource_id", specification.resourceId());
        addEqual(sql, arguments, "event_type", specification.eventType());
        if (specification.from() != null) {
            sql.append(" AND recorded_at >= ?");
            arguments.add(Timestamp.from(specification.from()));
        }
        if (specification.to() != null) {
            sql.append(" AND recorded_at < ?");
            arguments.add(Timestamp.from(specification.to()));
        }

        if (cursor != null) {
            if (specification.singleChain()) {
                sql.append(" AND sequence_number > ?");
                arguments.add(cursor.sequenceNumber());
            } else {
                sql.append("""
                         AND (recorded_at, chain_id, sequence_number, event_id)
                             < (?, ?, ?, ?)
                        """);
                arguments.add(Timestamp.from(cursor.recordedAt()));
                arguments.add(cursor.chainId());
                arguments.add(cursor.sequenceNumber());
                arguments.add(cursor.eventId());
            }
        }

        if (specification.singleChain()) {
            sql.append(" ORDER BY sequence_number ASC");
        } else {
            sql.append("""
                     ORDER BY recorded_at DESC, chain_id DESC,
                              sequence_number DESC, event_id DESC
                    """);
        }
        sql.append(" LIMIT ?");
        arguments.add(limit);
        return new Query(sql.toString(), arguments);
    }

    private void validateCursor(
            CursorCodec.Cursor cursor,
            String fingerprint,
            AuditEventSpecification specification
    ) {
        if (cursor == null) {
            return;
        }
        CursorCodec.Mode expected = specification.singleChain()
                ? CursorCodec.Mode.SINGLE_CHAIN
                : CursorCodec.Mode.CROSS_CHAIN;
        if (cursor.mode() != expected
                || !fingerprint.equals(cursor.filterFingerprint())) {
            throw new IllegalArgumentException(
                    "Cursor does not belong to this search"
            );
        }
        if (specification.singleChain()
                && !specification.chainId().equals(cursor.chainId())) {
            throw new IllegalArgumentException(
                    "Cursor does not belong to this chain"
            );
        }
    }

    private String cursorFor(
            AuditEventView last,
            AuditEventSpecification specification,
            String fingerprint
    ) {
        CursorCodec.Cursor cursor = specification.singleChain()
                ? CursorCodec.Cursor.singleChain(
                        fingerprint, last.chainId(), last.sequenceNumber()
                )
                : CursorCodec.Cursor.crossChain(
                        fingerprint, last.recordedAt(), last.chainId(),
                        last.sequenceNumber(), last.eventId()
                );
        return cursors.encode(cursor);
    }

    private String fingerprint(
            String tenantId,
            AuditEventSpecification specification
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (tenantId + "\n" + specification.fingerprintSource())
                            .getBytes(StandardCharsets.UTF_8)
            ));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void addEqual(
            StringBuilder sql,
            List<Object> arguments,
            String column,
            String value
    ) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" = ?");
            arguments.add(value);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception error) {
            throw new IllegalStateException("Stored event payload is invalid", error);
        }
    }

    private record Query(String sql, List<Object> arguments) {
    }

    public record Page(List<AuditEventView> items, String nextCursor) {
    }

    public record AuditEventView(
            UUID eventId,
            String chainId,
            long sequenceNumber,
            String eventType,
            String actorId,
            String resourceType,
            String resourceId,
            Instant occurredAt,
            Instant recordedAt,
            JsonNode payload,
            String contentHash
    ) {
    }
}
