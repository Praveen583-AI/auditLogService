-- Runtime roles are NOLOGIN group roles. Production login/workload roles
-- are provisioned outside Flyway and granted membership without committing
-- credentials to source control.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_app') THEN
        CREATE ROLE audit_app NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_verifier') THEN
        CREATE ROLE audit_verifier NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_maintenance') THEN
        CREATE ROLE audit_maintenance NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
END
$$;

REVOKE ALL ON TABLE audit_event, chain_head, idempotency_record FROM PUBLIC;

GRANT USAGE ON SCHEMA public TO audit_app, audit_verifier, audit_maintenance;

-- Normal synchronous application write/query path.
GRANT SELECT, INSERT ON TABLE audit_event TO audit_app;
GRANT SELECT, INSERT, UPDATE ON TABLE chain_head TO audit_app;
GRANT SELECT, INSERT, UPDATE ON TABLE idempotency_record TO audit_app;
REVOKE UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER
    ON TABLE audit_event FROM audit_app;
REVOKE DELETE, TRUNCATE, REFERENCES, TRIGGER
    ON TABLE chain_head, idempotency_record FROM audit_app;

-- Verification is deliberately read-only.
GRANT SELECT ON TABLE audit_event, chain_head TO audit_verifier;

-- Retention/archive maintenance remains separate from the public runtime role.
-- The maintenance workflow must first write and verify its immutable archive
-- manifest, then perform any approved relational cleanup transaction.
GRANT SELECT, DELETE ON TABLE audit_event, idempotency_record
    TO audit_maintenance;
GRANT SELECT, UPDATE ON TABLE chain_head TO audit_maintenance;

COMMENT ON ROLE audit_app IS
    'NOLOGIN group role for normal audit API reads and atomic appends; cannot modify or delete audit events.';
COMMENT ON ROLE audit_verifier IS
    'NOLOGIN group role for read-only chain verification.';
COMMENT ON ROLE audit_maintenance IS
    'NOLOGIN privileged group role for approved retention/archive workflows; never used by public API requests.';
