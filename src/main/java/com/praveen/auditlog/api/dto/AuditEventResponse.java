package com.praveen.auditlog.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable receipt returned only after the event, chain head, and idempotency
 * result have committed atomically.
 */
public record AuditEventResponse(
        UUID eventId,
        String chainId,
        long sequenceNumber,
        Instant recordedAt,
        String contentHash,
        String hashAlgorithm,
        int canonicalizationVersion
) {
}
