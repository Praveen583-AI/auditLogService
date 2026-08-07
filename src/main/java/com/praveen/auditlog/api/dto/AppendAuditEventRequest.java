package com.praveen.auditlog.api.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Caller-controlled fields for POST /v1/audit/events.
 *
 * <p>Tenant, producer, chain position, receipt time, and integrity fields are
 * deliberately absent because they come from authenticated context or are
 * assigned by the server.</p>
 */
public record AppendAuditEventRequest(
        String eventType,
        int eventSchemaVersion,
        Instant occurredAt,
        ActorDto actor,
        ResourceDto resource,
        Map<String, Object> payload
) {
    public AppendAuditEventRequest {
        payload = payload == null ? null : Map.copyOf(payload);
    }
}
