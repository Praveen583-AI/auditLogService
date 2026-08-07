# Performance and observability

## Measurement policy

No production latency or throughput target is confirmed. Results are baselines for the reported environment, not capacity claims. Every run must identify the commit, JDK, Maven and PostgreSQL versions, CPU and memory, storage, network placement, connection-pool size, event/payload distribution, tenant and chain counts, hot-chain skew, concurrency, cache state, sample count and background jobs.

Measure append latency for sequential, hot-chain and distributed-chain writes; filtered first/later search pages; valid and early/middle/late-failing long-chain verification; and bounded export generation. Report count, errors, median, p95, p99 and maximum only when the sample count supports them. Export results also report records and bytes. Verification reports records checked and time to first mismatch.

`scripts/performance/capture-baseline.sh` captures environment identifiers, dataset cardinality and `EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)` plans against an existing representative database. Cold- and warm-cache runs must be labeled separately. Do not run `EXPLAIN ANALYZE` for mutating statements against production.

CI also publishes `query-plan-baseline`, an explicitly synthetic PostgreSQL 16 artifact containing plans for 10,000 small events in one chain with 100 actors and 100 resources. It proves that current predicates can use the intended indexes; it is not a latency, throughput or production-sizing claim. The external script remains required before production tuning because real cardinality and skew can change planner choices.

The artifact records both a bounded chain range and a full-chain verification read. PostgreSQL may correctly prefer a sequential scan for the latter because every chain row is required; this is evidence of linear verification work, not justification to force an index scan.

## Index policy

The chain/sequence and idempotency indexes enforce correctness. Tenant cursor, actor/recorded-time and resource/recorded-time indexes match implemented keyset queries. Add no time-only, event-type or JSONB index until a representative plan shows excessive reads or sorting for a current query. Compare actual/estimated rows, rows filtered, buffer reads, sort spills and execution time before and after any candidate index, including append cost.

## Metrics

Prometheus metrics are exposed at `/actuator/prometheus`; health is exposed at `/actuator/health`. Health details are not public. Current instrumentation includes `audit.write.total`, `audit.write.duration`, `audit.idempotent.replay.total`, `audit.chain.lock.wait`, `audit.chain.lock.retry.total`, `audit.verification.total` and `audit.verification.duration`.

Metric labels are bounded outcomes and stable reason codes. Tenant, actor, resource, event, chain, job, idempotency and correlation identifiers are forbidden as metric labels. Future job instrumentation should add export/archive duration, outcome and queue lag with job type—not job ID—as a label.

Integrity failures, invalid archive signatures/checksums and sustained write failures require alerts. Latency, lock wait, retry rate, queue lag and export/archive failure thresholds remain baseline-relative until service objectives are confirmed.

## Logs and traces

Implemented write and verification logs propagate `correlationId` and use
event/chain/sequence identifiers without payload values. Export and archive
services do not yet implement distributed tracing or consistent structured job
logs. A production worker should propagate `jobId` and originating correlation
metadata into a new trace. None of these identifiers belongs in metric labels.

Never log payloads, actor claims or contact data, idempotency keys, request fingerprints, redacted/original values, commitments, tokens, credentials, signing keys or raw exported records. SQL parameters and stack traces stay out of public error responses. Traces contain operation names and timing, not request bodies or audit evidence.
