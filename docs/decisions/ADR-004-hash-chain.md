# ADR-004: Use per-tenant SHA-256 hash chains

- **Status:** Accepted
- **Date:** 2026-08-07
- **Decision owners:** Audit-log service team
- **Related:** [Integrity design](../integrity-design.md), [Event payload](ADR-003-event-payload.md), [Data model](../data-model.md)

## Context

The assignment requires tamper evidence: unauthorized modification, deletion, insertion, and reordering must be detectable. It does not require distributed consensus or mutually distrustful organizations to agree on a ledger.

The mechanism must be achievable in a two-to-three day prototype, support append-heavy writes, remain verifiable after archival, and leave a credible path to stronger protection against a privileged full-history rewrite.

The ordering boundary also affects contention and the meaning of verification. A single global chain provides total order but serializes every write. Resource chains reduce contention but fragment tenant-wide investigations.

## Options considered

### SHA-256 hash chain

Each event stores its predecessor's hash and a digest of its canonical integrity fields. Verification recomputes events sequentially.

- Lowest implementation and operational effort.
- Natural fit for append-only writes and ordered verification.
- Detects changes and gaps within the verified range.
- Requires a trusted head, archive boundary, or external anchor to detect a privileged rewrite of the complete history.

### Merkle tree

Events are leaves whose hashes are combined into a root.

- Supports compact inclusion proofs and efficient comparison of published batches.
- Requires tree-node persistence, batching or append-tree rules, proof formats, and root lifecycle management.
- Does not create external trust unless roots are signed or independently published.
- Adds complexity not needed by a requirement for sequential chain verification.

### Blockchain

Events are committed through a replicated consensus protocol.

- Useful when mutually distrustful parties must share control of the ledger.
- Introduces nodes, consensus, keys, governance, network operations, upgrades, recovery, and monitoring.
- A blockchain controlled by one operator provides little additional external trust.
- Solves a broader problem than the assignment states.

## Decision

Use a SHA-256 hash chain. Partition chains by immutable tenant identifier and maintain one atomic `ChainHead` for each tenant.

Every event stores:

- `chainId` and monotonically increasing `sequence`;
- `previousHash`;
- `contentHash`;
- `hashAlgorithm`;
- `canonicalizationVersion`.

The hash covers the event's immutable identity, order, classification, common evidence fields, timestamps, original payload, predecessor hash, and version metadata. Exact serialization is governed by the stored canonicalization version.

The append transaction inserts the event and advances its chain head atomically. Verification proceeds in sequence and returns a `VerificationResult`. The prototype does not persist verification-run history; an append-only verification history is a production enhancement.

The prototype guarantees total recorded order only within one tenant chain. It intentionally provides no total order across tenants. Cross-chain timestamps are reporting data, not cryptographic ordering proof.

If no tenant exists in the assignment domain, the prototype uses one explicit fixed chain and documents global-chain contention as a limitation. It does not invent a hidden tenant model.

## External trust

The hash chain alone cannot prove history against an administrator who can rewrite every event and the current head. Completed archive manifests therefore retain range boundaries and object digests and should be protected by immutable storage or a separate administrative boundary.

Periodically signed chain-head anchors stored outside the audit database are the recommended extension when independent detection of privileged full-history rewrite is required. Anchor signing and external storage are design-only unless confirmed as prototype requirements.

## Consequences

### Positive

- Meets the stated tamper-evidence requirement with the smallest mechanism.
- Keeps append and verification behavior understandable and demonstrable.
- Tenant partitioning reduces contention and limits verification and recovery scope.
- Version metadata keeps historical hashes reproducible.
- Archive manifests preserve continuity through legitimate retention operations.
- The design can later add signed anchors or Merkle batch proofs without blockchain.

### Negative

- Verification of a complete chain is sequential and grows with chain length.
- A high-volume tenant can still contend on its chain head.
- There is no cryptographic total order across tenant chains.
- Internal hashes without independently protected checkpoints cannot detect every privileged full-history rewrite.
- Historical canonicalization implementations and test vectors must be retained.

## Rejected additions

- **Merkle tree for the prototype:** inclusion proofs are not a confirmed requirement and do not eliminate the need for a trusted root.
- **Blockchain:** distributed consensus, decentralized governance, and multi-party ledger ownership are not requirements.
- **One global production chain:** unnecessary serialization and failure scope for unrelated tenants.
- **Chain per resource:** makes resource verification convenient but weakens tenant-wide chronological investigation and reporting.

## Follow-up decisions

Before implementation is treated as production-ready, confirm the chain boundary, canonicalization specification, hash encoding and genesis value, archive trust boundary, verification service levels, and whether signed anchors are mandatory.
