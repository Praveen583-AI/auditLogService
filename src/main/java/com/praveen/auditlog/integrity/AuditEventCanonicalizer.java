package com.praveen.auditlog.integrity;

public interface AuditEventCanonicalizer {

    int version();

    byte[] canonicalize(CanonicalAuditEvent event);
}
