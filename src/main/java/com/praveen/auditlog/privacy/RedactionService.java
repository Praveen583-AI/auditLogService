package com.praveen.auditlog.privacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.praveen.auditlog.integrity.CanonicalEventSerializer;
import com.praveen.auditlog.security.ActorContext;
import com.praveen.auditlog.security.AuthorizationPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

public final class RedactionService {
    public static final String ALGORITHM = "HMAC-SHA256";
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    private final CanonicalEventSerializer canonical; private final AuthorizationPolicy authorization;
    private final String keyId; private final byte[] key; private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RedactionService(JdbcTemplate jdbc, ObjectMapper json,
                            CanonicalEventSerializer canonical, AuthorizationPolicy authorization,
                            String keyId, byte[] key, Clock clock) {
        this.jdbc = jdbc; this.json = json; this.canonical = canonical;
        this.authorization = authorization; this.keyId = keyId; this.key = key.clone(); this.clock = clock;
        if (keyId == null || keyId.isBlank() || key.length < 32) {
            throw new IllegalArgumentException("A versioned 256-bit redaction commitment key is required");
        }
    }

    @Transactional
    public RedactionRecord redact(ActorContext actor, UUID eventId, String jsonPointer,
                                  String policyId, String reason) {
        authorization.requirePrivilegedJob(actor,
                AuthorizationPolicy.PrivilegedOperation.REDACTION, actor.tenantId());
        JsonNode payload = payload(actor.tenantId(), eventId);
        JsonNode original = payload.at(jsonPointer);
        if (original.isMissingNode()) throw new IllegalArgumentException("Redaction path does not exist");
        validateObjectFieldPointer(payload, jsonPointer);
        byte[] nonce = new byte[32]; random.nextBytes(nonce);
        Instant at = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        RedactionRecord record = new RedactionRecord(
                UUID.randomUUID(), actor.tenantId(), eventId, jsonPointer,
                policyId, reason, actor.actorId(), at, "[REDACTED]", nonce,
                commitment(eventId, jsonPointer, nonce, original), ALGORITHM, keyId
        );
        jdbc.update("""
                INSERT INTO redaction_record (
                    redaction_id, tenant_id, event_id, json_pointer, policy_id,
                    reason, authorized_by, authorized_at, replacement,
                    nonce, original_value_commitment, commitment_algorithm,
                    commitment_key_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, record.redactionId(), record.tenantId(), record.eventId(),
                record.jsonPointer(), record.policyId(), record.reason(),
                record.authorizedBy(), Timestamp.from(record.authorizedAt()),
                record.replacement(), record.nonce(), record.originalValueCommitment(),
                record.commitmentAlgorithm(), record.commitmentKeyId());
        return record;
    }

    public JsonNode apply(String tenantId, UUID eventId, JsonNode originalPayload) {
        JsonNode view = originalPayload.deepCopy();
        for (RedactionRecord record : records(tenantId, eventId)) {
            replace(view, record.jsonPointer(), record.replacement());
        }
        return view;
    }

    public boolean verifyCommitments(ActorContext actor, UUID eventId) {
        authorization.requireCommitmentVerification(actor, actor.tenantId());
        JsonNode payload = payload(actor.tenantId(), eventId);
        for (RedactionRecord record : records(actor.tenantId(), eventId)) {
            JsonNode original = payload.at(record.jsonPointer());
            if (original.isMissingNode() || !MessageDigest.isEqual(
                    record.originalValueCommitment(),
                    commitment(eventId, record.jsonPointer(), record.nonce(), original))) return false;
        }
        return true;
    }

    public List<RedactionRecord> records(String tenantId, UUID eventId) {
        return jdbc.query("""
                SELECT redaction_id, tenant_id, event_id, json_pointer, policy_id,
                       reason, authorized_by, authorized_at, replacement, nonce,
                       original_value_commitment, commitment_algorithm, commitment_key_id
                FROM redaction_record WHERE tenant_id = ? AND event_id = ?
                ORDER BY authorized_at, redaction_id
                """, (r, n) -> new RedactionRecord(
                r.getObject("redaction_id", UUID.class), r.getString("tenant_id"),
                r.getObject("event_id", UUID.class), r.getString("json_pointer"),
                r.getString("policy_id"), r.getString("reason"), r.getString("authorized_by"),
                r.getTimestamp("authorized_at").toInstant(), r.getString("replacement"),
                r.getBytes("nonce"), r.getBytes("original_value_commitment"),
                r.getString("commitment_algorithm"), r.getString("commitment_key_id")
        ), tenantId, eventId);
    }

    private JsonNode payload(String tenantId, UUID eventId) {
        String value = jdbc.queryForObject(
                "SELECT payload::text FROM audit_event WHERE tenant_id = ? AND event_id = ?",
                String.class, tenantId, eventId);
        try { return json.readTree(value); }
        catch (Exception error) { throw new IllegalStateException("Stored payload is invalid", error); }
    }
    private byte[] commitment(UUID eventId, String pointer, byte[] nonce, JsonNode value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(eventId.toString().getBytes(StandardCharsets.UTF_8)); mac.update((byte) 0);
            mac.update(pointer.getBytes(StandardCharsets.UTF_8)); mac.update((byte) 0);
            mac.update(nonce); mac.update((byte) 0); mac.update(canonical.serializeJson(value));
            return mac.doFinal();
        } catch (Exception error) { throw new IllegalStateException("Cannot commit redacted value", error); }
    }
    private void validateObjectFieldPointer(JsonNode root, String pointer) {
        int split = pointer.lastIndexOf('/');
        String parentPointer = split == 0 ? "" : pointer.substring(0, split);
        if (!(root.at(parentPointer) instanceof ObjectNode))
            throw new IllegalArgumentException("Prototype redaction supports object fields only");
    }
    private void replace(JsonNode root, String pointer, String replacement) {
        int split = pointer.lastIndexOf('/');
        JsonNode parent = root.at(split == 0 ? "" : pointer.substring(0, split));
        String field = pointer.substring(split + 1).replace("~1", "/").replace("~0", "~");
        if (parent instanceof ObjectNode object && object.has(field)) object.put(field, replacement);
    }
}
