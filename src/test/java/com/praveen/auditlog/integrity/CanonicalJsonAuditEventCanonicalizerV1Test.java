package com.praveen.auditlog.integrity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalJsonAuditEventCanonicalizerV1Test {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CanonicalJsonAuditEventCanonicalizerV1 canonicalizer =
            new CanonicalJsonAuditEventCanonicalizerV1();
    private final Sha256AuditEventHasher hasher =
            new Sha256AuditEventHasher(canonicalizer);

    @Test
    void fixedVersionOneVectorProtectsTheByteContract() throws Exception {
        CanonicalAuditEvent event = event(payload("{\"result\":\"accepted\"}"));
        String expected = "{"
                + "\\"actorId\\":\\"actor-1\\","
                + "\\"actorIdentitySource\\":\\"PRODUCER_ASSERTED\\","
                + "\\"actorType\\":\\"USER\\","
                + "\\"canonicalizationVersion\\":1,"
                + "\\"chainId\\":\\"tenant:tenant-1\\","
                + "\\"domain\\":\\"audit-event\\","
                + "\\"eventId\\":\\"00000000-0000-0000-0000-000000000001\\","
                + "\\"eventSchemaVersion\\":1,"
                + "\\"eventType\\":\\"ACCOUNT_UPDATED\\","
                + "\\"hashAlgorithm\\":\\"SHA-256\\","
                + "\\"occurredAt\\":\\"2026-08-07T14:30:12.123000Z\\","
                + "\\"payload\\":{\\"result\\":\\"accepted\\"},"
                + "\\"previousHash\\":"
                + "\\"0000000000000000000000000000000000000000000000000000000000000000\\","
                + "\\"producerId\\":\\"producer-1\\","
                + "\\"recordedAt\\":\\"2026-08-07T14:30:12.456789Z\\","
                + "\\"resourceId\\":\\"account-1\\","
                + "\\"resourceType\\":\\"ACCOUNT\\","
                + "\\"sequenceNumber\\":1,"
                + "\\"tenantId\\":\\"tenant-1\\""
                + "}";

        assertThat(new String(
                canonicalizer.canonicalize(event),
                java.nio.charset.StandardCharsets.UTF_8
        )).isEqualTo(expected);
        assertThat(hasher.digestHex(event))
                .isEqualTo("193a16824a9147906e669fa6c539f2a6fb6d3cb99656300704f9818180019d77");
    }

    @Test
    void objectOrderWhitespaceAndEquivalentNumbersProduceSameDigest() throws Exception {
        CanonicalAuditEvent first = event(payload(
                "{\"z\":1.0,\"nested\":{\"b\":true,\"a\":null}}"
        ));
        CanonicalAuditEvent second = event(payload(
                """
                {
                  "nested": {"a": null, "b": true},
                  "z": 1e0
                }
                """
        ));

        assertThat(canonicalizer.canonicalize(first))
                .isEqualTo(canonicalizer.canonicalize(second));
        assertThat(hasher.digest(first)).isEqualTo(hasher.digest(second));
    }

    @Test
    void canonicalBytesAreIndependentOfDefaultLocaleAndTimezone() throws Exception {
        CanonicalAuditEvent event = event(payload(
                "{\"amount\":1234.500,\"label\":\"é\"}"
        ));
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            byte[] first = canonicalizer.canonicalize(event);

            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"));
            byte[] second = canonicalizer.canonicalize(event);

            assertThat(second).isEqualTo(first);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    void explicitNullAndAbsentPropertyProduceDifferentDigests() throws Exception {
        assertThat(hasher.digest(event(payload("{\"reason\":null}"))))
                .isNotEqualTo(hasher.digest(event(payload("{}"))));
    }

    @Test
    void arrayOrderChangesDigest() throws Exception {
        assertThat(hasher.digest(event(payload("{\"items\":[\"A\",\"B\"]}"))))
                .isNotEqualTo(hasher.digest(event(payload("{\"items\":[\"B\",\"A\"]}"))));
    }

    @Test
    void everyIntegrityFieldAndPreviousHashIndependentlyAffectDigest() throws Exception {
        CanonicalAuditEvent base = event(payload("{\"result\":\"accepted\"}"));
        byte[] expected = hasher.digest(base);
        List<String> fields = List.of(
                "eventId", "tenantId", "chainId", "sequenceNumber", "eventType",
                "eventSchemaVersion", "producerId", "actorId", "actorType",
                "actorIdentitySource", "resourceType", "resourceId", "occurredAt",
                "recordedAt", "payload", "previousHash"
        );

        assertThat(fields).allSatisfy(field ->
                assertThat(hasher.digest(changed(base, field)))
                        .as(field)
                        .isNotEqualTo(expected)
        );

        String canonical = new String(
                canonicalizer.canonicalize(base),
                java.nio.charset.StandardCharsets.UTF_8
        );
        assertThat(canonical)
                .contains("\"hashAlgorithm\":\"SHA-256\"")
                .contains("\"canonicalizationVersion\":1");
    }

    @Test
    void rejectsUnsupportedVersionAndExcessTimestampPrecision() throws Exception {
        CanonicalAuditEvent base = event(payload("{}"));

        assertThatThrownBy(() -> canonicalizer.canonicalize(new CanonicalAuditEvent(
                base.eventId(), base.tenantId(), base.chainId(), base.sequenceNumber(),
                base.eventType(), base.eventSchemaVersion(), base.producerId(),
                base.actorId(), base.actorType(), base.actorIdentitySource(),
                base.resourceType(), base.resourceId(), base.occurredAt(),
                base.recordedAt(), base.payload(), base.previousHash(),
                base.hashAlgorithm(), 2
        ))).isInstanceOf(CanonicalizationException.class);

        assertThatThrownBy(() -> canonicalizer.canonicalize(copy(
                base, base.eventId(), base.tenantId(), base.chainId(),
                base.sequenceNumber(), base.eventType(), base.eventSchemaVersion(),
                base.producerId(), base.actorId(), base.actorType(),
                base.actorIdentitySource(), base.resourceType(), base.resourceId(),
                Instant.parse("2026-08-07T14:30:12.123456789Z"),
                base.recordedAt(), base.payload(), base.previousHash()
        ))).isInstanceOf(CanonicalizationException.class);
    }

    private CanonicalAuditEvent changed(CanonicalAuditEvent base, String field) {
        try {
            return copy(
                    base,
                    field.equals("eventId")
                            ? UUID.fromString("00000000-0000-0000-0000-000000000002")
                            : base.eventId(),
                    field.equals("tenantId") ? "tenant-2" : base.tenantId(),
                    field.equals("chainId") ? "tenant:tenant-2" : base.chainId(),
                    field.equals("sequenceNumber") ? 2 : base.sequenceNumber(),
                    field.equals("eventType") ? "ACCOUNT_CLOSED" : base.eventType(),
                    field.equals("eventSchemaVersion") ? 2 : base.eventSchemaVersion(),
                    field.equals("producerId") ? "producer-2" : base.producerId(),
                    field.equals("actorId") ? "actor-2" : base.actorId(),
                    field.equals("actorType") ? "SERVICE" : base.actorType(),
                    field.equals("actorIdentitySource")
                            ? "AUTHENTICATED_PRINCIPAL"
                            : base.actorIdentitySource(),
                    field.equals("resourceType") ? "CUSTOMER" : base.resourceType(),
                    field.equals("resourceId") ? "account-2" : base.resourceId(),
                    field.equals("occurredAt")
                            ? base.occurredAt().plusSeconds(1)
                            : base.occurredAt(),
                    field.equals("recordedAt")
                            ? base.recordedAt().plusSeconds(1)
                            : base.recordedAt(),
                    field.equals("payload")
                            ? payload("{\"result\":\"rejected\"}")
                            : base.payload(),
                    field.equals("previousHash") ? changedHash() : base.previousHash()
            );
        } catch (JsonProcessingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private CanonicalAuditEvent event(JsonNode payload) {
        return new CanonicalAuditEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "tenant-1",
                "tenant:tenant-1",
                1,
                "ACCOUNT_UPDATED",
                1,
                "producer-1",
                "actor-1",
                "USER",
                "PRODUCER_ASSERTED",
                "ACCOUNT",
                "account-1",
                Instant.parse("2026-08-07T14:30:12.123000Z"),
                Instant.parse("2026-08-07T14:30:12.456789Z"),
                payload,
                new byte[32],
                "SHA-256",
                1
        );
    }

    private CanonicalAuditEvent copy(
            CanonicalAuditEvent base,
            UUID eventId,
            String tenantId,
            String chainId,
            long sequenceNumber,
            String eventType,
            int schemaVersion,
            String producerId,
            String actorId,
            String actorType,
            String actorIdentitySource,
            String resourceType,
            String resourceId,
            Instant occurredAt,
            Instant recordedAt,
            JsonNode payload,
            byte[] previousHash
    ) {
        return new CanonicalAuditEvent(
                eventId, tenantId, chainId, sequenceNumber, eventType, schemaVersion,
                producerId, actorId, actorType, actorIdentitySource, resourceType,
                resourceId, occurredAt, recordedAt, payload, previousHash,
                base.hashAlgorithm(), base.canonicalizationVersion()
        );
    }

    private byte[] changedHash() {
        byte[] value = new byte[32];
        value[31] = 1;
        return value;
    }

    private JsonNode payload(String json) throws JsonProcessingException {
        return objectMapper.readTree(json);
    }
}
