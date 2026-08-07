package com.praveen.auditlog.application;

public final class ChainBusyException extends RuntimeException {

    public ChainBusyException(Throwable cause) {
        super("Audit chain remained busy", cause);
    }
}
