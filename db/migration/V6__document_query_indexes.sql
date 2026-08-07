-- These indexes correspond to the implemented keyset-search predicates.
-- V2 created them after removing older occurred_at indexes. Re-declare no
-- speculative indexes here; attach intent so production plan reviews can
-- distinguish correctness/query indexes from candidates.
COMMENT ON INDEX uq_audit_event_chain_sequence IS
    'Enforces per-chain sequence uniqueness and supports ordered verification';
COMMENT ON INDEX uq_idempotency_scope IS
    'Enforces producer-scoped idempotency request uniqueness';
COMMENT ON INDEX ix_audit_event_tenant_cursor IS
    'Supports tenant-scoped cross-chain recorded_at keyset pagination';
COMMENT ON INDEX ix_audit_event_actor_recorded IS
    'Supports tenant actor and recorded_at keyset search';
COMMENT ON INDEX ix_audit_event_resource_recorded IS
    'Supports tenant resource and recorded_at keyset search';
