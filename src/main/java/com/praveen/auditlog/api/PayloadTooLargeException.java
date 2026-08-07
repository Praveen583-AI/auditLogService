package com.praveen.auditlog.api;

/**
 * Raised by the bounded request or JSON payload policy.
 */
public final class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException() {
        super("Request or payload exceeds its configured boundary");
    }
}
