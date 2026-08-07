package com.praveen.auditlog.persistence.jdbc;

import com.praveen.auditlog.persistence.ChainHeadRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class JdbcChainHeadRepository implements ChainHeadRepository {

    private final JdbcTemplate jdbc;

    public JdbcChainHeadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ChainHead lockOrCreate(
            String chainId,
            String tenantId,
            byte[] genesisHash,
            Instant initializedAt
    ) {
        jdbc.update("""
                INSERT INTO chain_head (
                    chain_id, tenant_id, latest_sequence, latest_hash,
                    version, updated_at
                ) VALUES (?, ?, 0, ?, 0, ?)
                ON CONFLICT DO NOTHING
                """, chainId, tenantId, genesisHash, Timestamp.from(initializedAt));

        return jdbc.queryForObject("""
                SELECT chain_id, tenant_id, latest_sequence, latest_hash, version
                FROM chain_head
                WHERE chain_id = ?
                  AND tenant_id = ?
                FOR UPDATE
                """, (row, ignored) -> new ChainHead(
                row.getString("chain_id"),
                row.getString("tenant_id"),
                row.getLong("latest_sequence"),
                row.getBytes("latest_hash"),
                row.getLong("version")
        ), chainId, tenantId);
    }

    @Override
    public boolean advance(
            ChainHead expected,
            long nextSequence,
            byte[] nextHash,
            Instant updatedAt
    ) {
        int rows = jdbc.update("""
                UPDATE chain_head
                SET latest_sequence = ?,
                    latest_hash = ?,
                    version = version + 1,
                    updated_at = ?
                WHERE chain_id = ?
                  AND tenant_id = ?
                  AND latest_sequence = ?
                  AND latest_hash = ?
                  AND version = ?
                """,
                nextSequence, nextHash, Timestamp.from(updatedAt), expected.chainId(),
                expected.tenantId(), expected.latestSequence(),
                expected.latestHash(), expected.version()
        );
        return rows == 1;
    }
}
