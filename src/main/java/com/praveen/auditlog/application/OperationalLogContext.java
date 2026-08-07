package com.praveen.auditlog.application;

import org.slf4j.MDC;

public final class OperationalLogContext {

    public static final String CORRELATION_ID = "correlationId";

    private OperationalLogContext() {
    }

    public static String correlationId() {
        String value = MDC.get(CORRELATION_ID);
        return value == null || value.isBlank() ? "unavailable" : value;
    }
}
