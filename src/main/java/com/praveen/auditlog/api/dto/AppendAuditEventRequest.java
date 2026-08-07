package com.praveen.auditlog.api.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Caller-controlled fields for POST /v1/audit/events.
 *
 * <p>Tenant, producer, chain position, receipt time, and integrity fields are
 * deliberately absent because they come from authenticated context or are
 * assigned by the server.</p>
 *
 * <p>The API layer must validate and convert {@code payload} into the domain's
 * immutable canonical representation. This transport DTO does not use
 * {@code Map.copyOf}, because JSON payloads may legitimately distinguish an
 * explicit {@code null} value from an absent field.</p>
 */
public record AppendAuditEventRequest(
        String eventType,
        int eventSchemaVersion,
        Instant occurredAt,
        ActorDto actor,
        ResourceDto resource,
        Map<String, Object> payload
) {
}
