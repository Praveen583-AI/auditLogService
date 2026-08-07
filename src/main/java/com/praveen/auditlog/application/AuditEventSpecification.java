package com.praveen.auditlog.application;

import java.time.Instant;

/**
 * Validated, tenant-scoped audit-event search filters.
 *
 * <p>Status is intentionally not represented: AuditEvent has no common status
 * field. Producer-specific status values remain inside payload and are not a
 * prototype search contract.</p>
 */
public record AuditEventSpecification(
        String chainId,
        String actorId,
        String resourceType,
        String resourceId,
        String eventType,
        Instant from,
        Instant to
) {
    public AuditEventSpecification {
        chainId = blankToNull(chainId);
        actorId = blankToNull(actorId);
        resourceType = blankToNull(resourceType);
        resourceId = blankToNull(resourceId);
        eventType = blankToNull(eventType);
        if (resourceId != null && resourceType == null) {
            throw new IllegalArgumentException(
                    "resourceType is required when resourceId is supplied"
            );
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }

    public boolean singleChain() {
        return chainId != null;
    }

    String fingerprintSource() {
        return String.join("\n",
                value(chainId), value(actorId), value(resourceType),
                value(resourceId), value(eventType), value(from), value(to)
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
