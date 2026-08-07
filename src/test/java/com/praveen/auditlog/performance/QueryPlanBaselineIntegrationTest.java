package com.praveen.auditlog.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.praveen.auditlog.application.AuditRequestContextProvider;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class QueryPlanBaselineIntegrationTest {
    private static final int EVENT_COUNT = 10_000;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired JdbcTemplate jdbc;

    @MockitoBean AuditRequestContextProvider contextProvider;

    @BeforeEach
    void seedSyntheticBaseline() {
        jdbc.execute("TRUNCATE idempotency_record, audit_event, chain_head CASCADE");
        jdbc.update("""
                INSERT INTO chain_head(chain_id,tenant_id,latest_sequence,latest_hash,version,updated_at)
                VALUES ('tenant:plan','plan',?,decode(repeat('00',32),'hex'),?,now())
                """, EVENT_COUNT, EVENT_COUNT);
        jdbc.execute("""
                INSERT INTO audit_event(
                    event_id,chain_id,tenant_id,sequence_number,event_type,event_schema_version,
                    producer_id,actor_id,actor_type,actor_identity_source,resource_type,resource_id,
                    occurred_at,recorded_at,payload,previous_hash,content_hash,hash_algorithm,
                    canonicalization_version)
                SELECT (substr(md5(i::text),1,8)||'-'||substr(md5(i::text),9,4)||'-'||
                        substr(md5(i::text),13,4)||'-'||substr(md5(i::text),17,4)||'-'||
                        substr(md5(i::text),21,12))::uuid,
                       'tenant:plan','plan',i,'PLAN_EVENT',1,'baseline-producer',
                       'actor-'||(i % 100),'USER','TEST','ACCOUNT','resource-'||(i % 100),
                       timestamptz '2026-01-01 00:00:00+00' + i * interval '1 second',
                       timestamptz '2026-01-01 00:00:00+00' + i * interval '1 second',
                       jsonb_build_object('syntheticSequence',i),decode(repeat('00',32),'hex'),
                       decode(md5(i::text)||md5(i::text),'hex'),'SHA-256',1
                FROM generate_series(1,10000) AS i
                """);
        jdbc.execute("ANALYZE audit_event");
    }

    @Test
    void currentQueriesUseExpectedIndexesAndWritePlanReport() throws Exception {
        Map<String, String> plans = new LinkedHashMap<>();
        plans.put("tenant-cursor", explain("""
                SELECT event_id,chain_id,sequence_number,recorded_at FROM audit_event
                WHERE tenant_id='plan'
                ORDER BY recorded_at DESC,chain_id DESC,sequence_number DESC,event_id DESC LIMIT 101
                """));
        plans.put("actor", explain("""
                SELECT event_id,chain_id,sequence_number,recorded_at FROM audit_event
                WHERE tenant_id='plan' AND actor_id='actor-7'
                ORDER BY recorded_at DESC,chain_id DESC,sequence_number DESC,event_id DESC LIMIT 101
                """));
        plans.put("resource", explain("""
                SELECT event_id,chain_id,sequence_number,recorded_at FROM audit_event
                WHERE tenant_id='plan' AND resource_type='ACCOUNT' AND resource_id='resource-7'
                ORDER BY recorded_at DESC,chain_id DESC,sequence_number DESC,event_id DESC LIMIT 101
                """));
        plans.put("bounded-chain-range", explain("""
                SELECT sequence_number,previous_hash,content_hash,payload FROM audit_event
                WHERE tenant_id='plan' AND chain_id='tenant:plan'
                  AND sequence_number BETWEEN 9000 AND 9100
                ORDER BY sequence_number
                """));
        plans.put("full-chain-verification", explain("""
                SELECT sequence_number,previous_hash,content_hash,payload FROM audit_event
                WHERE tenant_id='plan' AND chain_id='tenant:plan' ORDER BY sequence_number
                """));

        assertThat(plans.get("tenant-cursor")).contains("ix_audit_event_tenant_cursor");
        assertThat(plans.get("actor")).contains("ix_audit_event_actor_recorded");
        assertThat(plans.get("resource")).contains("ix_audit_event_resource_recorded");
        assertThat(plans.get("bounded-chain-range"))
                .contains("uq_audit_event_chain_sequence");

        Path report = Path.of("target", "performance-baseline", "query-plans.txt");
        Files.createDirectories(report.getParent());
        StringBuilder text = new StringBuilder()
                .append("scope=synthetic-query-plan-baseline\n")
                .append("events=").append(EVENT_COUNT).append('\n')
                .append("tenants=1 chains=1 actors=100 resources=100 payload=small-json\n")
                .append("postgres=").append(POSTGRES.getDockerImageName()).append('\n')
                .append("runner_os=").append(System.getProperty("os.name")).append('\n')
                .append("java=").append(System.getProperty("java.version")).append("\n\n");
        plans.forEach((name, plan) -> text.append("== ").append(name).append(" ==\n")
                .append(plan).append("\n\n"));
        Files.writeString(report, text);
    }

    private String explain(String sql) {
        return String.join("\n", jdbc.queryForList(
                "EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS) " + sql, String.class));
    }
}
