# Integrity design

## Purpose

The integrity mechanism must make unauthorized modification, deletion, insertion, and reordering of audit events detectable. It provides tamper evidence, not distributed consensus, and does not by itself prevent a sufficiently privileged operator from changing stored data.

This design uses a SHA-256 hash chain for each tenant. It complements the append-only controls described in [data storage](data-storage.md) and the event representation in [ADR-003](decisions/ADR-003-event-payload.md).

## Integrity boundary and ordering

The prototype uses one chain per tenant:

```text
chainId = "tenant:" + immutableTenantId
```

Each chain has an independent `ChainHead`. An accepted append atomically assigns the next sequence, inserts the event, and advances that head.

The service guarantees a unique recorded order within one tenant chain. It intentionally does not guarantee a total order between different tenant chains. Cross-chain timestamps may support reporting, but they are not cryptographic proof that one cross-chain event preceded another.

This choice assumes tenant is the required isolation and reporting boundary. If the assignment has no tenant concept, the prototype must use one documented fixed chain and record the scalability limitation rather than silently inventing a tenancy requirement.

## Event hash

For event (n):

```text
contentHash[n] = SHA-256(
    UTF8("audit-event:v1")
    || canonicalSerialize(integrityFields[n])
)
```

The fixed domain separator prevents bytes hashed for another purpose from being interpreted as an audit event. `previousHash` is one of the integrity fields, so each digest commits to the preceding event.

For the first event, `previousHash` is a documented genesis value. For every later event:

```text
event[n].previousHash == event[n-1].contentHash
event[n].sequence == event[n-1].sequence + 1
```

### Integrity-covered fields

The canonical hash input includes:

- `eventId`;
- `chainId` and `sequence`;
- `previousHash`;
- `eventType` and `eventSchemaVersion`;
- `actorId`;
- `resourceType` and `resourceId`;
- `occurredAt` and `recordedAt`;
- the complete original `payload`;
- `hashAlgorithm`;
- `canonicalizationVersion`.

Producer identity, source application, correlation ID, request ID, and tenant ID must also be covered if they are confirmed as evidentiary fields. Their required presence remains an open question.

### Excluded fields

The hash does not include:

- `contentHash` itself;
- mutable processing, delivery, or job status;
- database storage metadata without evidentiary meaning;
- archive location;
- derived redacted representations;
- verification results or export-job state.

Exclusion permits legitimate operational state changes. It must not be used for a field whose alteration would change the event's evidentiary meaning.

## Canonical serialization

The service validates an event before hashing and serializes semantic values, not producer-supplied JSON text. Canonicalization version 1 must define and test the following rules:

- encode as UTF-8 without a byte-order mark;
- sort object keys using one documented ordinal rule;
- emit no insignificant whitespace;
- preserve array order;
- reject duplicate object keys;
- distinguish an absent field from explicit `null`;
- use the contract's exact field names;
- encode booleans only as `true` and `false`;
- apply one defined string escaping and Unicode policy;
- encode integers in base 10 without leading zeros;
- encode decimals without implementation-dependent rounding or exponent choices;
- reject `NaN`, infinity, and other non-JSON numeric values;
- normalize timestamps to UTC with a fixed ISO 8601 representation and precision;
- serialize identifiers in one defined textual representation;
- encode hashes consistently, for example as lowercase hexadecimal;
- reject unknown schema fields or retain and canonicalize them—never silently discard them.

The repository should include regression fixtures containing input values, exact canonical bytes, and expected digests. The writer, verifier, archive reader, and exporter must use the same fixtures.

## Version fields

Three independent versions are retained on every event:

| Field | Meaning |
|---|---|
| `eventSchemaVersion` | Meaning and allowed structure of the payload |
| `canonicalizationVersion` | Deterministic conversion of the integrity fields to bytes |
| `hashAlgorithm` | Cryptographic digest function applied to those bytes |

The algorithm and canonicalization versions must both be stored because the same algorithm can produce different results from different serialization rules. Conversely, the canonical bytes may remain unchanged while the digest algorithm changes.

Verification dispatches using the versions recorded on each event. Version upgrades apply to new events; historical events are never rehashed in place.

## Append sequence

An append for one tenant executes in a single database transaction:

1. Authenticate the producer and resolve its permitted `chainId`.
2. Validate the event schema and canonicalizable values.
3. Resolve the idempotency key.
4. Lock or conditionally advance that chain's `ChainHead`.
5. Assign `lastSequence + 1` and copy `lastHash` to `previousHash`.
6. Canonicalize the integrity fields and calculate `contentHash`.
7. Insert the immutable `AuditEvent`.
8. Advance `ChainHead` and finalize the idempotency result.
9. Commit.

The event must not become visible as accepted unless both the event and head update commit.

## Verification

A verification run selects a stable chain boundary and then:

1. loads events in `sequence` order;
2. confirms sequence continuity;
3. verifies the genesis value or supplied predecessor boundary;
4. canonicalizes each event using its stored version;
5. recomputes and compares `contentHash`;
6. compares each `previousHash` with the prior verified digest;
7. checks the final digest and sequence against the selected head or trusted anchor;
8. verifies any archive-object digest and manifest boundaries;
9. appends an immutable `VerificationResult`.

The result is `intact`, `broken`, or `incomplete`. It records the scope, versions, records checked, and first inconsistency. A missing archive or unsupported historical version produces `incomplete`, not a false claim of integrity.

## Retention and archive continuity

Authorized archival must not look like deletion. A completed, immutable `ArchiveManifest` retains at least:

- `chainId`;
- first and last sequence and event identifiers;
- event count;
- predecessor hash and terminal hash;
- hash and canonicalization versions;
- archive format version;
- immutable object locator and complete-object digest;
- retention policy version;
- completion time and responsible action identity.

The archive object is written and verified before the manifest becomes complete. Active event rows may be removed only after that transition. Verification checks the preceding active or archived boundary, the archived events, and the following boundary.

## Privileged full-history rewrite

An internal chain detects ordinary changes, but an operator able to replace every event and the `ChainHead` could recompute a consistent history. Two controls strengthen detection.

### Immutable archive manifests

A manifest and archive object protected by retention lock, separate credentials, or a separate administrative boundary preserve old range digests and chain boundaries. A rewritten database will no longer agree with that independently protected evidence.

A manifest under the same unrestricted administrator is still useful for lifecycle verification, but it does not independently defeat a coordinated rewrite.

### Signed chain-head anchors

At a defined interval, the service may create a canonical anchor containing:

- `chainId`;
- anchored sequence and `contentHash`;
- anchor timestamp;
- hash algorithm and canonicalization version;
- anchor format version.

A protected key signs the anchor, which is then stored in an immutable or separately controlled location. Verification proves that the current chain reaches the previously anchored hash at the stated sequence.

Anchors limit the undetectable rewrite window but do not prevent changes after the most recent anchor. Anchor frequency, key custody, signer identity, publication location, and retention require confirmation. Signed anchors are an architecture recommendation, not a mandatory prototype feature unless the assignment explicitly requires an independent trust boundary.

## Failure and recovery rules

- A failed append rolls back both the event and head change.
- A head mismatch or sequence conflict is retried from the current head; it is never repaired by overwriting an event.
- A broken verification does not automatically rewrite or delete evidence. It creates a result and triggers the documented investigation path.
- Recovery from backup must restore event rows, chain heads, manifests, and version definitions consistently, then verify them against any independently held anchor.
- Archive movement is accepted only through completed manifests; an unexplained gap remains broken or incomplete.

## Assumptions requiring confirmation

Implementation still requires answers for:

- whether tenant is the chain and regulator-access boundary;
- the genesis representation and canonicalization standard;
- timestamp and decimal precision;
- which producer and correlation fields are evidentiary;
- required verification scope and completion time;
- whether immutable archive storage is available;
- whether signed anchors are required, where they are held, and how frequently they are produced;
- who may access original values when a redacted view exists.
