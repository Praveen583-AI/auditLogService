package com.praveen.auditlog.api;

import com.praveen.auditlog.api.dto.AuditEventResponse;
import com.praveen.auditlog.api.dto.CreateAuditEventRequest;
import com.praveen.auditlog.application.CreateAuditEventResult;
import com.praveen.auditlog.application.CreateAuditEventUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/v1/audit/events")
public final class AuditEventController {

    private final CreateAuditEventUseCase createAuditEvent;

    public AuditEventController(CreateAuditEventUseCase createAuditEvent) {
        this.createAuditEvent = createAuditEvent;
    }

    @PostMapping
    public ResponseEntity<AuditEventResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateAuditEventRequest request
    ) {
        CreateAuditEventResult result = createAuditEvent.create(idempotencyKey, request);
        AuditEventResponse response = result.response();

        ResponseEntity.BodyBuilder builder = result.replayed()
                ? ResponseEntity.ok()
                : ResponseEntity.created(URI.create("/v1/audit/events/" + response.eventId()));

        return builder
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(response);
    }
}
