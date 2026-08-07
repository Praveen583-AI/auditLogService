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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class AuditWriteConcurrencyTest {

    private static final long ADVISORY_LOCK = 88442211L;
    private static final ThreadLocal<AuditRequestContext> REQUEST_CONTEXT =
            new ThreadLocal<>();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AuditWriteService writes;

    @Autowired
    private AuditQueryService queries;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuditRequestContextProvider contextProvider;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE idempotency_record, audit_event, chain_head CASCADE");
        given(contextProvider.currentContext()).willAnswer(
                ignored -> REQUEST_CONTEXT.get()
        );
    }

    @AfterEach
    void cleanUp() {
        jdbc.execute("DROP TRIGGER IF EXISTS block_chain_a ON chain_head");
        jdbc.execute("DROP FUNCTION IF EXISTS block_chain_a()");
        REQUEST_CONTEXT.remove();
    }

    @Test
    void simultaneousSameChainWritesHaveUniqueContiguousSequences() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<CreateAuditEventResult> first = executor.submit(
                    () -> appendAfterBarrier(
                            context("tenant-same"), "same-1", ready, start
                    )
            );
            Future<CreateAuditEventResult> second = executor.submit(
                    () -> appendAfterBarrier(
                            context("tenant-same"), "same-2", ready, start
                    )
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Long> returnedSequences = List.of(
                    first.get(10, TimeUnit.SECONDS).response().sequenceNumber(),
                    second.get(10, TimeUnit.SECONDS).response().sequenceNumber()
            );
            assertThat(returnedSequences).containsExactlyInAnyOrder(1L, 2L);

            List<EventLink> links = jdbc.query("""
                    SELECT sequence_number, previous_hash, content_hash
                    FROM audit_event
                    WHERE chain_id = 'tenant:tenant-same'
                    ORDER BY sequence_number
                    """, (row, ignored) -> new EventLink(
                    row.getLong("sequence_number"),
                    row.getBytes("previous_hash"),
                    row.getBytes("content_hash")
            ));
            assertThat(links).extracting(EventLink::sequence)
                    .containsExactly(1L, 2L);
            assertThat(links.get(1).previousHash())
                    .containsExactly(links.get(0).contentHash());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void blockedChainDoesNotBlockAnUnrelatedChain() throws Exception {
        installBlockingTrigger();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try (Connection lockConnection = dataSource.getConnection();
             Statement lockStatement = lockConnection.createStatement()) {
            lockStatement.execute(
                    "SELECT pg_advisory_lock(" + ADVISORY_LOCK + ")"
            );

            Future<CreateAuditEventResult> blocked = executor.submit(
                    () -> append(context("tenant-a"), "chain-a")
            );
            awaitAdvisoryWait();

            Future<CreateAuditEventResult> independent = executor.submit(
                    () -> append(context("tenant-b"), "chain-b")
            );
            CreateAuditEventResult independentResult =
                    independent.get(3, TimeUnit.SECONDS);

            assertThat(independentResult.response().chainId())
                    .isEqualTo("tenant:tenant-b");
            assertThat(independentResult.response().sequenceNumber()).isEqualTo(1);

            lockStatement.execute(
                    "SELECT pg_advisory_unlock(" + ADVISORY_LOCK + ")"
            );
            assertThat(blocked.get(5, TimeUnit.SECONDS)
                    .response().sequenceNumber()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void keysetPaginationReturnsEveryEventOnce() throws Exception {
        AuditRequestContext context = context("tenant-page");
        for (int index = 0; index < 5; index++) {
            append(context, "page-" + index);
        }

        AuditEventSpecification specification = new AuditEventSpecification(
                context.chainId(), null, null, null, null, null, null
        );
        List<AuditQueryService.AuditEventView> all = new ArrayList<>();
        String cursor = null;
        do {
            AuditQueryService.Page page = queries.search(
                    context.tenantId(), specification, 2, cursor
            );
            all.addAll(page.items());
            cursor = page.nextCursor();
        } while (cursor != null);

        assertThat(all).extracting(AuditQueryService.AuditEventView::sequenceNumber)
                .containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(new HashSet<>(all.stream()
                .map(AuditQueryService.AuditEventView::eventId).toList()))
                .hasSize(5);
    }

    private CreateAuditEventResult appendAfterBarrier(
            AuditRequestContext context,
            String key,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return append(context, key);
    }

    private CreateAuditEventResult append(
            AuditRequestContext context,
            String key
    ) throws Exception {
        REQUEST_CONTEXT.set(context);
        try {
            return writes.create(key, request());
        } finally {
            REQUEST_CONTEXT.remove();
        }
    }

    private void installBlockingTrigger() {
        jdbc.execute("""
                CREATE FUNCTION block_chain_a()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.chain_id = 'tenant:tenant-a' THEN
                        PERFORM pg_advisory_xact_lock(88442211);
                    END IF;
                    RETURN NEW;
                END
                $$
                """);
        jdbc.execute("""
                CREATE TRIGGER block_chain_a
                BEFORE UPDATE ON chain_head
                FOR EACH ROW EXECUTE FUNCTION block_chain_a()
                """);
    }

    private void awaitAdvisoryWait() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE wait_event_type = 'Lock'
                      AND wait_event = 'advisory'
                    """, Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Chain A never reached the controlled lock");
    }

    private AuditRequestContext context(String tenantId) {
        return new AuditRequestContext(
                tenantId, "producer-1", "actor-1",
                "USER", "PRODUCER_ASSERTED"
        );
    }

    private CreateAuditEventRequest request() throws Exception {
        return new CreateAuditEventRequest(
                "ACCOUNT_UPDATED",
                1,
                Instant.parse("2026-08-07T14:30:12.123Z"),
                new ActorDto("actor-1", "USER"),
                new ResourceDto("ACCOUNT", "account-1"),
                objectMapper.readTree("{\"result\":\"accepted\"}")
        );
    }

    private record EventLink(
            long sequence,
            byte[] previousHash,
            byte[] contentHash
    ) {
    }
}
