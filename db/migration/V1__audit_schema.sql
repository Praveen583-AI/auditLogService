-- Initial PostgreSQL schema for atomic, per-tenant audit-chain appends.
-- Database roles and retention-only delete privileges are deployment concerns
-- and must be configured before treating the tables as operationally append-only.

CREATE TABLE chain_head (
    chain_id                   text        PRIMARY KEY,
    tenant_id                  text        NOT NULL UNIQUE,
    latest_sequence            bigint      NOT NULL DEFAULT 0,
    latest_hash                bytea       NOT NULL,
    version                    bigint      NOT NULL DEFAULT 0,
    updated_at                 timestamptz NOT NULL,

    CONSTRAINT uq_chain_head_chain_tenant
        UNIQUE (chain_id, tenant_id),
    CONSTRAINT ck_chain_head_sequence
        CHECK (latest_sequence >= 0),
    CONSTRAINT ck_chain_head_version
        CHECK (version >= 0),
    CONSTRAINT ck_chain_head_hash_length
        CHECK (octet_length(latest_hash) = 32)
);

CREATE TABLE audit_event (
    event_id                   uuid         PRIMARY KEY,
    chain_id                   text         NOT NULL,
    tenant_id                  text         NOT NULL,
    sequence_number            bigint       NOT NULL,
    event_type                 varchar(100) NOT NULL,
    event_schema_version       integer      NOT NULL,
    producer_id                varchar(255) NOT NULL,
    actor_id                   varchar(255) NOT NULL,
    actor_type                 varchar(50)  NOT NULL,
    actor_identity_source      varchar(50)  NOT NULL,
    resource_type              varchar(100) NOT NULL,
    resource_id                varchar(255) NOT NULL,
    occurred_at                timestamptz  NOT NULL,
    recorded_at                timestamptz  NOT NULL,
    payload                    jsonb        NOT NULL,
    previous_hash              bytea        NOT NULL,
    content_hash               bytea        NOT NULL,
    hash_algorithm             varchar(32)  NOT NULL,
    canonicalization_version   integer      NOT NULL,

    CONSTRAINT fk_audit_event_chain_tenant
        FOREIGN KEY (chain_id, tenant_id)
        REFERENCES chain_head (chain_id, tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uq_audit_event_chain_sequence
        UNIQUE (chain_id, sequence_number),
    CONSTRAINT ck_audit_event_sequence
        CHECK (sequence_number >= 1),
    CONSTRAINT ck_audit_event_schema_version
        CHECK (event_schema_version >= 1),
    CONSTRAINT ck_audit_event_canonicalization_version
        CHECK (canonicalization_version >= 1),
    CONSTRAINT ck_audit_event_payload_object
        CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_audit_event_previous_hash_length
        CHECK (octet_length(previous_hash) = 32),
    CONSTRAINT ck_audit_event_content_hash_length
        CHECK (octet_length(content_hash) = 32)
);

CREATE INDEX ix_audit_event_actor_time
    ON audit_event (tenant_id, actor_id, occurred_at DESC, event_id);

CREATE INDEX ix_audit_event_resource_time
    ON audit_event (
        tenant_id,
        resource_type,
        resource_id,
        occurred_at DESC,
        event_id
    );

CREATE TABLE idempotency_record (
    idempotency_id             uuid         PRIMARY KEY,
    tenant_id                  text         NOT NULL,
    producer_id                varchar(255) NOT NULL,
    operation                  varchar(100) NOT NULL,
    idempotency_key_hash       bytea        NOT NULL,
    request_fingerprint        bytea        NOT NULL,
    status                     varchar(20)  NOT NULL,
    event_id                   uuid,
    response_json              jsonb,
    response_status            integer,
    created_at                 timestamptz  NOT NULL,
    updated_at                 timestamptz  NOT NULL,
    expires_at                 timestamptz,

    CONSTRAINT uq_idempotency_scope
        UNIQUE (
            tenant_id,
            producer_id,
            operation,
            idempotency_key_hash
        ),
    CONSTRAINT fk_idempotency_event
        FOREIGN KEY (event_id) REFERENCES audit_event (event_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_idempotency_status
        CHECK (status IN ('PROCESSING', 'COMPLETED')),
    CONSTRAINT ck_idempotency_key_hash_present
        CHECK (octet_length(idempotency_key_hash) > 0),
    CONSTRAINT ck_idempotency_request_fingerprint_present
        CHECK (octet_length(request_fingerprint) > 0),
    CONSTRAINT ck_idempotency_completed_result
        CHECK (
            (
                status = 'PROCESSING'
                AND event_id IS NULL
                AND response_json IS NULL
                AND response_status IS NULL
            )
            OR
            (
                status = 'COMPLETED'
                AND event_id IS NOT NULL
                AND response_json IS NOT NULL
                AND response_status BETWEEN 200 AND 299
            )
        )
);

CREATE INDEX ix_idempotency_expiry
    ON idempotency_record (expires_at)
    WHERE expires_at IS NOT NULL;

COMMENT ON TABLE audit_event IS
    'Immutable audit evidence. Application roles must have no UPDATE or DELETE privilege.';

COMMENT ON TABLE chain_head IS
    'Operational append coordinator; updated atomically with audit_event insertion.';

COMMENT ON TABLE idempotency_record IS
    'Request-processing state committed atomically with event and chain-head changes.';
