package com.praveen.auditlog.application;

public final class TemporaryDatabaseFailureException extends RuntimeException {

    public TemporaryDatabaseFailureException(Throwable cause) {
        super("Audit storage is temporarily unavailable", cause);
    }
}
