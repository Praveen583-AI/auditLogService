package com.praveen.auditlog.api;

import java.util.List;

/**
 * Stable public error representation. It deliberately excludes exception
 * names, stack traces, SQL details, credentials, request fingerprints, and
 * rejected values.
 *
 * @param code stable machine-readable error code
 * @param message safe human-readable summary
 * @param correlationId identifier for this individual API attempt
 * @param violations optional field-level violations
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
