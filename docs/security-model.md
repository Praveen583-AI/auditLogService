# Security model

## Security objective

The service treats audit records as regulated evidence. Identity and tenant scope become trusted only after authentication at the service boundary. Request bodies, query parameters and ordinary client headers are not authoritative identity sources.

## Trust boundaries

| Boundary | Untrusted input | Enforcement |
|---|---|---|
| Producer to `POST /v1/audit/events` | Event envelope, resource and payload | JWT authentication, `audit.write` authority, server-derived tenant/producer/actor, request validation, payload limits and idempotency |
| Reader to `GET /v1/audit/events` | Filters, page size and cursor | JWT authentication, `audit.read` authority, server-applied tenant scope, bounded pagination and opaque cursor validation |
| Auditor to chain verification | Chain identifier | JWT authentication, `audit.read` authority and tenant-scoped repository lookup |
| Proposed operator route (`/internal/**`) | Retention, redaction or export job parameters | **Design-only route:** separate workload authority, non-public routing and action evidence |
| Proposed administrator route (`/admin/**`) | Privileged policy or evidence requests | **Design-only route:** separate admin authority, explicit tenant scope and action evidence |
| Application to database/archive | Integrity-protected records and manifests | Implemented database roles and transactions; archive uses local files. Encrypted transport and separate archive credentials are production controls. |

A gateway identity header is trusted only if the gateway strips client copies, injects the value itself and the application cannot be reached by bypassing that gateway. The prototype uses verified JWT claims directly.

Only event write, search, and chain verification have production controllers.
Retention, redaction, and export are tested application services. The endpoint
checklist below states the required boundary if controllers are added; it does
not claim those lifecycle routes exist.

## Trusted identity context

Production requires these verified claims:

- `tenant_id`: authoritative tenant scope.
- `sub`: authenticated actor.
- `client_id` or `azp`: authenticated producer application.
- `actor_type`: optional classification; defaults to `USER`.
- OAuth scopes: `audit.write`, `audit.read`, `audit.internal` or `audit.admin`.

`AuthenticatedActor` converts the verified principal into `AuditRequestContext`. Persistence and query services use this server-created context. The actor object currently present in the create-event body is producer-supplied compatibility data and is not authoritative; the transactional appender hashes and persists actor identity from the trusted context.

## Endpoint checklist

| Endpoint or operation | Authentication | Authorization | Tenant isolation | Size/rate control | Logging | Audit-of-audit |
|---|---|---|---|---|---|---|
| `POST /v1/audit/events` | JWT | `audit.write` | Context-derived tenant and chain | Existing payload limits; edge rate limit required | Correlation, event and chain IDs; never payload/token | Normal event receipt |
| `GET /v1/audit/events` | JWT | `audit.read` | Tenant injected by query service | Page-size/cursor bounds; read rate limit required | Filters and cursor values must not be logged | Broad/sensitive searches recommended |
| `GET /v1/audit/events/chains/{chainId}/verification` | JWT | `audit.read` | Repository requires tenant plus chain | Bound synchronous work; verification rate limit required | Result/reason/sequence only | Record regulator/admin verification in production |
| Export service operation (no controller) | Injected actor context in tests | Compliance/admin policy | Requester and tenant persisted | Bounded prototype selector; no API rate control | Never log artifact contents or tokens | Export access actions implemented |
| Retention service operation (no controller) | Trusted internal caller assumption | Database maintenance role; controller authorization not implemented | Request carries tenant/chain; repository constrains range | One explicit range | Policy/manifest IDs only | Archive lifecycle actions implemented |
| Redaction service operation (no controller) | Injected actor context in tests | Admin policy | Tenant and target event verified | JSON-pointer and reason validation | Never log removed value | Redaction record implemented |

Rate limiting and network policy remain deployment controls for the prototype and must be enabled before public exposure.

## Local development

The `local` profile deliberately disables JWT validation and supplies fixed, non-secret demo identity values. It must be activated explicitly. Production keeps `audit.security.enabled=true` and requires `AUDIT_JWT_ISSUER_URI`. Deployment policy must reject the `local` profile outside developer machines.

## Secret handling

The issuer URI and non-sensitive limits belong in configuration. Database credentials, client secrets and archive credentials belong in environment-injected secrets or a secret manager. Encryption and signing keys belong in a key-management system. Tokens, passwords, private keys, real audit payloads, database dumps, signed export URLs and populated `.env` files must never be committed.
