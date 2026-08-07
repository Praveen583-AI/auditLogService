# ADR-003: Event payload representation and versioning

- **Status:** Accepted
- **Date:** 2026-08-07
- **Decision owners:** Audit-log service team
- **Related:** [Data model](../data-model.md), [Primary database](ADR-002-primary-database.md)

## Context

Audit events may be retained for years, while producers can submit different event types and payload shapes. The service must preserve stable integrity and query fields, allow schema evolution, and verify historical events using the rules that applied when each event was written.

Three representations were considered:

1. **JSON-only events.** Flexible for producers, but common fields can drift, database constraints become weak, and routine filters depend on payload conventions and expensive JSON indexes.
2. **Fully normalized event types.** Strong relational constraints, but every new payload shape can require migrations, tables, joins, and coordinated producer changes.
3. **A stable relational envelope plus a versioned JSON payload.** Common evidence and filter fields remain consistent while event-specific detail evolves independently.

## Decision

Use a stable relational envelope plus a versioned JSON payload.

The relational envelope contains:

- identity and ordering: `event_id`, `chain_id`, and `sequence`;
- classification and schema: `event_type` and `event_schema_version`;
- common filters: `actor_id`, `resource_type`, `resource_id`, and `occurred_at`;
- recording metadata: `recorded_at`;
- integrity metadata: `previous_hash`, `content_hash`, `hash_algorithm`, and `canonicalization_version`.

The `payload` contains event-specific structured detail. It is validated against the schema identified by `(event_type, event_schema_version)` before append. Unsupported versions are rejected. Accepted events retain their original schema version; migration does not rewrite historical payloads.

A payload field is promoted into the relational envelope only when it becomes stable across applicable event types and is required for a confirmed filter, index, constraint, or integrity rule. The prototype will not add a broad JSON index for speculative payload search.

## Schema versioning

`event_schema_version` defines the meaning and allowed shape of the event payload.

- Schemas are retained for every accepted version.
- A new version is introduced for incompatible meaning or shape changes.
- Historical events remain readable under their stored version.
- Export and archive artifacts preserve the event type and schema version.
- Validation occurs before hashing so invalid or ambiguous content is never appended.

## Canonicalization versioning

`canonicalization_version` identifies the exact deterministic rule used to turn the integrity-covered event into bytes for `content_hash`. It is independent of `event_schema_version`: a payload schema can change without changing byte rules, and byte rules can evolve without changing business meaning.

Each canonicalization version must define:

- exactly which envelope and payload fields are covered;
- object-key ordering and whether array order is significant;
- treatment of absent fields, explicit `null`, and unknown fields;
- number representation and rejection of non-finite or ambiguous values;
- timestamp format, timezone, and precision;
- character encoding, Unicode handling, escaping, and whitespace;
- rejection of duplicate JSON object keys.

Hashing operates on validated semantic values, not producer-supplied whitespace or object-key order. Arrays remain ordered unless a schema explicitly defines a normalization rule.

Verification dispatches to the stored canonicalization version and stored hash algorithm. A version upgrade applies only to new events; old events are never rehashed in place. Archive manifests and exports preserve the versions needed to verify their contents.

## Redaction interaction

Redaction produces an authorized presentation of evidence; it does not mutate the original payload, schema version, canonicalization version, or content hash. The redaction record preserves the policy and proof metadata needed to explain why the visible representation differs from the integrity-protected original. Whether any role may retrieve original sensitive values remains a product-owner decision.

## Query and index policy

The prototype indexes confirmed common filters in relational columns: chain and sequence, actor and time, and resource and time. Event-type and time indexing is added only if confirmed reporting requires it. Arbitrary JSON-path filtering and broad JSON indexing are design extensions, not mandatory prototype features.

## Consequences

**Benefits**

- Common filters, ordering, and integrity constraints are consistent across producers.
- New event-specific data usually does not require a database migration.
- Historical events remain interpretable and verifiable under explicit versions.
- Index cost is controlled by confirmed queries.

**Costs and limitations**

- A production service supporting multiple producer schemas would need a schema
  registry or equivalent versioned validators. The prototype stores an event
  schema version and enforces structural limits; it does not implement a
  producer-schema registry.
- Canonicalization rules are part of the long-lived evidence contract and require regression fixtures.
- Cross-event constraints inside arbitrary payloads remain weaker than normalized columns.
- Frequently queried payload properties may later need deliberate promotion and backfill.
- Exporters, archive readers, and verifiers must retain support for historical schema and canonicalization versions.

## Assumptions requiring confirmation

This decision does not define the allowed payload size, supported event schema versions, canonicalization format, hash algorithm, regulator export format, redaction access to originals, or arbitrary payload-search requirements. Those items require explicit confirmation before their contracts are implemented.
