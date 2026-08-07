package com.praveen.auditlog.integrity;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * Stable entry point for canonical serialization.
 */
public final class CanonicalEventSerializer {

    private final AuditEventCanonicalizer eventCanonicalizer;

    public CanonicalEventSerializer(AuditEventCanonicalizer eventCanonicalizer) {
        this.eventCanonicalizer =
                Objects.requireNonNull(eventCanonicalizer, "eventCanonicalizer");
    }

    public int version() {
        return eventCanonicalizer.version();
    }

    public byte[] serialize(CanonicalAuditEvent event) {
        return eventCanonicalizer.canonicalize(event);
    }

    public byte[] serializeJson(JsonNode value) {
        return CanonicalJsonWriter.write(value);
    }
}
