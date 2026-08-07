package com.praveen.auditlog.application;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.praveen.auditlog.api.IdempotencyKeyReusedException;
import com.praveen.auditlog.api.dto.AuditEventResponse;
import com.praveen.auditlog.api.dto.CreateAuditEventRequest;
import com.praveen.auditlog.integrity.CanonicalAuditEvent;
import com.praveen.auditlog.integrity.CanonicalEventSerializer;
import com.praveen.auditlog.integrity.HashService;
import com.praveen.auditlog.persistence.AuditEventRepository;
import com.praveen.auditlog.persistence.ChainHeadRepository;
import com.praveen.auditlog.persistence.IdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class TransactionalAuditAppender {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TransactionalAuditAppender.class);
    private static final String OPERATION = "APPEND_AUDIT_EVENT";
    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofHours(24);
    private static final byte[] GENESIS_HASH = new byte[32];

    private final AuditRequestContextProvider contextProvider;
    private final IdempotencyRepository idempotency;
    private final ChainHeadRepository chainHeads;
    private final AuditEventRepository events;
    private final CanonicalEventSerializer serializer;
    private final HashService hashes;
    private final Clock clock;
    private final Supplier<UUID> eventIds;

    public TransactionalAuditAppender(
            AuditRequestContextProvider contextProvider,
            IdempotencyRepository idempotency,
            ChainHeadRepository chainHeads,
            AuditEventRepository events,
            CanonicalEventSerializer serializer,
            HashService hashes,
            Clock clock,
            Supplier<UUID> eventIds
    ) {
        this.contextProvider = contextProvider;
        this.idempotency = idempotency;
        this.chainHeads = chainHeads;
        this.events = events;
        this.serializer = serializer;
        this.hashes = hashes;
        this.clock = clock;
        this.eventIds = eventIds;
    }

    @Transactional
    public CreateAuditEventResult append(
            String idempotencyKey,
            CreateAuditEventRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }

        AuditRequestContext context = contextProvider.currentContext();
        byte[] keyHash = hashes.sha256(
                idempotencyKey.getBytes(StandardCharsets.UTF_8)
        );
        byte[] requestFingerprint = fingerprint(context, request);
        Instant transactionTime = microseconds(clock.instant());
        UUID idempotencyId = eventIds.get();

        boolean claimed = idempotency.claim(
                idempotencyId,
                context.tenantId(),
                context.producerId(),
                OPERATION,
                keyHash,
                requestFingerprint,
                transactionTime,
                transactionTime.plus(IDEMPOTENCY_RETENTION)
        );

        if (!claimed) {
            IdempotencyRepository.Record existing = idempotency.find(
                    context.tenantId(),
                    context.producerId(),
                    OPERATION,
                    keyHash
            ).orElseThrow(() -> new IllegalStateException(
                    "Conflicting idempotency record disappeared"
            ));

            if (!Arrays.equals(existing.requestFingerprint(), requestFingerprint)) {
                LOGGER.warn(
                        "idempotency_conflict correlationId={} operation={} keyHashPrefix={} existingStatus={} reason=REQUEST_FINGERPRINT_MISMATCH",
                        OperationalLogContext.correlationId(), OPERATION,
                        hashes.hex(keyHash).substring(0, 12), existing.status()
                );
                throw new IdempotencyKeyReusedException();
            }
            if (!"COMPLETED".equals(existing.status()) || existing.response() == null) {
                throw new IllegalStateException("Idempotent request is still processing");
            }
            return new CreateAuditEventResult(existing.response(), true);
        }

        ChainHeadRepository.ChainHead head = chainHeads.lockOrCreate(
                context.chainId(),
                context.tenantId(),
                GENESIS_HASH,
                transactionTime
        );

        long sequence = Math.addExact(head.latestSequence(), 1);
        Instant recordedAt = microseconds(clock.instant());
        UUID eventId = eventIds.get();

        CanonicalAuditEvent event = new CanonicalAuditEvent(
                eventId,
                context.tenantId(),
                context.chainId(),
                sequence,
                request.eventType(),
                request.eventSchemaVersion(),
                context.producerId(),
                context.actorId(),
                context.actorType(),
                context.actorIdentitySource(),
                request.resource().type(),
                request.resource().id(),
                microseconds(request.occurredAt()),
                recordedAt,
                request.payload(),
                head.latestHash(),
                HashService.ALGORITHM,
                serializer.version()
        );
        byte[] contentHash = hashes.hash(event);

        events.insert(event, contentHash);
        if (!chainHeads.advance(head, sequence, contentHash, recordedAt)) {
            throw new ChainHeadConflictException();
        }

        AuditEventResponse response = new AuditEventResponse(
                eventId,
                context.chainId(),
                sequence,
                recordedAt,
                hashes.hex(contentHash),
                HashService.ALGORITHM,
                serializer.version()
        );
        idempotency.complete(idempotencyId, response, recordedAt);
        return new CreateAuditEventResult(response, false);
    }

    private byte[] fingerprint(
            AuditRequestContext context,
            CreateAuditEventRequest request
    ) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("actorId", context.actorId());
        root.put("actorIdentitySource", context.actorIdentitySource());
        root.put("actorType", context.actorType());
        root.put("eventSchemaVersion", request.eventSchemaVersion());
        root.put("eventType", request.eventType());
        root.put("occurredAt", microseconds(request.occurredAt()).toString());
        root.set("payload", request.payload());
        root.put("producerId", context.producerId());
        root.put("resourceId", request.resource().id());
        root.put("resourceType", request.resource().type());
        root.put("tenantId", context.tenantId());
        return hashes.sha256(serializer.serializeJson(root));
    }

    private Instant microseconds(Instant value) {
        int nanos = value.getNano();
        return value.with(java.time.temporal.ChronoField.NANO_OF_SECOND,
                (nanos / 1_000) * 1_000L);
    }
}
