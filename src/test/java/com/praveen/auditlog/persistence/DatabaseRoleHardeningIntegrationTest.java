package com.praveen.auditlog.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.auditlog.api.dto.ActorDto;
import com.praveen.auditlog.api.dto.CreateAuditEventRequest;
import com.praveen.auditlog.api.dto.ResourceDto;
import com.praveen.auditlog.application.AuditRequestContext;
import com.praveen.auditlog.application.AuditRequestContextProvider;
import com.praveen.auditlog.application.AuditWriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;

@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class DatabaseRoleHardeningIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AuditWriteService writes;

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
                        "role-test-tenant", "producer-1", "actor-1",
                        "USER", "TEST"
                )
        );
        writes.create("role-test-key", new CreateAuditEventRequest(
                "ACCOUNT_UPDATED",
                1,
                Instant.parse("2026-08-07T14:30:12.123Z"),
                new ActorDto("ignored-actor", "ADMIN"),
                new ResourceDto("ACCOUNT", "account-1"),
                objectMapper.readTree("{\"result\":\"accepted\"}")
        ));
    }

    @Test
    void applicationRoleCannotUpdateOrDeleteAuditEvents() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET ROLE audit_app");

                SQLException updateFailure = catchThrowableOfType(
                        SQLException.class,
                        () -> statement.executeUpdate("""
                                UPDATE audit_event
                                SET event_type = 'TAMPERED'
                                WHERE chain_id = 'tenant:role-test-tenant'
                                """)
                );
                assertThat(updateFailure.getSQLState()).isEqualTo("42501");

                SQLException deleteFailure = catchThrowableOfType(
                        SQLException.class,
                        () -> statement.executeUpdate("""
                                DELETE FROM audit_event
                                WHERE chain_id = 'tenant:role-test-tenant'
                                """)
                );
                assertThat(deleteFailure.getSQLState()).isEqualTo("42501");

                statement.execute("RESET ROLE");
            }
            return null;
        });

        assertThat(jdbc.queryForObject(
                "SELECT event_type FROM audit_event WHERE chain_id = ?",
                String.class,
                "tenant:role-test-tenant"
        )).isEqualTo("ACCOUNT_UPDATED");
    }
}
