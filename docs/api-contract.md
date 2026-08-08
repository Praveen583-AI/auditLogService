# Audit API contract

## Scope

The executable OpenAPI contract covers exactly the implemented public append,
search, and synchronous chain-verification controller operations. Retention,
redaction, export, and asynchronous verification have no production
controllers; they remain application services or architecture recommendations
and are intentionally absent from the public specification.

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
  "eventSchemaVersion": 1,
  "occurredAt": "2026-08-07T14:30:12.123Z",
  "actor": {
    "id": "employee-42",
    "type": "USER"
  },
  "resource": {
    "type": "ACCOUNT",
    "id": "account-123"
  },
  "payload": {
    "changedFields": ["address"]
  }
}
```

| Field | Required | Meaning |
|---|---:|---|
| `eventType` | Yes | Selects the event meaning and payload schema |
| `eventSchemaVersion` | Yes | Version of the producer payload contract |
| `occurredAt` | Yes for the prototype | Time the business action occurred; it does not determine chain order |
| `actor.id` | Yes | Compatibility field in the current DTO; it cannot override authenticated actor identity |
| `actor.type` | Yes | Compatibility field in the current DTO; it cannot override authenticated actor type |
| `resource.type` | Yes | Type of affected resource |
| `resource.id` | Yes | Identifier of affected resource |
| `payload` | Yes | Event-specific JSON object; producer-specific schemas are not enforced by the prototype |

The caller supplies the business fact but does not control its audit-chain placement. Unknown request fields are rejected rather than silently omitted before hashing.

### Trusted and server-assigned values

The authenticated context supplies `tenantId`, `producerId`, authorization scope, and therefore `chainId`. The server assigns `eventId`, `sequenceNumber`, `previousHash`, `recordedAt`, `contentHash`, `hashAlgorithm`, and `canonicalizationVersion`.

These values are not accepted in request JSON. Tenant and producer identity must not be trusted from ordinary headers or payload properties.

The running append service uses the authenticated actor for persistence and
hashing. Although the current DTO still requires `actor`, its values are not
authoritative. Removing that compatibility field is a future contract change.

## Success receipt

A first successful append returns:

```http
HTTP/1.1 201 Created
Location: /v1/audit/events/018f...
Idempotency-Replayed: false
X-Correlation-Id: 7c28...
```

```json
{
  "eventId": "018f...",
  "chainId": "tenant:tenant-7",
  "sequenceNumber": 815,
  "recordedAt": "2026-08-07T14:30:12.456789Z",
  "contentHash": "4f8c...",
  "hashAlgorithm": "SHA-256",
  "canonicalizationVersion": 1
}
```

The response is a durable receipt. It does not expose `previousHash`, chain-head state, database identifiers, request fingerprints, idempotency-key hashes, credentials, storage locations, or internal exception details.

## Idempotency

The idempotency scope is:

```text
(authenticatedTenantId, authenticatedProducerId, operation, hash(Idempotency-Key))
```

The request fingerprint covers all normalized caller-controlled event semantics plus the trusted tenant and producer scope. It does not cover JSON formatting, transport correlation ID, authentication-token bytes, the idempotency key itself, or server-assigned event fields.

| Situation | Behavior | Status |
|---|---|---:|
| First valid use | Atomically insert the event, advance its chain head, and complete the idempotency record | `201 Created` |
| Same key and same semantic request | Return the stored original receipt; do not insert or advance the head | `200 OK` |
| Same key and different semantic request | Reject without revealing the original request or receipt | `409 Conflict` |

An exact replay returns `Idempotency-Replayed: true`. A timeout after commit is therefore safe: the retry finds the completed record and returns the original receipt.

The event, chain-head change, and completed idempotency record must commit in one PostgreSQL transaction. The idempotency record retains the request fingerprint and response receipt for the promised retry window.

## Errors

Errors use a stable, non-sensitive shape:

```json
{
  "code": "IDEMPOTENCY_KEY_REUSED",
  "message": "The Idempotency-Key has already been used with a different request.",
  "correlationId": "7c28...",
  "violations": []
}
```

| Status | Example use |
|---:|---|
| `400` | Malformed JSON, missing header, or invalid field syntax |
| `401` | Missing or invalid authentication |
| `403` | Producer is not permitted to append the event |
| `409` | Idempotency key reused with different semantics |
| `404` | Requested chain is absent during verification |
| `503` | Chain coordination remains busy or storage is temporarily unavailable |
| `500` | Unexpected failure; response contains no stack trace or database detail |

`401` and `403` are currently produced by Spring Security before the controller
advice and therefore do not yet use the common `ApiError` body. A `413` mapper
exists, but no general JSON request-size guard is wired, so `413` is not
promised by the executable contract. `429` is not an implemented response.
Some invalid search combinations currently throw
`IllegalArgumentException` and reach the generic `500` mapper; this is an
implementation limitation, not a promised validation outcome.

## Search audit events

```http
GET /v1/audit/events?resourceType=ACCOUNT&eventType=ACCOUNT_UPDATED&pageSize=50
Authorization: Bearer <credential>
```

Filters `chainId`, `actorId`, `resourceType`, `resourceId`, `eventType`, `from`,
and `to` combine with logical AND. `resourceId` requires `resourceType`.
`from` is inclusive and `to` is exclusive over `recordedAt`.

For a single chain, results use `sequenceNumber ASC`. Cross-chain results use
`recordedAt DESC`, then `chainId DESC`, `sequenceNumber DESC`, and `eventId DESC`.
The returned `nextCursor` is opaque and may only be reused with the same tenant,
filters, and ordering mode. Cursor failures use `CURSOR_MALFORMED`,
`CURSOR_VERSION_UNSUPPORTED`, or `CURSOR_CONTEXT_MISMATCH`.

## Verify a chain

```http
GET /v1/audit/events/chains/{chainId}/verification
Authorization: Bearer <credential>
```

Verification is synchronous in the prototype. A completed operation returns
`200` with status `VALID`, `INVALID`, or `INDETERMINATE`; an invalid chain is a
verification result, not an HTTP client error. A missing chain returns
`404 CHAIN_NOT_FOUND`. A future job API for very large chains is architecture
work and is not part of the OpenAPI contract.

A validation failure before append does not permanently consume the idempotency key. A committed event is never reported as failed merely because delivery of its response timed out.

## Atomic processing boundary

Within one database transaction, the service claims the scoped idempotency key, locks or conditionally advances the tenant chain head, assigns the sequence, hashes and inserts the event, advances the head, stores the successful receipt, and commits. No success response is sent before commit.

## Assumptions requiring confirmation

- `occurredAt` is mandatory.
- The redundant caller-supplied actor compatibility field requires a future contract decision.
- The allowed actor types, payload size, timestamp skew, authentication details,
  idempotency-key syntax, and retry-retention window still require confirmation.
