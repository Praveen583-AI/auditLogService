package com.praveen.auditlog.persistence;

import com.praveen.auditlog.integrity.CanonicalAuditEvent;

public interface AuditEventRepository {

    void insert(CanonicalAuditEvent event, byte[] contentHash);
}
