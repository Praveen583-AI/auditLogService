package com.praveen.auditlog.api.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Caller-controlled fields for POST /v1/audit/events.
 *
 * <p>Tenant and producer identity come from authenticated context. Event
 * identity, chain position, receipt time, and integrity values are assigned by
 * the server and are deliberately absent.</p>
 *
 * <p>The API boundary must apply bounded-JSON limits before binding. The domain
 * layer then validates event identifiers, timestamp policy, payload policy, and
 * canonicalizability. The payload remains unchanged in this transport record
 * so explicit JSON nulls are distinguishable from absent properties.</p>
 */
public record CreateAuditEventRequest(
        String eventType,
        int eventSchemaVersion,
        Instant occurredAt,
        ActorDto actor,
        ResourceDto resource,
        Map<String, Object> payload
) {
}
