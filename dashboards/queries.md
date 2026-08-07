# Dashboard and alert query notes

PromQL examples assume a five-minute rate window. Tune windows and thresholds from captured baselines.

```promql
sum by (outcome) (rate(audit_write_total[5m]))
histogram_quantile(0.95, sum by (le) (rate(audit_write_duration_seconds_bucket[5m])))
histogram_quantile(0.99, sum by (le) (rate(audit_chain_lock_wait_seconds_bucket[5m])))
sum by (reason) (rate(audit_chain_lock_retry_total[5m]))
sum by (status, reason) (rate(audit_verification_total[5m]))
```

Page immediately on any integrity reason (`CONTENT_HASH_MISMATCH`, `PREVIOUS_HASH_MISMATCH`, `SEQUENCE_GAP`, `ARCHIVE_CHECKSUM_MISMATCH`, `ARCHIVE_SIGNATURE_INVALID`). Alert on sustained non-success writes or exhausted lock retries. Use baseline-relative warnings for p95/p99 latency and lock waits until an owner approves service objectives.

Dashboard panels should show write rate/outcome, write p50/p95/p99, lock wait/retries, replay rate, verification duration/outcome and JVM/database-pool health. Future job panels add queue lag, archive failure and export duration after those asynchronous paths expose metrics.
