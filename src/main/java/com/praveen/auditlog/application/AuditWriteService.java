package com.praveen.auditlog.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.transaction.TransactionSystemException;

@Service
public class AuditWriteService implements CreateAuditEventUseCase {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AuditWriteService.class);

    private final TransactionalAuditAppender appender;
    private final RetryPolicy retryPolicy;
    private final OperationalMetrics metrics;

    public AuditWriteService(
            TransactionalAuditAppender appender,
            RetryPolicy retryPolicy
    ) {
        this(appender, retryPolicy, new OperationalMetrics(new SimpleMeterRegistry()));
    }

    @Autowired
    public AuditWriteService(
            TransactionalAuditAppender appender,
            RetryPolicy retryPolicy,
            OperationalMetrics metrics
    ) {
        this.appender = appender;
        this.retryPolicy = retryPolicy;
        this.metrics = metrics;
    }

    @Override
    public CreateAuditEventResult create(
            String idempotencyKey,
            com.praveen.auditlog.api.dto.CreateAuditEventRequest request
    ) {
        long operationStarted = metrics.start();
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            long attemptStarted = metrics.start();
            try {
                CreateAuditEventResult result =
                        appender.append(idempotencyKey, request);
                LOGGER.info(
                        "audit_append_completed correlationId={} eventId={} chainId={} sequenceNumber={} replayed={}",
                        OperationalLogContext.correlationId(),
                        result.response().eventId(),
                        result.response().chainId(),
                        result.response().sequenceNumber(),
                        result.replayed()
                );
                metrics.write(operationStarted, "SUCCESS", result.replayed());
                return result;
            } catch (RuntimeException failure) {
                if (isChainLockTimeout(failure)) {
                    metrics.lockWait(attemptStarted, "TIMEOUT");
                    if (attempt == retryPolicy.maxAttempts()) {
                        metrics.write(operationStarted, "CHAIN_BUSY", false);
                        throw new ChainBusyException(failure);
                    }
                    LOGGER.warn(
                            "audit_append_retry correlationId={} reason=CHAIN_LOCK_TIMEOUT attempt={} maxAttempts={}",
                            OperationalLogContext.correlationId(), attempt,
                            retryPolicy.maxAttempts()
                    );
                    metrics.retry("CHAIN_LOCK_TIMEOUT");
                    retryPolicy.backoff(attempt);
                    continue;
                }
                if (isConnectionFailure(failure)) {
                    metrics.write(operationStarted, "DATABASE_FAILURE", false);
                    throw new TemporaryDatabaseFailureException(failure);
                }
                metrics.write(operationStarted, "FAILURE", false);
                throw failure;
            }
        }
        throw new IllegalStateException("Unreachable retry state");
    }

    private boolean isChainLockTimeout(Throwable failure) {
        return failure instanceof CannotAcquireLockException
                || hasSqlState(failure, "55P03", false);
    }

    private boolean isConnectionFailure(Throwable failure) {
        return failure instanceof DataAccessResourceFailureException
                || failure instanceof TransientDataAccessResourceException
                || hasSqlState(failure, "08", true);
    }

    private boolean hasSqlState(
            Throwable failure,
            String expected,
            boolean prefix
    ) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 20; depth++) {
            if (current instanceof TransactionSystemException transactionFailure) {
                Throwable original = transactionFailure.getOriginalException();
                if (original != null
                        && original != transactionFailure.getCause()
                        && hasSqlState(original, expected, prefix)) {
                    return true;
                }
            }
            if (current instanceof java.sql.SQLException sqlException) {
                java.sql.SQLException candidate = sqlException;
                while (candidate != null) {
                    String state = candidate.getSQLState();
                    if (state != null && (prefix
                            ? state.startsWith(expected)
                            : state.equals(expected))) {
                        return true;
                    }
                    candidate = candidate.getNextException();
                }
            }
            current = current.getCause();
        }
        return false;
    }

}
