package com.praveen.auditlog.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable receipt returned after the event and chain head commit atomically.
 */
public record AppendAuditEventResponse(
        UUID eventId,
        String chainId,
        long sequenceNumber,
        Instant recordedAt,
        String contentHash,
        String hashAlgorithm,
        int canonicalizationVersion
) {
}
