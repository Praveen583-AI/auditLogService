package com.praveen.auditlog.privacy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RedactionRecord(
        UUID redactionId,
        String tenantId,
        UUID eventId,
        String jsonPointer,
        String policyId,
        String reason,
        String authorizedBy,
        Instant authorizedAt,
        String replacement,
        byte[] nonce,
        byte[] originalValueCommitment,
        String commitmentAlgorithm,
        String commitmentKeyId
) {
    public RedactionRecord {
        Objects.requireNonNull(redactionId);
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(authorizedAt);
        tenantId = required(tenantId, "tenantId");
        jsonPointer = required(jsonPointer, "jsonPointer");
        policyId = required(policyId, "policyId");
        reason = required(reason, "reason");
        authorizedBy = required(authorizedBy, "authorizedBy");
        replacement = required(replacement, "replacement");
        commitmentAlgorithm = required(commitmentAlgorithm, "commitmentAlgorithm");
        commitmentKeyId = required(commitmentKeyId, "commitmentKeyId");
        nonce = Objects.requireNonNull(nonce).clone();
        originalValueCommitment = Objects.requireNonNull(originalValueCommitment).clone();
        if (!jsonPointer.startsWith("/") || nonce.length < 16
                || originalValueCommitment.length != 32) {
            throw new IllegalArgumentException("Invalid redaction proof");
        }
    }
    @Override public byte[] nonce() { return nonce.clone(); }
    @Override public byte[] originalValueCommitment() {
        return originalValueCommitment.clone();
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
