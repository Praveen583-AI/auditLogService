DROP INDEX IF EXISTS ix_audit_event_actor_time;
DROP INDEX IF EXISTS ix_audit_event_resource_time;

CREATE INDEX ix_audit_event_tenant_cursor
    ON audit_event (
        tenant_id,
        recorded_at DESC,
        chain_id DESC,
        sequence_number DESC,
        event_id DESC
    );

CREATE INDEX ix_audit_event_actor_recorded
    ON audit_event (
        tenant_id,
        actor_id,
        recorded_at DESC,
        chain_id DESC,
        sequence_number DESC,
        event_id DESC
    );

CREATE INDEX ix_audit_event_resource_recorded
    ON audit_event (
        tenant_id,
        resource_type,
        resource_id,
        recorded_at DESC,
        chain_id DESC,
        sequence_number DESC,
        event_id DESC
    );
