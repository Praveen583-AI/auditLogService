
CREATE TABLE archive_manifest (
    manifest_id uuid PRIMARY KEY,
    manifest_version integer NOT NULL CHECK (manifest_version >= 1),
    tenant_id text NOT NULL,
    chain_id text NOT NULL,
    start_sequence bigint NOT NULL CHECK (start_sequence >= 1),
    end_sequence bigint NOT NULL,
    record_count bigint NOT NULL,
    predecessor_hash bytea NOT NULL CHECK (octet_length(predecessor_hash) = 32),
    first_event_hash bytea NOT NULL CHECK (octet_length(first_event_hash) = 32),
    last_event_hash bytea NOT NULL CHECK (octet_length(last_event_hash) = 32),
    bundle_checksum bytea NOT NULL CHECK (octet_length(bundle_checksum) = 32),
    checksum_algorithm varchar(32) NOT NULL,
    bundle_format_version integer NOT NULL CHECK (bundle_format_version >= 1),
    policy_id varchar(255) NOT NULL,
    archived_at timestamptz NOT NULL,
    storage_location text NOT NULL,
    storage_version text NOT NULL,
    signature_algorithm varchar(64) NOT NULL,
    signing_key_id varchar(255) NOT NULL,
    signature_version integer NOT NULL CHECK (signature_version >= 1),
    signed_at timestamptz NOT NULL,
    signature bytea NOT NULL CHECK (octet_length(signature) > 0),
    CONSTRAINT ck_archive_manifest_range CHECK (
        end_sequence >= start_sequence
        AND record_count = end_sequence - start_sequence + 1
    ),
    CONSTRAINT uq_archive_manifest_range UNIQUE (chain_id, start_sequence, end_sequence),
    CONSTRAINT uq_archive_manifest_object UNIQUE (storage_location, storage_version),
    CONSTRAINT fk_archive_manifest_chain FOREIGN KEY (chain_id, tenant_id)
        REFERENCES chain_head (chain_id, tenant_id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE INDEX ix_archive_manifest_chain_start
    ON archive_manifest (tenant_id, chain_id, start_sequence);

CREATE TABLE archive_lifecycle_action (
    action_id uuid PRIMARY KEY,
    manifest_id uuid NOT NULL,
    action_type varchar(40) NOT NULL,
    recorded_at timestamptz NOT NULL,
    correlation_id varchar(255),
    details jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_archive_action_type CHECK (action_type IN (
        'SELECTED', 'STORED', 'VERIFIED', 'MANIFEST_PUBLISHED',
        'HOT_DATA_REMOVED', 'STORED_COPY_INVALID', 'FAILED'
    ))
);

CREATE INDEX ix_archive_action_manifest_time
    ON archive_lifecycle_action (manifest_id, recorded_at, action_id);

CREATE TABLE legal_hold_action (
    action_id uuid PRIMARY KEY,
    tenant_id text NOT NULL,
    chain_id text NOT NULL,
    hold_id uuid NOT NULL,
    action_type varchar(20) NOT NULL,
    start_sequence bigint NOT NULL CHECK (start_sequence >= 1),
    end_sequence bigint,
    reason_reference varchar(255) NOT NULL,
    authorized_by varchar(255) NOT NULL,
    recorded_at timestamptz NOT NULL,
    previous_action_hash bytea NOT NULL CHECK (octet_length(previous_action_hash) = 32),
    content_hash bytea NOT NULL CHECK (octet_length(content_hash) = 32),
    CONSTRAINT ck_legal_hold_action CHECK (action_type IN ('PLACED', 'EXTENDED', 'RELEASED')),
    CONSTRAINT ck_legal_hold_range CHECK (end_sequence IS NULL OR end_sequence >= start_sequence),
    CONSTRAINT uq_legal_hold_action_hash UNIQUE (content_hash),
    CONSTRAINT fk_legal_hold_chain FOREIGN KEY (chain_id, tenant_id)
        REFERENCES chain_head (chain_id, tenant_id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE INDEX ix_legal_hold_effective_scope
    ON legal_hold_action (tenant_id, chain_id, hold_id, recorded_at DESC, action_id DESC);

COMMENT ON TABLE archive_manifest IS 'Immutable signed evidence for one contiguous archived chain range.';
COMMENT ON TABLE archive_lifecycle_action IS 'Append-only operational history of each archive transition.';
COMMENT ON TABLE legal_hold_action IS 'Append-only legal-hold actions; the latest action determines effective state.';

REVOKE ALL ON TABLE archive_manifest, archive_lifecycle_action,
    legal_hold_action FROM PUBLIC;
GRANT SELECT ON TABLE archive_manifest TO audit_app, audit_verifier,
    audit_maintenance;
GRANT SELECT, INSERT ON TABLE archive_lifecycle_action,
    legal_hold_action TO audit_maintenance;
GRANT INSERT ON TABLE archive_manifest TO audit_maintenance;
REVOKE UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER
    ON TABLE archive_manifest, archive_lifecycle_action,
    legal_hold_action FROM audit_app, audit_verifier, audit_maintenance;

