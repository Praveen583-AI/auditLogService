package com.praveen.auditlog.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.auditlog.api.IdempotencyKeyReusedException;
import com.praveen.auditlog.api.dto.ActorDto;
import com.praveen.auditlog.api.dto.CreateAuditEventRequest;
import com.praveen.auditlog.api.dto.ResourceDto;
import com.praveen.auditlog.support.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PostgresTestContainerConfiguration.class)
class AuditWriteIntegrationTest {
    @Autowired AuditWriteService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoBean AuditRequestContextProvider contextProvider;

    @BeforeEach void isolateTestData() {
        jdbc.execute("TRUNCATE idempotency_record, audit_event, chain_head CASCADE");
        given(contextProvider.currentContext()).willReturn(new AuditRequestContext(
                "tenant-1", "producer-1", "actor-1", "USER", "JWT"));
    }

    @AfterEach void removeFailureInjection() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_chain_head_update ON chain_head");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_chain_head_update()");
    }

    @Test void firstAndNextEventsPersistOneContiguousChain() throws Exception {
        CreateAuditEventResult first = service.create("first-key", request("accepted"));
        CreateAuditEventResult second = service.create("second-key", request("approved"));

        assertThat(first.response().sequenceNumber()).isEqualTo(1);
        assertThat(second.response().sequenceNumber()).isEqualTo(2);
        Map<String, Object> secondRow = jdbc.queryForMap("""
                SELECT previous_hash, content_hash FROM audit_event
                WHERE chain_id = 'tenant:tenant-1' AND sequence_number = 2
                """);
        byte[] firstHash = jdbc.queryForObject("""
                SELECT content_hash FROM audit_event
                WHERE chain_id = 'tenant:tenant-1' AND sequence_number = 1
                """, byte[].class);
        assertThat((byte[]) secondRow.get("previous_hash")).isEqualTo(firstHash);
        assertThat(jdbc.queryForObject("SELECT latest_sequence FROM chain_head WHERE tenant_id='tenant-1'", Long.class))
                .isEqualTo(2L);
        assertThat(count("audit_event")).isEqualTo(2);
    }

    @Test void exactReplayReturnsEveryOriginalResponseIdentifier() throws Exception {
        CreateAuditEventResult original = service.create("replay-key", request("accepted"));
        CreateAuditEventResult replay = service.create("replay-key", request("accepted"));

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.response()).isEqualTo(original.response());
        assertThat(count("audit_event")).isOne();
        Map<String, Object> stored = jdbc.queryForMap("""
                SELECT event_id, response_status, response_json
                FROM idempotency_record WHERE status='COMPLETED'
                """);
        assertThat(stored.get("event_id")).isEqualTo(original.response().eventId());
        assertThat(stored.get("response_status")).isEqualTo(201);
        assertThat(stored.get("response_json").toString())
                .contains(original.response().eventId().toString())
                .contains(original.response().contentHash());
    }

    @Test void reuseWithDifferentPayloadPreservesOriginalEventAndReceipt() throws Exception {
        CreateAuditEventResult original = service.create("conflict-key", request("accepted"));

        assertThatThrownBy(() -> service.create("conflict-key", request("declined")))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(count("audit_event")).isOne();
        assertThat(count("idempotency_record")).isOne();
        assertThat(jdbc.queryForObject("SELECT event_id FROM idempotency_record", java.util.UUID.class))
                .isEqualTo(original.response().eventId());
    }

    @Test void failureAfterEventInsertRollsBackAllThreeStateChanges() throws Exception {
        jdbc.execute("""
                CREATE FUNCTION fail_chain_head_update() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected rollback'; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_chain_head_update BEFORE UPDATE ON chain_head
                FOR EACH ROW EXECUTE FUNCTION fail_chain_head_update()
                """);

        assertThatThrownBy(() -> service.create("rollback-key", request("accepted")))
                .isInstanceOf(RuntimeException.class);
        assertThat(count("audit_event")).isZero();
        assertThat(count("chain_head")).isZero();
        assertThat(count("idempotency_record")).isZero();
    }

    private CreateAuditEventRequest request(String result) throws Exception {
        return new CreateAuditEventRequest("ACCOUNT_UPDATED", 1,
                Instant.parse("2026-08-07T14:30:12.123Z"),
                new ActorDto("untrusted-body-actor", "USER"),
                new ResourceDto("ACCOUNT", "account-1"),
                json.readTree("{\"result\":\"" + result + "\",\"amount\":1.0}"));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
