package com.praveen.auditlog.api;

/**
 * Signals that a scoped idempotency key was used for different request
 * semantics. Original request data must not be included in the public response.
 */
public final class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("Scoped idempotency key conflicts with an existing request");
    }
}
