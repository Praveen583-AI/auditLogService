package com.praveen.auditlog.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.auditlog.api.dto.ActorDto;
import com.praveen.auditlog.api.dto.CreateAuditEventRequest;
import com.praveen.auditlog.api.dto.ResourceDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class AuditWriteServiceIntegrationTest {

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
        jdbc.execute("TRUNCATE idempotency_record, audit_event, chain_head CASCADE");
        given(contextProvider.currentContext()).willReturn(new AuditRequestContext(
                "tenant-1",
                "producer-1",
                "actor-1",
                "USER",
                "PRODUCER_ASSERTED"
        ));
    }

    @AfterEach
    void removeFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS force_chain_head_failure ON chain_head");
        jdbc.execute("DROP FUNCTION IF EXISTS force_chain_head_failure()");
    }

    @Test
    void exactReplayReturnsOriginalResultWithoutAnotherAppend() throws Exception {
        CreateAuditEventRequest request = request();

        CreateAuditEventResult first = service.create("same-key", request);
        CreateAuditEventResult replay = service.create("same-key", request);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.response()).isEqualTo(first.response());
        assertThat(count("audit_event")).isEqualTo(1);
        assertThat(count("idempotency_record")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT latest_sequence FROM chain_head WHERE chain_id = ?",
                Long.class,
                "tenant:tenant-1"
        )).isEqualTo(1L);
    }

    @Test
    void failedChainHeadUpdateRollsBackEventHeadAndIdempotency() throws Exception {
        jdbc.execute("""
                CREATE FUNCTION force_chain_head_failure()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'forced chain-head failure';
                END
                $$
                """);
        jdbc.execute("""
                CREATE TRIGGER force_chain_head_failure
                BEFORE UPDATE ON chain_head
                FOR EACH ROW EXECUTE FUNCTION force_chain_head_failure()
                """);

        assertThatThrownBy(() -> service.create("rollback-key", request()))
                .isInstanceOf(RuntimeException.class);

        assertThat(count("audit_event")).isZero();
        assertThat(count("chain_head")).isZero();
        assertThat(count("idempotency_record")).isZero();
    }

    private CreateAuditEventRequest request() throws Exception {
        return new CreateAuditEventRequest(
                "ACCOUNT_UPDATED",
                1,
                Instant.parse("2026-08-07T14:30:12.123Z"),
                new ActorDto("actor-1", "USER"),
                new ResourceDto("ACCOUNT", "account-1"),
                objectMapper.readTree(
                        "{\"result\":\"accepted\",\"amount\":1.0}"
                )
        );
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
