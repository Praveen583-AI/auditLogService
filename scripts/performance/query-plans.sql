\set ON_ERROR_STOP on
\timing on

ANALYZE audit_event;

SELECT current_database() AS database,
       current_setting('server_version') AS postgres_version,
       count(*) AS event_count,
       count(DISTINCT tenant_id) AS tenant_count,
       count(DISTINCT chain_id) AS chain_count
FROM audit_event;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT event_id, chain_id, sequence_number, recorded_at
FROM audit_event
WHERE tenant_id = :'tenant_id'
ORDER BY recorded_at DESC, chain_id DESC, sequence_number DESC, event_id DESC
LIMIT 101;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT event_id, chain_id, sequence_number, recorded_at
FROM audit_event
WHERE tenant_id = :'tenant_id' AND actor_id = :'actor_id'
ORDER BY recorded_at DESC, chain_id DESC, sequence_number DESC, event_id DESC
LIMIT 101;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT event_id, chain_id, sequence_number, recorded_at
FROM audit_event
WHERE tenant_id = :'tenant_id'
  AND resource_type = :'resource_type' AND resource_id = :'resource_id'
ORDER BY recorded_at DESC, chain_id DESC, sequence_number DESC, event_id DESC
LIMIT 101;

EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT sequence_number, previous_hash, content_hash, payload
FROM audit_event
WHERE tenant_id = :'tenant_id' AND chain_id = :'chain_id'
ORDER BY sequence_number;
