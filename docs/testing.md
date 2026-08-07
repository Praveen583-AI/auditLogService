# Test strategy and execution

## Required environment

- Java 17
- Maven 3.9 or later
- A Docker-compatible runtime for Testcontainers PostgreSQL

No externally provisioned database is required. Integration tests start PostgreSQL 16 and apply Flyway migrations to a clean database.

## Checkpoint commands

Run the complete suite:

```sh
mvn --batch-mode --no-transfer-progress test
```

Exercise the synchronized concurrency contract repeatedly:

```sh
for run in 1 2 3; do
  mvn --batch-mode --no-transfer-progress -Dtest=ConcurrentWriteIntegrationTest test
done
```

Run the archive and tamper demonstration from clean fixture state:

```sh
bash scripts/demo-tampering.sh
```

## Observable invariants

The same-chain test releases eight writers through one start gate and asserts unique event identifiers, one idempotency record per request, sequence numbers exactly `1..8`, correct hash linkage and a valid final chain. It does not infer ordering from thread completion order.

The different-chain test deliberately blocks chain A at a database lock, confirms the request is waiting, and proves chain B completes before chain A is released.

The tamper demonstration archives sequences 1–2 from a five-event chain and first verifies that legitimate movement remains valid. It then changes event 3 directly and expects `CONTENT_HASH_MISMATCH`, restores the original value, deletes event 4 and expects `SEQUENCE_GAP`. Direct mutation uses the integration-test database owner; runtime-role tests separately prove the application role cannot update or delete audit events.

## CI evidence

The `Java CI` workflow runs the full suite, repeats the concurrency class three times and runs the tamper script. A checkpoint is complete only when all three jobs steps pass on the same commit. Surefire reports remain the authoritative test counts and failure details.
