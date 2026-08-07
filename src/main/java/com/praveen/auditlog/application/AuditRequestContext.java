package com.praveen.auditlog.application;

public record AuditRequestContext(
        String tenantId,
        String producerId,
        String actorId,
        String actorType,
        String actorIdentitySource
) {
    public String chainId() {
        return "tenant:" + tenantId;
    }
}
