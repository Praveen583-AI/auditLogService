CREATE TABLE redaction_record (
    redaction_id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    event_id UUID NOT NULL,
    json_pointer VARCHAR(1024) NOT NULL,
    policy_id VARCHAR(128) NOT NULL,
    reason VARCHAR(1024) NOT NULL,
    authorized_by VARCHAR(256) NOT NULL,
    authorized_at TIMESTAMPTZ NOT NULL,
    replacement VARCHAR(128) NOT NULL DEFAULT '[REDACTED]',
    nonce BYTEA NOT NULL CHECK (octet_length(nonce) >= 16),
    original_value_commitment BYTEA NOT NULL CHECK (octet_length(original_value_commitment) = 32),
    commitment_algorithm VARCHAR(32) NOT NULL,
    commitment_key_id VARCHAR(128) NOT NULL,
    CONSTRAINT uq_redaction_action UNIQUE (tenant_id, event_id, json_pointer, policy_id)
);
CREATE INDEX ix_redaction_event ON redaction_record (tenant_id, event_id, authorized_at);

CREATE TABLE export_job (
    export_id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    selector_type VARCHAR(32) NOT NULL CHECK (selector_type IN ('ACTOR_ID','RESOURCE_ID')),
    selector_value VARCHAR(256) NOT NULL,
    requested_by VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('REQUESTED','RUNNING','COMPLETED','FAILED','EXPIRED','REVOKED')),
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    artifact_location TEXT,
    download_token_hash BYTEA,
    failure_code VARCHAR(64)
);
CREATE INDEX ix_export_tenant_status ON export_job (tenant_id, status, requested_at DESC);

CREATE TABLE export_access_action (
    action_id UUID PRIMARY KEY,
    export_id UUID NOT NULL REFERENCES export_job(export_id),
    tenant_id VARCHAR(128) NOT NULL,
    actor_id VARCHAR(256) NOT NULL,
    action_type VARCHAR(32) NOT NULL CHECK (action_type IN
      ('REQUESTED','COMPLETED','FAILED','DOWNLOADED','DOWNLOAD_DENIED')),
    outcome VARCHAR(32) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);

-- Redaction evidence is append-only. It deliberately has no event FK: retention may
-- move an event out of the hot table without deleting its redaction proof.
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_app') THEN
    GRANT SELECT ON redaction_record TO audit_app;
    GRANT SELECT, INSERT ON redaction_record TO audit_maintenance;
    GRANT SELECT ON redaction_record TO audit_verifier;
    REVOKE UPDATE, DELETE, TRUNCATE ON redaction_record FROM audit_app, audit_maintenance, audit_verifier;
    GRANT SELECT, INSERT, UPDATE ON export_job TO audit_maintenance;
    GRANT SELECT, INSERT ON export_access_action TO audit_maintenance;
    REVOKE UPDATE, DELETE, TRUNCATE ON export_access_action FROM audit_app, audit_maintenance, audit_verifier;
  END IF;
END $$;
