package com.praveen.auditlog.integrity;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;

/**
 * Canonicalization contract version 1.
 *
 * <p>Object keys are ordered by Java String natural order (UTF-16 code units),
 * arrays retain order, strings use deterministic JSON escaping, numbers are
 * normalized by mathematical value, and timestamps use UTC with exactly six
 * fractional digits.</p>
 */
public final class CanonicalJsonAuditEventCanonicalizerV1
        implements AuditEventCanonicalizer {

    public static final int VERSION = 1;
    public static final String DOMAIN = "audit-event";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final java.time.format.DateTimeFormatter TIMESTAMP_FORMATTER =
            new DateTimeFormatterBuilder().appendInstant(6).toFormatter();

    @Override
    public int version() {
        return VERSION;
    }

    @Override
    public byte[] canonicalize(CanonicalAuditEvent event) {
        if (event.canonicalizationVersion() != VERSION) {
            throw new CanonicalizationException(
                    "unsupported canonicalization version: "
                            + event.canonicalizationVersion()
            );
        }
        if (!HASH_ALGORITHM.equals(event.hashAlgorithm())) {
            throw new CanonicalizationException(
                    "unsupported hash algorithm: " + event.hashAlgorithm()
            );
        }

        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("actorId", event.actorId());
        root.put("actorIdentitySource", event.actorIdentitySource());
        root.put("actorType", event.actorType());
        root.put("canonicalizationVersion", event.canonicalizationVersion());
        root.put("chainId", event.chainId());
        root.put("domain", DOMAIN);
        root.put("eventId", event.eventId().toString());
        root.put("eventSchemaVersion", event.eventSchemaVersion());
        root.put("eventType", event.eventType());
        root.put("hashAlgorithm", event.hashAlgorithm());
        root.put("occurredAt", timestamp(event.occurredAt(), "occurredAt"));
        root.set("payload", event.payload());
        root.put("previousHash", hex(event.previousHash()));
        root.put("producerId", event.producerId());
        root.put("recordedAt", timestamp(event.recordedAt(), "recordedAt"));
        root.put("resourceId", event.resourceId());
        root.put("resourceType", event.resourceType());
        root.put("sequenceNumber", event.sequenceNumber());
        root.put("tenantId", event.tenantId());

        return CanonicalJsonWriter.write(root);
    }

    private String timestamp(Instant value, String field) {
        if (value.getNano() % 1_000 != 0) {
            throw new CanonicalizationException(
                    field + " has precision finer than microseconds"
            );
        }
        return TIMESTAMP_FORMATTER.format(value);
    }

    private String hex(byte[] bytes) {
        char[] output = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            output[index * 2] = alphabet[value >>> 4];
            output[index * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(output);
    }
}
