package com.praveen.auditlog.persistence;

import com.praveen.auditlog.integrity.CanonicalAuditEvent;

public interface ChainVerificationRepository {

    ChainBoundary scan(
            String tenantId,
            String chainId,
            EventVisitor visitor
    );

    @FunctionalInterface
    interface EventVisitor {
        boolean visit(StoredEvent event);
    }

    record StoredEvent(CanonicalAuditEvent event, byte[] contentHash) {
        public StoredEvent {
            contentHash = contentHash.clone();
        }

        @Override
        public byte[] contentHash() {
            return contentHash.clone();
        }
    }

    record ChainBoundary(
            boolean exists,
            long latestSequence,
            byte[] latestHash
    ) {
        public ChainBoundary {
            latestHash = latestHash.clone();
        }

        @Override
        public byte[] latestHash() {
            return latestHash.clone();
        }

        public static ChainBoundary missing() {
            return new ChainBoundary(false, 0, new byte[32]);
        }
    }
}
