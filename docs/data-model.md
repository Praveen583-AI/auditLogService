# Data model

This model supports append-only evidence, per-chain verification, idempotent ingestion, retention and archival, redaction, export, and recorded verification outcomes. It separates immutable evidence from mutable operational state.

## Entity summary

| Entity | Responsibility | State classification |
|---|---|---|
| `AuditEvent` | The authoritative audit fact and its chain proof | Immutable |
| `ChainHead` | Coordinates the next position in one chain | Operational state |
| `IdempotencyRecord` | Makes producer retries safe and detects key reuse with different input | Operational state with restricted transitions |
| `ArchiveManifest` | Proves which contiguous range moved to an archive and where it is stored | Operational while preparing; immutable evidence when completed |
| `RedactionAction` | Records an authorized redacted view without rewriting the source event | Lifecycle state; immutable when completed |
| `VerificationResult` | Records the outcome of a verification run | Immutable observation |
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

**Key fields.** `chain_id`, `last_sequence`, `last_event_id`, `last_hash`, `version`, and `updated_at`.

**Invariants.**

- Exactly one head exists per chain.
- An append inserts one `AuditEvent` and advances the head by exactly one in the same transaction.
- Sequence and version never decrease.

**Database shape.** Primary key `chain_id`; no additional index is required for the prototype. A foreign key from `last_event_id` is optional because physical archival can remove the referenced event.

## IdempotencyRecord

**Responsibility.** Return the original outcome for an equivalent retry and reject reuse of a key for different input.

**Key fields.** `idempotency_id`, `scope_id`, `operation`, `idempotency_key_hash`, `request_fingerprint`, `status`, `event_id`, `response_reference`, `created_at`, `updated_at`, and `expires_at`.

**Invariants.**

- `(scope_id, operation, idempotency_key_hash)` is unique.
- The same key and request fingerprint returns the recorded result.
- The same key with a different fingerprint is rejected.
- A successful record identifies at most one event.

**Database shape.** Primary key `idempotency_id`; unique constraint on the idempotency scope. Add an `expires_at` index only if expiry cleanup is implemented. A physical event foreign key is safe only when the record expires before event archival.

## ArchiveManifest

**Responsibility.** Describe and authenticate a contiguous archived event range without treating authorized movement as tampering.

**Key fields.** `archive_id`, `archive_action_id`, `chain_id`, `first_sequence`, `last_sequence`, `first_event_id`, `last_event_id`, `event_count`, `predecessor_hash`, `terminal_hash`, `hash_algorithm`, `canonicalization_version`, `object_locator`, `object_digest`, `retention_policy_version`, `status`, `archived_at`, and `failure_reason`.

**Invariants.**

- A completed manifest covers one contiguous range and cannot overlap another completed range in the same chain.
- The archive object is verified before active rows are removed.
- Completed range, boundary proof, locator, digest, and policy metadata are frozen.

**Database shape.** Primary key `archive_id`; foreign key `chain_id` to `ChainHead` with restricted deletion; unique `object_locator`; range lookup index on `(chain_id, first_sequence, last_sequence)` for completed manifests. Prevent overlap with a database exclusion constraint or an equivalent locked transaction. First and last event IDs are logical references, not foreign keys, because their rows may be archived.

## RedactionAction

**Responsibility.** Record who authorized a restricted presentation, which payload paths are affected, and how verification remains possible.

**Key fields.** `redaction_action_id`, `event_id`, `chain_id`, `event_sequence`, `field_paths`, `redaction_method`, `replacement_representation`, `policy_version`, `reason`, `requested_by`, `approved_by`, `requested_at`, `executed_at`, `status`, `proof_metadata`, and `failure_reason`.

**Invariants.**

- Only policy-authorized payload paths can be redacted.
- Event identity, ordering, stored content, and chain hashes are unchanged.
- Normal views apply completed actions; verification continues against original evidence.
- Completed action details are immutable.

**Database shape.** Primary key `redaction_action_id`; index `(event_id, executed_at DESC)`; optional completed-action index `(chain_id, event_sequence)`. Use a physical event foreign key only while archived event identities remain represented online.

## VerificationResult

**Responsibility.** Preserve what was checked, when it was checked, and the first detected inconsistency.

**Key fields.** `verification_id`, `verification_type`, `chain_id`, scope boundaries, `started_at`, `completed_at`, `status` (`intact`, `broken`, or `incomplete`), `records_checked`, first inconsistent event or sequence, `violation_type`, expected and observed values, chain-head snapshot, algorithm versions, archive IDs, and `details`.

**Invariants.**

- Every run creates a new result; prior results are not overwritten.
- The result identifies the exact scope and versions used.
- Broken or incomplete results retain enough detail to reproduce the investigation.

**Database shape.** Primary key `verification_id`; foreign key `chain_id` to `ChainHead`; index `(chain_id, completed_at DESC)`. The first inconsistent event is a logical reference because the condition being reported may be a missing or archived record.

## ExportJob

**Responsibility.** Track a stable actor- or resource-based evidence export and its integrity proof.

**Key fields.** `export_job_id`, selector type, actor or resource selector, requested time range, `requested_by`, timestamps, `status`, `snapshot_sequence`, `snapshot_head_hash`, `record_count`, `bundle_locator`, `bundle_digest`, `format_version`, `included_archive_ids`, `redaction_view_version`, and `failure_reason`.

**Invariants.**

- Exactly one selector type is used.
- A completed export has a frozen snapshot boundary, count, artifact locator, digest, format, and applicable redaction view.
- An artifact is not released before its digest and proof metadata are final.

**Database shape.** Primary key `export_job_id`; unique `bundle_locator` when present. Add queue-status and selector-history indexes only if asynchronous processing and job history queries are implemented.

## Query indexes and write cost

| Requirement | Index | Cost and decision |
|---|---|---|
| Writes and ordered verification by chain | Unique `AuditEvent(chain_id, sequence)` | Mandatory; adds one index write but enforces ordering uniqueness and supports sequential reads. |
| Search by actor and time | `AuditEvent(actor_id, occurred_at DESC, event_id)` | Supported; every append writes another index and high-cardinality actors increase storage. |
| Search by resource and time | `AuditEvent(resource_type, resource_id, occurred_at DESC, event_id)` | Supported; necessary for resource evidence, with the same append and retention overhead. |
| Filter by event type and time | `AuditEvent(event_type, occurred_at DESC, event_id)` | Add only if required by confirmed searches; it increases write amplification. |
| Time-only reporting | `AuditEvent(occurred_at DESC, event_id)` | Defer unless time-only reports cannot use a selective index. |
| Arbitrary payload filtering | Broad JSON index | Out of the prototype: costly to maintain and not supported by a confirmed query contract. Promote stable, commonly queried fields instead. |
| Idempotent retries | Unique idempotency-scope index | Mandatory; enables atomic conflict detection at the cost of one indexed write per accepted key. |
| Ordered archive lookup | Completed-range archive index | Needed when archival is implemented; small compared with event indexes. |

Every additional event index increases append latency, storage, page writes, vacuum work, and archival cleanup cost. Indexes therefore follow demonstrated filters rather than speculative analytics.

## Transaction and lifecycle boundaries

- **Append:** lock or conditionally update one `ChainHead`; validate idempotency; insert `AuditEvent`; advance the head; finalize `IdempotencyRecord`; commit atomically.
- **Archive:** create a preparing manifest; write and verify the immutable object; complete and freeze the manifest; only then remove eligible active rows under privileged operational control.
- **Redaction:** authorize and append a `RedactionAction`; reads derive the permitted view without updating `AuditEvent`.
- **Verification:** read a stable scope across active events and completed manifests, then append a `VerificationResult`.
- **Export:** capture a chain boundary, gather active and archived evidence, apply the authorized redaction view, calculate the bundle digest, then complete the `ExportJob`.

## Assumptions requiring confirmation

The physical schema depends on unresolved assignment questions: the scope of a chain and ordering guarantee, idempotency retention, whether archived event identities remain online, exact redaction semantics and privileged access to originals, export consistency and format, archive immutability guarantees, and expected query volume. These are documented in [open questions](open-questions.md); they must not be silently converted into implementation requirements.
