# Audit API contract

## Scope

This document and the current OpenAPI file describe only the append contract.
The application also implements public search and chain-verification
controllers, but their complete schemas have not yet been added to this OpenAPI
file. Retention, redaction, and export exist as application services without
production controllers; their endpoint contracts remain design work.

The OpenAPI source is [`openapi/audit-api.yaml`](../openapi/audit-api.yaml).

## Append an audit event

```http
POST /v1/audit/events
Authorization: Bearer <credential>
Idempotency-Key: <opaque caller-generated value>
Content-Type: application/json
```

### Caller-provided request

```json
{
  "eventType": "ACCOUNT_UPDATED",
  "eventSche…22402 tokens truncated…nant/chain; repository constrains range | One explicit range | Policy/manifest IDs only | Archive lifecycle actions implemented |
| Redaction service operation (no controller) | Injected actor context in tests | Admin policy | Tenant and target event verified | JSON-pointer and reason validation | Never log removed value | Redaction record implemented |

Rate limiting and network policy remain deployment controls for the prototype and must be enabled before public exposure.

## Local development

The `local` profile deliberately disables JWT validation and supplies fixed, non-secret demo identity values. It must be activated explicitly. Production keeps `audit.security.enabled=true` and requires `AUDIT_JWT_ISSUER_URI`. Deployment policy must reject the `local` profile outside developer machines.

## Secret handling

The issuer URI and non-sensitive limits belong in configuration. Database credentials, client secrets and archive credentials belong in environment-injected secrets or a secret manager. Encryption and signing keys belong in a key-management system. Tokens, passwords, private keys, real audit payloads, database dumps, signed export URLs and populated `.env` files must never be committed.
