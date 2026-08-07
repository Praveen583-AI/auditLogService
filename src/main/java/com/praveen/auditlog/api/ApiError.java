package com.praveen.auditlog.api;

/**
 * Stable public error representation. Internal exceptions, database details,
 * request fingerprints, and original idempotent request data are not exposed.
 *
 * @param code machine-readable error code
 * @param message safe human-readable description
 * @param requestId identifier for the individual API attempt
 * @param field optional request field associated with the error
 */
public record ApiError(
        String code,
        String message,
        String requestId,
        String field
) {
}
