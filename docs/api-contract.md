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
| `actor.id` | Yes | Actor asserted by the producer |
| `actor.type` | Yes | Confirmed actor category |
| `resource.type` | Yes | Type of affected resource |
| `resource.id` | Yes | Identifier of affected resource |
| `payload` | Yes | Event-specific object validated by event type and schema version |

The caller supplies the business fact but does not control its audit-chain placement. Unknown request fields are rejected rather than silently omitted before hashing.

### Trusted and server-assigned values

The authenticated context supplies `tenantId`, `producerId`, authorization scope, and therefore `chainId`. The server assigns `eventId`, `sequenceNumber`, `previousHash`, `recordedAt`, `contentHash`, `hashAlgorithm`, and `canonicalizationVersion`.

These values are not accepted in request JSON. Tenant and producer identity must not be trusted from ordinary headers or payload properties.

The distinction between authenticated producer and asserted actor is deliberate: the service proves which producer reported the event, but cannot independently prove the producer's actor assertion unless actor identity comes from a verified end-user credential.

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
| `422` | Well-formed request fails the selected event schema |
| `500` | Unexpected failure; response contains no stack trace or database detail |

A validation failure before append does not permanently consume the idempotency key. A committed event is never reported as failed merely because delivery of its response timed out.

## Atomic processing boundary

Within one database transaction, the service claims the scoped idempotency key, locks or conditionally advances the tenant chain head, assigns the sequence, hashes and inserts the event, advances the head, stores the successful receipt, and commits. No success response is sent before commit.

## Assumptions requiring confirmation

- Java package `com.praveen.auditlog` is provisional because no application skeleton exists.
- `occurredAt` is mandatory.
- Actor identity is asserted by the authenticated producer rather than derived from an end-user token.
- The allowed actor types, payload size, timestamp skew, identifier limits, authentication scheme, idempotency-key syntax, and retry-retention window still require confirmation.
