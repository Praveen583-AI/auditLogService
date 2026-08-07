package com.praveen.auditlog.application;

import com.praveen.auditlog.api.dto.CreateAuditEventRequest;

public interface CreateAuditEventUseCase {

    CreateAuditEventResult create(
            String idempotencyKey,
            CreateAuditEventRequest request
    );
}
