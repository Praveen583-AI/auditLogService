package com.praveen.auditlog.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.auditlog.api.dto.ActorDto;
import com.praveen.auditlog.api.dto.CreateAuditEventRequest;
import com.praveen.auditlog.api.dto.ResourceDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class AuditWriteFailureIntegrationTest {

    private static final String CORRELATION_ID = "failure-test-correlation";
    private static final String PAYLOAD_MARKER =
            "regulated-payload-must-not-be-logged";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AuditWriteService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuditRequestContextProvider contextProvider;

    @BeforeEach
    void setUp() {
        dropFailureTrigger();
        jdbc.execute("TRUNCATE idempotency_record, audit_event, chain_head CASCADE");
        given(contextProvider.currentContext()).willReturn(
                new AuditRequestContext(
                        "failure-tenant",
                        "producer-1",
                        "actor-1",
                        "USER",
                        "AUTHENTICATED_PRINCIPAL"
                )
        );
        MDC.put(OperationalLogContext.CORRELATION_ID, CORRELATION_ID);
    }

    @AfterEach
    void cleanUp() {
        MDC.remove(OperationalLogContext.CORRELATION_ID);
        dropFailureTrigger();
    }

    @Test
    void chainHeadLockTimeoutRetriesThenReturnsChainBusy(
            CapturedOutput output
    ) throws Exception {
        installFailureTrigger("55P03", "simulated lock timeout");

        assertThatThrownBy(() -> service.create("lock-timeout", request()))
                .isInstanceOf(ChainBusyException.class);

        assertThat(count("audit_event")).isZero();
        assertThat(count("chain_head")).isZero();
        assertThat(count("idempotency_record")).isZero();
        assertThat(output).contains("audit_append_retry")
                .contains("reason=CHAIN_LOCK_TIMEOUT")
                .contains(CORRELATION_ID)
                .doesNotContain(PAYLOAD_MARKER);
    }

    @Test
    void transientConnectionFailureIsMappedWithoutRetry(
            CapturedOutput output
    ) throws Exception {
        installFailureTrigger("08006", "simulated connection failure");

        assertThatThrownBy(() -> service.create("connection-failure", request()))
                .isInstanceOf(TemporaryDatabaseFailureException.class)
                .hasMessageNotContaining("simulated connection failure");

        assertThat(count("audit_event")).isZero();
        assertThat(count("chain_head")).isZero();
        assertThat(count("idempotency_record")).isZero();
        assertThat(output)
                .doesNotContain("audit_append_retry")
                .doesNotContain("simulated connection failure")
                .doesNotContain(PAYLOAD_MARKER);
    }

    private void installFailureTrigger(String sqlState, String message) {
        jdbc.execute("""
                CREATE FUNCTION fail_chain_head_update()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION '%s' USING ERRCODE = '%s';
                    RETURN NEW;
                END
                $$
                """.formatted(message, sqlState));
        jdbc.execute("""
                CREATE TRIGGER fail_chain_head_update
                BEFORE UPDATE ON chain_head
                FOR EACH ROW EXECUTE FUNCTION fail_chain_head_update()
                """);
    }

    private void dropFailureTrigger() {
        jdbc.execute(
                "DROP TRIGGER IF EXISTS fail_chain_head_update ON chain_head"
        );
        jdbc.execute(
                "DROP FUNCTION IF EXISTS fail_chain_head_update()"
        );
    }

    private CreateAuditEventRequest request() throws Exception {
        return new CreateAuditEventRequest(
                "ACCOUNT_UPDATED",
                1,
                Instant.parse("2026-08-07T14:30:12.123Z"),
                new ActorDto("actor-1", "USER"),
                new ResourceDto("ACCOUNT", "account-1"),
                objectMapper.readTree(
                        "{\"note\":\"" + PAYLOAD_MARKER + "\"}"
                )
        );
    }

    private long count(String table) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table,
                Long.class
        );
    }
}
