package com.praveen.auditlog.application;

public final class InvalidCursorException extends RuntimeException {

    private final Reason reason;

    public InvalidCursorException(Reason reason) {
        super("Pagination cursor is invalid");
        this.reason = reason;
    }

    public InvalidCursorException(Reason reason, Throwable cause) {
        super("Pagination cursor is invalid", cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        MALFORMED,
        VERSION_UNSUPPORTED,
        CONTEXT_MISMATCH
    }
}
