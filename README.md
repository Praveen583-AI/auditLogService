# Audit Log Service

## Problem

Regulated financial systems need evidence of who did what, to which resource,
and when. Ordinary application logs are insufficient when records can be
silently changed, deleted, reordered, or accessed without accountability.

## Solution summary

This Spring Boot and PostgreSQL prototype appends each tenant's audit events to
an ordered SHA-256 hash chain. It provides atomic idempotent writes,
deterministic search, and streaming verification that reports the first invalid
sequence after modification, deletion, or reordering. Trusted actor and tenant
context are supplied by authentication rather than caller-controlled tenant
fields.

Hash chaining provides tamper evidence, not absolute prevention. The precise
implementation boundary is recorded in [scope](docs/scope.md).

## Implemented scenarios

| Assignment scenario | Prototype status |
| --- | --- |
| Scenario A — Core Audit Log Service | Public HTTP endpoints implement append, filtered query with opaque cursor pagination, and chain verification. Atomicity, idempotency, deterministic hashing, authorization, and per-chain concurrency are tested. |
| Scenario B — Retention, Redaction, and Bulk Export | Contiguous archival with signed manifests, legal holds, redaction overlays, and signed export bundles are implemented and tested as application services. Production internal HTTP controllers and durable object storage are not implemented. |
| Scenario C — Compliance Reporting | Requirements, open questions, and a partial design boundary are documented. Regulator identity, report population, and the external access contract remain unresolved; regulator-ready access is not claimed. |

See [acceptance criteria](docs/acceptance-criteria.md) and
[requirement-to-test traceability](docs/test-traceability.md) for observable
evidence.

## Five-minute quick start

### Prerequisites

For the Compose path, install:

- Git.
- Docker Desktop, or Docker Engine with Docker Compose v2.
- `curl` (included with current Windows, macOS, and most Linux environments).

Confirm the tools before cloning:

```sh
git --version
docker --version
docker compose version
curl --version
```

The commands below use POSIX shell quoting and line continuation. On Windows,
run them in Git Bash or WSL so the JSON bodies reach `curl` unchanged.

Java and Maven are not required for the Compose path. The container build uses
Java 17 because [pom.xml](pom.xml) targets Java 17; Java 21 is not a repository
requirement. A native build requires JDK 17 and Maven 3.9 or newer.

### Start and verify health

```sh
git clone https://github.com/Praveen583-AI/auditLogService.git
cd auditLogService
docker compose up --build --detach
docker compose ps
curl --fail http://localhost:8080/actuator/health
```

The health response should contain `"status":"UP"`. The Compose environment
uses a synthetic local identity and disables JWT validation only through the
explicit `local` profile. Its database password is local demonstration data,
not a production secret.

### Create two synthetic events

```sh
curl --include --request POST http://localhost:8080/v1/audit/events \
  --header "Content-Type: application/json" \
  --header "Idempotency-Key: reviewer-event-001" \
  --data '{
    "eventType":"ACCOUNT_PREFERENCE_CHANGED",
    "eventSchemaVersion":1,
    "occurredAt":"2026-08-07T14:30:00Z",
    "actor":{"id":"synthetic-caller","type":"SYSTEM"},
    "resource":{"type":"DEMO_ACCOUNT","id":"demo-account-001"},
    "payload":{"changedField":"statementDelivery","newSetting":"DIGITAL"}
  }'

curl --include --request POST http://localhost:8080/v1/audit/events \
  --header "Content-Type: application/json" \
  --header "Idempotency-Key: reviewer-event-002" \
  --data '{
    "eventType":"DOCUMENT_VIEWED",
    "eventSchemaVersion":1,
    "occurredAt":"2026-08-07T14:31:00Z",
    "actor":{"id":"synthetic-caller","type":"SYSTEM"},
    "resource":{"type":"DEMO_ACCOUNT","id":"demo-account-001"},
    "payload":{"documentType":"SAMPLE_STATEMENT","outcome":"DISPLAYED"}
  }'
```

The responses should be HTTP 201 with sequence numbers `1` and `2`. The actor
stored in the audit evidence comes from the synthetic local authentication
context; the request actor is retained only because it is part of the assignment
envelope and cannot override the trusted identity.

### Query and verify the chain

```sh
curl --fail "http://localhost:8080/v1/audit/events?resourceType=DEMO_ACCOUNT&resourceId=demo-account-001&pageSize=10"

curl --fail "http://localhost:8080/v1/audit/events/chains/tenant:local-demo-tenant/verification"
```

The query should return both events in deterministic order. Verification is a
successful HTTP 200 operation whose body reports `valid=true`.

### Stop or reset

```sh
docker compose down
```

To remove all local demonstration data and restart from sequence 1:

```sh
docker compose down --volumes
```

## Build and test

With local JDK 17, Maven 3.9+, and a running Docker engine:

```sh
mvn --batch-mode --no-transfer-progress clean test
```

PostgreSQL integration tests use Testcontainers and require Docker. A successful
run ends with `BUILD SUCCESS`. The CI workflow also runs secret scanning, the
clean test suite, repeated concurrency tests, the tamper demonstration, and
query-plan checks. See [testing](docs/testing.md),
[test traceability](docs/test-traceability.md), and the
[CI workflow](.github/workflows/java-ci.yml).

## Tamper demonstration

Run from Git Bash, WSL, macOS, or Linux:

```sh
sh scripts/demo-tampering.sh
```

The repeatable demonstration creates a valid chain, proves it verifies, then
modifies and deletes database records directly and confirms the verifier reports
the first invalid sequence. It uses an isolated Testcontainers PostgreSQL
database and does not alter the Compose database.

## Troubleshooting

| Symptom | Diagnosis and recovery |
| --- | --- |
| Port `8080` is already in use | Run `docker compose ps` and inspect the host process using the port. Stop that process, or change the mapping in `compose.yaml`, for example `8081:8080`, and use `http://localhost:8081` in the curl commands. |
| Health request fails immediately after startup | The image may still be building or the application may still be migrating. Run `docker compose ps` and `docker compose logs --tail=200 audit-service`, then retry the health request. |
| PostgreSQL is unavailable or unhealthy | Run `docker compose logs --tail=200 postgres` and `docker compose exec postgres pg_isready -U postgres -d audit_log`. The database is internal to Compose and intentionally does not occupy host port 5432. |
| Flyway migration fails | Run `docker compose logs audit-service` and find the first Flyway error, including the migration version and PostgreSQL message. Do not edit an already-applied migration. For disposable demo data, fix the cause and run `docker compose down --volumes` before restarting. Preserve and repair real data rather than deleting its volume. |
| Native build cannot find Java or uses the wrong version | Run `java -version` and `mvn -version`; both should report the same JDK 17 installation. Set `JAVA_HOME` to that JDK and reopen the shell. |
| Testcontainers cannot start PostgreSQL | Confirm `docker info` succeeds and the Docker engine has enough memory and disk. Review the failing test output before retrying. |
| API returns an idempotency conflict | Each changed request needs a new `Idempotency-Key`. An exact replay with the same key and body returns the original receipt with HTTP 200. |

## Repository layout

| Path | Purpose |
| --- | --- |
| [`src/main/java`](src/main/java/) | HTTP, application, integrity, persistence, security, retention, and privacy modules. |
| [`src/test/java`](src/test/java/) | Unit, integration, authorization, concurrency, lifecycle, and tamper tests. |
| [`db/migration`](db/migration/) | Versioned Flyway schema, database roles, lifecycle tables, and query indexes. |
| [`openapi`](openapi/) | Versioned append API contract. Search and verification behavior are documented separately. |
| [`docs`](docs/) | Requirements, architecture, decisions, security, evidence, and review material. |
| [`scripts`](scripts/) | Repeatable tamper demonstration and performance/query-plan utilities. |
| [`dashboards`](dashboards/) | Operational metric and query notes. |
| [`compose.yaml`](compose.yaml) and [`Dockerfile`](Dockerfile) | Reproducible local reviewer environment. |

## Reviewer documentation

- [Business context](docs/business-context.md),
  [requirements](docs/requirements.md), [open questions](docs/open-questions.md),
  and [scope](docs/scope.md).
- [Functional architecture](docs/architecture/functional-architecture.md),
  [domain model](docs/diagrams/domain-model.md), and
  [architecture decisions](docs/decisions/).
- [API contract](docs/api-contract.md) and
  [OpenAPI append contract](openapi/audit-api.yaml).
- [Integrity design](docs/integrity-design.md),
  [verification behavior](docs/verification.md), and
  [data storage](docs/data-storage.md).
- [Security model](docs/security-model.md), [threat model](docs/threat-model.md),
  [security review](docs/security-review.md), and
  [logging policy](docs/logging-policy.md).
- [AI usage disclosure](docs/ai-usage.md) and
  [repository attestation](docs/attestation.md).
- [Test evidence](docs/test-traceability.md) and
  [live-defense runbook](docs/live-defense-runbook.md).
- [Privacy and exports](docs/privacy-and-exports.md) and
  [observability](docs/observability.md).

## Known limitations

- A privileged operator could rewrite the complete database history and chain
  heads. Signed external anchors are a documented production enhancement.
- Archive and export artifacts use create-only local filesystem adapters, not
  durable retention-locked object storage.
- Redaction is a presentation overlay; it does not irreversibly destroy the
  original value.
- Prototype signing and commitment keys are not production key management.
- Verification and export are bounded synchronous operations, not demonstrated
  production-scale workers.
- Retention, redaction, and export have no production internal HTTP controllers.
- Rate limiting, deployment network policy, multi-region recovery, and a
  regulator portal are recommendations, not implemented controls.
- Scenario C regulator identity, report scope, and access contract remain
  unresolved.

The [security review](docs/security-review.md) classifies residual risks and
keeps deferred controls separate from implemented behavior.
