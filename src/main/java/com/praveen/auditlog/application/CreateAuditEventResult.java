package com.praveen.auditlog.application;

import com.praveen.auditlog.api.dto.AuditEventResponse;

import java.util.Objects;

public record CreateAuditEventResult(
        AuditEventResponse response,
        boolean replayed
) {
    public CreateAuditEventResult {
        Objects.requireNonNull(response, "response");
    }
}
