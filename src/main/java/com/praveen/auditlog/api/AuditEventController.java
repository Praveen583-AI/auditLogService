package com.praveen.auditlog.api;

import com.praveen.auditlog.api.dto.AuditEventResponse;
import com.praveen.auditlog.api.dto.CreateAuditEventRequest;
import com.praveen.auditlog.application.AuditEventSpecification;
import com.praveen.auditlog.application.AuditQueryService;
import com.praveen.auditlog.application.AuditRequestContextProvider;
import com.praveen.auditlog.application.ChainVerificationService;
import com.praveen.auditlog.application.CreateAuditEventResult;
import com.praveen.auditlog.application.VerificationResult;
import com.praveen.auditlog.application.CreateAuditEventUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

@RestController
@RequestMapping("/v1/audit/events")
public final class AuditEventController {

    private final CreateAuditEventUseCase createAuditEvent;
    private final ChainVerificationService chainVerification;
    private final AuditQueryService auditQuery;
    private final AuditRequestContextProvider contextProvider;

    public AuditEventController(
            CreateAuditEventUseCase createAuditEvent,
            ChainVerificationService chainVerification,
            AuditQueryService auditQuery,
            AuditRequestContextProvider contextProvider
    ) {
        this.createAuditEvent = createAuditEvent;
        this.chainVerification = chainVerification;
        this.auditQuery = auditQuery;
        this.contextProvider = contextProvider;
    }

    @GetMapping
    public AuditQueryService.Page search(
            @RequestParam(required = false) String chainId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String cursor
    ) {
        AuditEventSpecification specification =
                new AuditEventSpecification(
                        chainId, actorId, resourceType, resourceId,
                        eventType, from, to
                );
        return auditQuery.search(
                contextProvider.currentContext().tenantId(),
                specification,
                pageSize,
                cursor
        );
    }

    @GetMapping("/chains/{chainId}/verification")
    public VerificationResult verify(@PathVariable String chainId) {
        return chainVerification.verify(chainId);
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
