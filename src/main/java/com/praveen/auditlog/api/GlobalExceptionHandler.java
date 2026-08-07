package com.praveen.auditlog.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Framework-neutral public error mapping boundary.
 *
 * <p>The eventual HTTP framework adapter should delegate exception-specific
 * handling to these mappings and log internal detail separately against the
 * same correlation ID. This class intentionally has no Spring or Jakarta
 * dependency because the repository does not yet define an application
 * framework or build.</p>
 */
public final class GlobalExceptionHandler {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String RETRY_AFTER_HEADER = "Retry-After";

    public ErrorResponse invalidRequest(
            String correlationId,
            List<ApiError.FieldViolation> violations
    ) {
        return response(
                400,
                "INVALID_REQUEST",
                "The request is invalid.",
                correlationId,
                violations,
                Map.of()
        );
    }

    public ErrorResponse unauthenticated(String correlationId) {
        return response(
                401,
                "UNAUTHENTICATED",
                "Authentication is required.",
                correlationId,
                List.of(),
                Map.of("WWW-Authenticate", "Bearer")
        );
    }

    public ErrorResponse accessDenied(String correlationId) {
        return response(
                403,
                "ACCESS_DENIED",
                "You are not permitted to perform this operation.",
                correlationId,
                List.of(),
                Map.of()
        );
    }

    public ErrorResponse chainNotFound(String correlationId) {
        return response(
                404,
                "CHAIN_NOT_FOUND",
                "The requested chain was not found.",
                correlationId,
                List.of(),
                Map.of()
        );
    }

    public ErrorResponse idempotencyKeyReused(String correlationId) {
        return response(
                409,
                "IDEMPOTENCY_KEY_REUSED",
                "The Idempotency-Key has already been used with a different request.",
                correlationId,
                List.of(),
                Map.of()
        );
    }

    public ErrorResponse payloadTooLarge(String correlationId) {
        return response(
                413,
                "PAYLOAD_LIMIT_EXCEEDED",
                "The request exceeds the permitted size.",
                correlationId,
                List.of(),
                Map.of()
        );
    }

    public ErrorResponse rateLimited(String correlationId, int retryAfterSeconds) {
        return retryable(
                429,
                "RATE_LIMIT_EXCEEDED",
                "Too many requests.",
                correlationId,
                retryAfterSeconds
        );
    }

    public ErrorResponse appendTemporarilyUnavailable(
            String correlationId,
            int retryAfterSeconds
    ) {
        return retryable(
                503,
                "APPEND_TEMPORARILY_UNAVAILABLE",
                "The event could not be appended at this time.",
                correlationId,
                retryAfterSeconds
        );
    }

    public ErrorResponse dependencyTemporarilyUnavailable(
            String correlationId,
            int retryAfterSeconds
    ) {
        return retryable(
                503,
                "DEPENDENCY_TEMPORARILY_UNAVAILABLE",
                "The request could not be completed at this time.",
                correlationId,
                retryAfterSeconds
        );
    }

    public ErrorResponse internalError(String correlationId) {
        return response(
                500,
                "INTERNAL_ERROR",
                "The request could not be completed.",
                correlationId,
                List.of(),
                Map.of()
        );
    }

    private ErrorResponse retryable(
            int status,
            String code,
            String message,
            String correlationId,
            int retryAfterSeconds
    ) {
        if (retryAfterSeconds < 0) {
            throw new IllegalArgumentException("retryAfterSeconds must not be negative");
        }
        return response(
                status,
                code,
                message,
                correlationId,
                List.of(),
                Map.of(RETRY_AFTER_HEADER, Integer.toString(retryAfterSeconds))
        );
    }

    private ErrorResponse response(
            int status,
            String code,
            String message,
            String correlationId,
            List<ApiError.FieldViolation> violations,
            Map<String, String> additionalHeaders
    ) {
        Objects.requireNonNull(correlationId, "correlationId");
        ApiError body = new ApiError(code, message, correlationId, violations);
        Map<String, String> headers = new java.util.HashMap<>(additionalHeaders);
        headers.put(CORRELATION_HEADER, correlationId);
        return new ErrorResponse(status, Map.copyOf(headers), body);
    }

    public record ErrorResponse(
            int status,
            Map<String, String> headers,
            ApiError body
    ) {
    }
}
