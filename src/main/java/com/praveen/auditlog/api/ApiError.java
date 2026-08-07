package com.praveen.auditlog.api;

import java.util.List;

/**
 * Stable public error representation. It deliberately excludes exceptions,
 * stack traces, SQL details, credentials, request fingerprints, and rejected
 * values.
 */
public record ApiError(
        String code,
        String message,
        String correlationId,
        List<FieldViolation> violations
) {
    public ApiError {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public record FieldViolation(
            String field,
            String code,
            String message
    ) {
    }
}
