package com.praveen.auditlog.persistence;

import java.time.Instant;

public interface ChainHeadRepository {

    ChainHead lockOrCreate(
            String chainId,
            String tenantId,
            byte[] genesisHash,
            Instant initializedAt
    );

    boolean advance(
            ChainHead expected,
            long nextSequence,
            byte[] nextHash,
            Instant updatedAt
    );

    record ChainHead(
            String chainId,
            String tenantId,
            long latestSequence,
            byte[] latestHash,
            long version
    ) {
        public ChainHead {
            latestHash = latestHash.clone();
        }

        @Override
        public byte[] latestHash() {
            return latestHash.clone();
        }
    }
}
