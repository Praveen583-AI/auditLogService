# ADR-002: Primary Database

- **Status:** Accepted
- **Date:** 2026-08-07
- **Decision owners:** Prototype engineering owner
- **Related documents:** [Data Storage](../data-storage.md), [Functional Architecture](../architecture/functional-architecture.md), [ADR-001](ADR-001-application-boundary.md)

## Context

The prototype requires:

- An atomic append operation that selects one authoritative predecessor, constructs integrity metadata, stores the record, and advances chain state before success is returned.
- Deterministic ordering within a chain.
- Filtering by any supported combination of actor, resource, event type, and time range.
- Pagination.
- Explicit schema and index evolution.
- Simple local setup.
- Direct data-store modification for the required tampering demonstration.
- Retention, redaction evidence, export, and compliance reporting over one authoritative store.

PostgreSQL, DynamoDB, and MongoDB were considered.

## Decision

Use **PostgreSQL** as the primary online database for the prototype.

The append operation will use one transaction to:

1. Lock the relevant chain-state record.
2. Read its current sequence and terminal hash.
3. Construct the next record and integrity metadata.
4. Insert the completed audit record.
5. Advance the chain-state record.
6. Commit before returning success.

Writes to the same chain serialize through the chain state. Unrelated chains may proceed independently.

## Comparison

| Concern | PostgreSQL | DynamoDB | MongoDB |
|---|---|---|---|
| Atomic append | Direct transaction with explicit chain-state locking | Transactional insert plus conditional head update | Transactional head update plus insert |
| Per-chain ordering | Explicit row or transaction-scoped lock | Partition, sort key, conditional version, and retries | Chain-head document, unique sequence, and conflict retries |
| Dynamic filters | Natural optional predicates and index combination | Efficient access depends on predefined keys and indexes | Flexible queries, with compound-index ordering constraints |
| Migration discipline | Explicit schema, constraint, and index migrations | Mixed item shapes require application-managed versioning | Flexible documents require application validation and migration discipline |
| Local setup | One database instance | Local emulator and service-specific tooling | Transactions require replica-set configuration |
| Tampering demonstration | Direct row modification is simple and visible | Direct item modification requires service-specific tooling | Direct document modification is simple |

## Rationale

PostgreSQL best matches the prototype because:

- Predecessor selection and persistence can be one understandable transaction.
- Per-chain serialization is explicit.
- Required filter combinations do not require a separate access-path model.
- Constraints and migrations make integrity-sensitive changes reviewable.
- One local instance supports end-to-end tests.
- Direct row modification makes tamper detection easy to demonstrate.
- Retention, redaction, export, and compliance behavior can share one transactional source of truth.

DynamoDB is not selected because its key-oriented query model makes arbitrary filter combinations and local demonstration less direct.

MongoDB is viable, but reliable multi-document append transactions require a replica set, adding setup without a clear prototype benefit.

## Consequences

### Positive

- One authoritative transactional persistence boundary.
- Clear atomic append and concurrency behavior.
- Straightforward query and pagination implementation.
- Explicit constraints, roles, indexes, and migrations.
- Reproducible local tampering tests.
- Consistent storage for lifecycle and verification evidence.

### Negative

- Writes within one chain are serialized.
- A single global chain would become a write hotspot.
- Relational migrations must be maintained deliberately.
- The structured payload requires a documented canonical representation for hashing.
- PostgreSQL is not itself an immutable store; privileges and integrity verification remain necessary.

## Main Limitation

The principal limitation is **write serialization within each chain**.

If the prototype uses one global chain, every append coordinates through one chain-state record. This limits concurrent write throughput. The assignment provides no event-volume target, so correctness and demonstrability take priority.

Future chain partitioning requires clarification of business ordering and verification scope. It must not be introduced only to claim scale.

## Append-Only Control Implications

PostgreSQL does not make a table intrinsically append-only. The design therefore requires layered controls:

- No general update or delete API.
- One application append path.
- Runtime roles without `UPDATE`, `DELETE`, or `TRUNCATE`.
- A non-login schema owner distinct from runtime roles.
- Constraints for required fields, chain positions, and hash representation.
- Optional update/delete blocking triggers as defense in depth.
- Full-chain verification and protected chain-head state.
- Controlled, monitored operational access.

Implemented database privileges prevent ordinary runtime mutation, and hash
verification detects modification within its documented trust boundary. A
blocking update/delete trigger and separately owned schema are recommended
deployment layers, not installed by the prototype. A fully privileged actor who
rewrites all records, internal anchors, and verification logic is outside the
protection of an internal hash chain alone.

## Alternatives Reconsideration

Reconsider this decision only if clarified requirements establish:

- Access patterns dominated by known partition keys and managed horizontal scaling.
- A document model whose benefits outweigh transaction setup and migration discipline.
- Event volume incompatible with the selected chain scope.
- Independent storage or geographic requirements not present in the prototype assignment.
