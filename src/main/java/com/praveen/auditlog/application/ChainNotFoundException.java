package com.praveen.auditlog.application;

public final class ChainNotFoundException extends RuntimeException {

    public ChainNotFoundException() {
        super("Audit chain was not found");
    }
}
