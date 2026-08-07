package com.praveen.auditlog.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Service;

@Service
public class AuditWriteService implements CreateAuditEventUseCase {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AuditWriteService.class);

    private final TransactionalAuditAppender appender;
    private final AppendRetryPolicy retryPolicy;

    public AuditWriteService(
            TransactionalAuditAppender appender,
            AppendRetryPolicy retryPolicy
    ) {
        this.appender = appender;
        this.retryPolicy = retryPolicy;
    }

    @Override
    public CreateAuditEventResult create(
            String idempotencyKey,
            com.praveen.auditlog.api.dto.CreateAuditEventRequest request
    ) {
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
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
                return result;
            } catch (CannotAcquireLockException lockTimeout) {
                if (attempt == retryPolicy.maxAttempts()) {
                    throw new ChainBusyException(lockTimeout);
                }
                LOGGER.warn(
                        "audit_append_retry correlationId={} reason=CHAIN_LOCK_TIMEOUT attempt={} maxAttempts={}",
                        OperationalLogContext.correlationId(), attempt,
                        retryPolicy.maxAttempts()
                );
                retryPolicy.backoff(attempt);
            } catch (DataAccessResourceFailureException
                     | TransientDataAccessResourceException connectionFailure) {
                throw new TemporaryDatabaseFailureException(connectionFailure);
            }
        }
        throw new IllegalStateException("Unreachable retry state");
    }
}
