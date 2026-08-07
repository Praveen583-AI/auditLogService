package com.praveen.auditlog.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

/**
 * Caller-controlled fields for POST /v1/audit/events.
 *
 * <p>Request-byte, JSON-depth, collection-size, and canonicalization limits
 * must be enforced by the bounded JSON input policy in addition to these
 * envelope constraints.</p>
 */
public record CreateAuditEventRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$")
        String eventType,

        @Min(1)
        int eventSchemaVersion,

        @NotNull
        Instant occurredAt,

        @NotNull
        @Valid
        ActorDto actor,

        @NotNull
        @Valid
        ResourceDto resource,

        @NotNull
        Map<String, Object> payload
) {
}
