# Data model

This document describes both implemented persistence and explicitly labeled
design extensions. The prototype supports append-only evidence, per-chain
verification, idempotent ingestion, archival, redaction overlays, and bounded
exports. Verification results are returned but not persisted.

## Entity summary

| Entity | Responsibility | State classification |
|---|---|---|
| `AuditEvent` | The authoritative audit fact and its chain proof | Immutable |
| `ChainHead` | Coordinates the next position in one chain | Operational state |
| `IdempotencyRecord` | Makes producer retries safe and detects key reuse with different input | Operational state with restricted transitions |
| `ArchiveManifest` | Proves which contiguous range moved to an archive and where it is stored | Insert-only completed evidence; preparation is recorded in separate lifecycle actions |
| `RedactionAction` | Records an authorized redacted view without rewriting the source event | Insert-only `redaction_record`; no mutable lifecycle status |
| `VerificationResult` | Returns the outcome of a verification run | Immutable in-memory value in the prototype; persistence is design-only |
| `ExportJob` | Tracks creation of a regulator or auditor export | Operational until terminal; completed artifact metadata is immutable |

## AuditEvent

**Responsibility.** Preserve one audit fact, its common query fields, event-specific payload, and its position in a tamper-evident chain.

**Key fields.** `event_id`, `chain_id`, `sequence`, `event_type`, `event_schema_version`, `actor_id`, `resource_type`, `resource_id`, `payload`, `occurred_at`, `recorded_at`, `previous_hash`, `content_hash`, `hash_algorithm`, and `canonicalization_version`.

**Invariants.**

- `event_id` and `(chain_id, sequence)` are unique.
- Sequence 1 uses the defined genesis value; every later event references the preceding hash in the same chain.
- Required evidence and version fields are present.
- The stored event is never updated or deleted through normal service or application database roles.
- A redacted representation does not replace the original event or its hash.

**Database shape.** Primary key `event_id`; unique constraint on `(chain_id, sequence)`. Do not make `content_hash` unique because identical business content can be valid in separate events.

## ChainHead

**Responsibility.** Serialize appends within one ordering scope and expose the expected end of that chain.

**Implemented key fields.** `chain_id`, `tenant_id`, `latest_sequence`,
`latest_hash`, `version`, and `updated_at`. No last-event identifier is stored.

**Invariants.**

- Exactly one head exists per chain.
- An append inserts one `AuditEvent` and advances the head by exactly one in the same transaction.
- Sequence and version never decrease.

**Database shape.** Primary key `chain_id`, unique `tenant_id`, and unique
`(chain_id, tenant_id)` for composite event and archive foreign keys.

## IdempotencyRecord

**Responsibility.** Return the original outcome for an equivalent retry and reject reuse of a key for different input.

**Key fields.** `idempotency_id`, `scope_id`, `operation`, `idempotency_key_hash`, `request_fingerprint`, `status`, `event_id`, `response_reference`, `created_at`, `updated_at`, and `expires_at`.

**Invariants.**

- `(scope_id, operation, idempotency_key_hash)` is unique.
- The same key and request fingerprint returns the recorded result.
- The same key with a different fingerprint is rejected.
- A successful record identifies at most one event.

**Database shape.** Primary key `idempotency_id`; unique constraint on the
tenant/producer/operation/key-hash scope; event foreign key; and a partial
`expires_at` index. Retention currently deletes associated idempotency rows
before archiving their events; scheduled expiry cleanup is not implemented.

## ArchiveManifest

**Responsibility.** Describe and authenticate a contiguous archived event range without treating authorized movement as tampering.

**Implemented key fields.** `manifest_id`, `manifest_version`, `tenant_id`,
`chain_id`, `start_sequence`, `end_sequence`, `record_count`, boundary hashes,
`bundle_checksum`, bundle/checksum versions, `policy_id`, `archived_at`, storage
location/version, and signature metadata. Preparation and failure information
belongs to append-only `archive_lifecycle_action` records; the manifest has no
mutable status.

**Invariants.**

- A completed manifest covers one contiguous range and cannot overlap another completed range in the same chain.
- The archive object is verified before active rows are removed.
- Completed range, boundary proof, locator, digest, and policy metadata are frozen.

**Database shape.** Primary key `manifest_id`; restricted chain/tenant foreign
key; unique range and storage-location/version constraints; range lookup index
on `(tenant_id, chain_id, start_sequence)`. Overlap is rejected by the locked
publication transaction, not a database exclusion constraint.

## RedactionAction

**Responsibility.** Record who authorized a restricted presentation, which payload paths are affected, and how verification remains possible.

**Implemented key fields.** `redaction_id`, `tenant_id`, `event_id`, one
`json_pointer`, `policy_id`, `reason`, `authorized_by`, `authorized_at`,
replacement text, nonce, original-value commitment, commitment algorithm, and
commitment key identifier. Request/approval workflow and mutable status are not
implemented.

**Invariants.**

- Only policy-authorized payload paths can be redacted.
- Event identity, ordering, stored content, and chain hashes are unchanged.
- Normal views apply stored redaction records; verification continues against original evidence.
- Stored redaction records are insert-only for runtime roles.

**Database shape.** Primary key `redaction_id`; unique
`(tenant_id, event_id, json_pointer, policy_id)` and index
`(tenant_id, event_id, authorized_at)`. There is deliberately no event foreign
key because the hot event row may be archived.

## VerificationResult

**Prototype responsibility.** Return what was checked and the first detected
inconsistency. The current implementation is an immutable response value and is
not persisted.

**Implemented fields.** `status`, `valid`, `failure_reason`,
`failure_sequence`, `verified_count`, `last_verified_sequence`, and
`last_verified_hash`.

**Implemented invariants.**

- `valid=true` only for `VALID`; `valid=false` only for `INVALID`.
- `INDETERMINATE` has no Boolean validity claim.
- Any non-valid result includes a stable failure reason.

**Design-only production enhancement.** If verification-run history becomes a
requirement, persist a new append-only record containing a verification ID,
requester, tenant and chain scope, start and completion times, result, checked
boundaries, algorithm versions, and first inconsistency. Such a table and its
indexes do not exist in the prototype.

## ExportJob

**Responsibility.** Track a stable actor- or resource-based evidence export and its integrity proof.

**Implemented key fields.** `export_id`, `tenant_id`, selector type/value,
`requested_by`, status and timestamps, artifact location, hashed download token,
expiry, and failure code. Snapshot boundaries, record count, checksums, format,
archive manifests, and redaction proofs are held inside the signed artifact;
they are not separate `export_job` columns.

**Invariants.**

- Exactly one selector type is used.
- A completed export has an artifact locator, expiry, and hashed download token;
  its signed manifest contains the evidence boundary and section checksums.
- An artifact is not released before its digest and proof metadata are final.

**Database shape.** Primary key `export_id`; index
`(tenant_id, status, requested_at DESC)`. Artifact-location uniqueness and a
durable asynchronous queue are not implemented.

## Query indexes and write cost

| Requirement | Index | Cost and decision |
|---|---|---|
| Writes and ordered verification by chain | Unique `AuditEvent(chain_id, sequence)` | Mandatory; adds one index write but enforces ordering uniqueness and supports sequential reads. |
| Search by actor and time | `AuditEvent(tenant_id, actor_id, recorded_at DESC, chain_id, sequence_number, event_id)` | Implemented; every append writes another index and high-cardinality actors increase storage. |
| Search by resource and time | `AuditEvent(tenant_id, resource_type, resource_id, recorded_at DESC, chain_id, sequence_number, event_id)` | Implemented; necessary for resource evidence, with the same append and retention overhead. |
| Filter by event type and time | No dedicated index | Event type filtering is implemented but may scan/filter; add an index only after representative plans justify its write cost. |
| Cross-chain tenant ordering | `AuditEvent(tenant_id, recorded_at DESC, chain_id, sequence_number, event_id)` | Implemented for keyset pagination. |
| Arbitrary payload filtering | Broad JSON index | Out of the prototype: costly to maintain and not supported by a confirmed query contract. Promote stable, commonly queried fields instead. |
| Idempotent retries | Unique idempotency-scope index | Mandatory; enables atomic conflict detection at the cost of one indexed write per accepted key. |
| Ordered archive lookup | `(tenant_id, chain_id, start_sequence)` | Implemented; small compared with event indexes. |

Every additional event index increases append latency, storage, page writes, vacuum work, and archival cleanup cost. Indexes therefore follow demonstrated filters rather than speculative analytics.

## Transaction and lifecycle boundaries

- **Append:** lock or conditionally update one `ChainHead`; validate idempotency; insert `AuditEvent`; advance the head; finalize `IdempotencyRecord`; commit atomically.
- **Archive:** append preparation actions; write and verify the archive bundle;
  insert the signed manifest; then remove eligible hot rows in the same database
  transaction as manifest publication. External object immutability is not
  implemented.
- **Redaction:** authorize and append a `RedactionAction`; reads derive the permitted view without updating `AuditEvent`.
- **Verification:** read a stable scope across active events and completed manifests, then return a `VerificationResult`. Persisting verification history is not implemented.
- **Export:** capture a chain boundary, gather active and archived evidence, apply the authorized redaction view, calculate the bundle digest, then complete the `ExportJob`.

## Assumptions requiring confirmation

The physical schema depends on unresolved assignment questions: the scope of a chain and ordering guarantee, idempotency retention, whether archived event identities remain online, exact redaction semantics and privileged access to originals, export consistency and format, archive immutability guarantees, and expected query volume. These are documented in [open questions](open-questions.md); they must not be silently converted into implementation requirements.
