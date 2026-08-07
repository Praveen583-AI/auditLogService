package com.praveen.auditlog.application;

import com.praveen.auditlog.api.dto.AuditEventResponse;
import com.praveen.auditlog.api.dto.CreateAuditEventRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class AuditWriteFailureMappingTest {

    private final TransactionalAuditAppender appender =
            mock(TransactionalAuditAppender.class);
    private final RetryPolicy retryPolicy =
            mock(RetryPolicy.class);
    private final AuditWriteService service =
            new AuditWriteService(appender, retryPolicy);

    @Test
    void lockTimeoutRetriesWithANewTransactionalCall() {
        given(retryPolicy.maxAttempts()).willReturn(3);
        given(appender.append(eq("key"), nullable(CreateAuditEventRequest.class)))
                .willThrow(new CannotAcquireLockException("lock timeout"))
                .willReturn(result());

        CreateAuditEventResult actual = service.create("key", null);

        assertThat(actual).isEqualTo(result());
        verify(appender, times(2)).append("key", null);
        verify(retryPolicy).backoff(1);
    }

    @Test
    void exhaustedLockTimeoutMapsToChainBusy() {
        given(retryPolicy.maxAttempts()).willReturn(3);
        given(appender.append(eq("key"), nullable(CreateAuditEventRequest.class)))
                .willThrow(new CannotAcquireLockException("lock timeout"));

        assertThatThrownBy(() -> service.create("key", null))
                .isInstanceOf(ChainBusyException.class);

        verify(appender, times(3)).append("key", null);
        verify(retryPolicy).backoff(1);
        verify(retryPolicy).backoff(2);
    }

    @Test
    void connectionFailureIsNotRetriedWhenCommitOutcomeMayBeUnknown() {
        given(retryPolicy.maxAttempts()).willReturn(3);
        given(appender.append(eq("key"), nullable(CreateAuditEventRequest.class)))
                .willThrow(new DataAccessResourceFailureException(
                        "host=db-secret password=secret"
                ));

        assertThatThrownBy(() -> service.create("key", null))
                .isInstanceOf(TemporaryDatabaseFailureException.class)
                .hasMessageNotContaining("db-secret")
                .hasMessageNotContaining("password");

        verify(appender).append("key", null);
        verify(retryPolicy).maxAttempts();
        verify(retryPolicy, never()).backoff(org.mockito.ArgumentMatchers.anyInt());
    }

    private CreateAuditEventResult result() {
        return new CreateAuditEventResult(
                new AuditEventResponse(
                        UUID.fromString(
                                "00000000-0000-0000-0000-000000000001"
                        ),
                        "tenant:tenant-1",
                        1,
                        Instant.parse("2026-08-07T14:30:12.456789Z"),
                        "a".repeat(64),
                        "SHA-256",
                        1
                ),
                false
        );
    }
}
