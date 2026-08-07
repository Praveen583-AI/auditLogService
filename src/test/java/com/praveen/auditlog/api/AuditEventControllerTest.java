package com.praveen.auditlog.api;

import com.praveen.auditlog.api.dto.AuditEventResponse;
import com.praveen.auditlog.application.AuditEventSpecification;
import com.praveen.auditlog.application.AuditQueryService;
import com.praveen.auditlog.application.AuditRequestContext;
import com.praveen.auditlog.application.AuditRequestContextProvider;
import com.praveen.auditlog.application.ChainBusyException;
import com.praveen.auditlog.application.ChainVerificationService;
import com.praveen.auditlog.application.InvalidCursorException;
import com.praveen.auditlog.application.TemporaryDatabaseFailureException;
import com.praveen.auditlog.application.CreateAuditEventResult;
import com.praveen.auditlog.application.CreateAuditEventUseCase;
import com.praveen.auditlog.application.VerificationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;\nimport org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(AuditEventController.class)\n@AutoConfigureMockMvc(addFilters = false)
class AuditEventControllerTest {

    private static final String PATH = "/v1/audit/events";
    private static final String IDEMPOTENCY_KEY = "01J4QX8T2M7K9P3Y6R5N1C0BHA";
    private static final String CORRELATION_ID = "test-correlation-123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateAuditEventUseCase createAuditEvent;

    @MockitoBean
    private ChainVerificationService chainVerification;

    @MockitoBean
    private AuditQueryService auditQuery;

    @MockitoBean
    private AuditRequestContextProvider contextProvider;

    @Test
    void invalidRequestReturns400WithSafeViolations() throws Exception {
        mockMvc.perform(post(PATH)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("X-Correlation-Id", CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "invalid type",
                                  "eventSchemaVersion": 0,
                                  "payload": {}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Correlation-Id", CORRELATION_ID))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.violations", hasSize(5)))
                .andExpect(content().string(not(containsString("jakarta.validation"))))
                .andExpect(content().string(not(containsString("rejectedValue"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void conflictingIdempotencyKeyReturns409WithoutOriginalData(
            CapturedOutput output
    ) throws Exception {
        given(createAuditEvent.create(eq(IDEMPOTENCY_KEY), any()))
                .willThrow(new IdempotencyKeyReusedException());

        mockMvc.perform(validRequest("secret-account-value"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.violations", hasSize(0)))
                .andExpect(content().string(not(containsString("secret-account-value"))))
                .andExpect(content().string(not(containsString("fingerprint"))))
                .andExpect(content().string(not(containsString("stackTrace"))));

        assertThat(output).contains("IDEMPOTENCY_KEY_REUSED")
                .contains(CORRELATION_ID)
                .doesNotContain("secret-account-value")
                .doesNotContain("fingerprint")
                .doesNotContain("stackTrace");
    }

    @Test
    void oversizedPayloadReturns413WithoutEchoingPayload(
            CapturedOutput output
    ) throws Exception {
        given(createAuditEvent.create(eq(IDEMPOTENCY_KEY), any()))
                .willThrow(new PayloadTooLargeException());

        mockMvc.perform(validRequest("sensitive-payload-marker"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("The request exceeds the permitted size."))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(content().string(not(containsString("sensitive-payload-marker"))))
                .andExpect(content().string(not(containsString("PayloadTooLargeException"))))
                .andExpect(content().string(not(containsString("stackTrace"))));

        assertThat(output).contains("PAYLOAD_LIMIT_EXCEEDED")
                .contains(CORRELATION_ID)
                .doesNotContain("sensitive-payload-marker")
                .doesNotContain("PayloadTooLargeException")
                .doesNotContain("stackTrace");
    }

    @Test
    void unexpectedFailureReturnsFixed500WithoutExceptionDetails(
            CapturedOutput output
    ) throws Exception {
        given(createAuditEvent.create(eq(IDEMPOTENCY_KEY), any()))
                .willThrow(new RuntimeException(
                        "password=secret SQL=select * from audit_event"));

        mockMvc.perform(validRequest("sensitive-payload-marker"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("The request could not be completed."))
                .andExpect(content().string(not(containsString("password=secret"))))
                .andExpect(content().string(not(containsString("select *"))))
                .andExpect(content().string(not(containsString("RuntimeException"))))
                .andExpect(content().string(not(containsString("stackTrace"))));

        assertThat(output).contains("INTERNAL_ERROR")
                .contains(CORRELATION_ID)
                .doesNotContain("password=secret")
                .doesNotContain("select *")
                .doesNotContain("RuntimeException")
                .doesNotContain("stackTrace");
    }

    @Test
    void completedInvalidVerificationReturns200WithReason() throws Exception {
        given(chainVerification.verify("tenant:tenant-1")).willReturn(
                VerificationResult.invalid(
                        VerificationResult.FailureReason.CONTENT_HASH_MISMATCH,
                        2, 1, 1L, 1L,
                        "The stored content hash does not match the recalculated hash."
                )
        );

        mockMvc.perform(get(
                        PATH + "/chains/tenant:tenant-1/verification"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.failureReason")
                        .value("CONTENT_HASH_MISMATCH"))
                .andExpect(jsonPath("$.failureSequence").value(2));
    }

    @Test
    void malformedCursorReturns400WithoutLoggingCursor(
            CapturedOutput output
    ) throws Exception {
        String rawCursor = "sensitive-invalid-cursor";
        given(contextProvider.currentContext()).willReturn(
                new AuditRequestContext(
                        "tenant-1", "producer-1", "actor-1",
                        "USER", "AUTHENTICATED_PRINCIPAL"
                )
        );
        given(auditQuery.search(
                eq("tenant-1"),
                any(AuditEventSpecification.class),
                eq(50),
                eq(rawCursor)
        )).willThrow(new InvalidCursorException(
                InvalidCursorException.Reason.MALFORMED
        ));

        mockMvc.perform(get(PATH)
                        .param("cursor", rawCursor)
                        .header("X-Correlation-Id", CORRELATION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURSOR_MALFORMED"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(content().string(not(containsString(rawCursor))));

        assertThat(output).contains("CURSOR_MALFORMED")
                .contains(CORRELATION_ID)
                .doesNotContain(rawCursor);
    }

    @Test
    void exhaustedChainLockReturns503WithRetryAfter(
            CapturedOutput output
    ) throws Exception {
        given(createAuditEvent.create(eq(IDEMPOTENCY_KEY), any()))
                .willThrow(new ChainBusyException(
                        new RuntimeException("sensitive lock detail")
                ));

        mockMvc.perform(validRequest("sensitive-payload-marker"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.code").value("CHAIN_BUSY"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(content().string(not(
                        containsString("sensitive lock detail")
                )));

        assertThat(output).contains("CHAIN_BUSY")
                .contains(CORRELATION_ID)
                .doesNotContain("sensitive lock detail")
                .doesNotContain("sensitive-payload-marker");
    }

    @Test
    void transientConnectionFailureReturnsSanitized503(
            CapturedOutput output
    ) throws Exception {
        given(createAuditEvent.create(eq(IDEMPOTENCY_KEY), any()))
                .willThrow(new TemporaryDatabaseFailureException(
                        new RuntimeException(
                                "host=db-secret password=secret payload-value"
                        )
                ));

        mockMvc.perform(validRequest("payload-value"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("TEMPORARY_DATABASE_FAILURE"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(content().string(not(containsString("db-secret"))))
                .andExpect(content().string(not(containsString("payload-value"))));

        assertThat(output).contains("TEMPORARY_DATABASE_FAILURE")
                .contains(CORRELATION_ID)
                .doesNotContain("db-secret")
                .doesNotContain("password=secret")
                .doesNotContain("payload-value");
    }

    @Test
    void successfulCreateReturnsDurableReceipt() throws Exception {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        AuditEventResponse response = new AuditEventResponse(
                eventId,
                "tenant:tenant-1",
                1,
                Instant.parse("2026-08-07T14:30:12.456Z"),
                "a".repeat(64),
                "SHA-256",
                1
        );
        given(createAuditEvent.create(eq(IDEMPOTENCY_KEY), any()))
                .willReturn(new CreateAuditEventResult(response, false));

        mockMvc.perform(validRequest("account-1"))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/v1/audit/events/" + eventId
                ))
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.sequenceNumber").value(1));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            validRequest(String resourceId) {
        return post(PATH)
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .header("X-Correlation-Id", CORRELATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "eventType": "ACCOUNT_UPDATED",
                          "eventSchemaVersion": 1,
                          "occurredAt": "2026-08-07T14:30:12.123Z",
                          "actor": {
                            "id": "employee-42",
                            "type": "USER"
                          },
                          "resource": {
                            "type": "ACCOUNT",
                            "id": "%s"
                          },
                          "payload": {
                            "changedFields": ["address"]
                          }
                        }
                        """.formatted(resourceId));
    }
}
