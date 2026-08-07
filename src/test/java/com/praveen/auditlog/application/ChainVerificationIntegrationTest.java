package com.praveen.auditlog.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.auditlog.api.dto.ActorDto;
import com.praveen.auditlog.api.dto.CreateAuditEventRequest;
import com.praveen.auditlog.api.dto.ResourceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class ChainVerificationIntegrationTest {

    private static final String CHAIN_ID = "tenant:verification-tenant";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AuditWriteService writes;

    @Autowired
    private ChainVerificationService verification;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuditRequestContextProvider contextProvider;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE idempotency_record, audit_event, chain_head CASCADE");
        given(contextProvider.currentContext()).willReturn(
                new AuditRequestContext(
                        "verification-tenant",
                        "producer-1",
                        "actor-1",
                        "USER",
                        "PRODUCER_ASSERTED"
                )
        );
        for (int index = 1; index <= 3; index++) {
            writes.create("verification-" + index, request(index));
        }
    }

    @Test
    void validChainPasses() {
        VerificationResult result = verification.verify(CHAIN_ID);

        assertThat(result.status()).isEqualTo(VerificationResult.Status.VALID);
        assertThat(result.valid()).isTrue();
        assertThat(result.verifiedCount()).isEqualTo(3);
        assertThat(result.firstSequence()).isEqualTo(1);
        assertThat(result.lastVerifiedSequence()).isEqualTo(3);
    }

    @Test
    void modifiedPayloadFailsAtChangedEvent(CapturedOutput output) {
        jdbc.update("""
                UPDATE audit_event
                SET payload = '{"tampered":true}'::jsonb
                WHERE chain_id = ? AND sequence_number = 2
                """, CHAIN_ID);

        VerificationResult result = verification.verify(CHAIN_ID);

        assertThat(result.status()).isEqualTo(
                VerificationResult.Status.INVALID
        );
        assertThat(result.failureReason()).isEqualTo(
                VerificationResult.FailureReason.CONTENT_HASH_MISMATCH
        );
        assertThat(result.failureSequence()).isEqualTo(2);
        assertThat(result.verifiedCount()).isEqualTo(1);
        assertThat(output).contains("CONTENT_HASH_MISMATCH")
                .contains("failureSequence=2")
                .contains("verifiedCount=1")
                .doesNotContain("tampered")
                .doesNotContain("payload")
                .doesNotContain("previousHash")
                .doesNotContain("contentHash");
    }

    @Test
    void missingRecordFailsAtFirstObservedGap() {
        jdbc.update("""
                DELETE FROM idempotency_record
                WHERE event_id = (
                    SELECT event_id FROM audit_event
                    WHERE chain_id = ? AND sequence_number = 2
                )
                """, CHAIN_ID);
        jdbc.update("""
                DELETE FROM audit_event
                WHERE chain_id = ? AND sequence_number = 2
                """, CHAIN_ID);

        VerificationResult result = verification.verify(CHAIN_ID);

        assertThat(result.failureReason()).isEqualTo(
                VerificationResult.FailureReason.SEQUENCE_GAP
        );
        assertThat(result.failureSequence()).isEqualTo(3);
        assertThat(result.verifiedCount()).isEqualTo(1);
    }

    @Test
    void reorderedRecordsFailAtBrokenLink() {
        jdbc.update("""
                UPDATE audit_event SET sequence_number = 99
                WHERE chain_id = ? AND sequence_number = 1
                """, CHAIN_ID);
        jdbc.update("""
                UPDATE audit_event SET sequence_number = 1
                WHERE chain_id = ? AND sequence_number = 2
                """, CHAIN_ID);
        jdbc.update("""
                UPDATE audit_event SET sequence_number = 2
                WHERE chain_id = ? AND sequence_number = 99
                """, CHAIN_ID);

        VerificationResult result = verification.verify(CHAIN_ID);

        assertThat(result.failureReason()).isEqualTo(
                VerificationResult.FailureReason.PREVIOUS_HASH_MISMATCH
        );
        assertThat(result.failureSequence()).isEqualTo(1);
        assertThat(result.verifiedCount()).isZero();
    }

    private CreateAuditEventRequest request(int index) throws Exception {
        return new CreateAuditEventRequest(
                "ACCOUNT_UPDATED",
                1,
                Instant.parse("2026-08-07T14:30:12.123Z").plusSeconds(index),
                new ActorDto("actor-1", "USER"),
                new ResourceDto("ACCOUNT", "account-" + index),
                objectMapper.readTree(
                        "{\"result\":\"accepted\",\"index\":" + index + "}"
                )
        );
    }
}
