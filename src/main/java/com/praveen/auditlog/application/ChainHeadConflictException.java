package com.praveen.auditlog.application;

public final class ChainHeadConflictException extends RuntimeException {

    public ChainHeadConflictException() {
        super("Chain head did not match the locked state");
    }
}
