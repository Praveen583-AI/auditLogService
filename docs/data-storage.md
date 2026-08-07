# Data Storage

## Purpose

Define what remains in PostgreSQL, what moves to archive storage, and how the
system verifies legitimate archival without treating it as tampering. The
prototype uses a create-only local-file adapter plus signed manifests; an
externally immutable or retention-locked store is a production recommendation.

## Storage Responsibilities

| PostgreSQL | Archive bundle (local adapter in the prototype) |
|---|---|
| Active audit records | Complete archived records |
| Current chain state | Original hash-covered event fields |
| Archive-range manifests | Record identifiers and order |
| Retention and redaction evidence | Content and predecessor hashes |
| Range boundary hashes | Canonicalization and hash versions |
| Archive object locator and digest | Archive manifest |
| Policy and lifecycle status | Self-contained verification evidence |
| Query, export, and compliance views | Protected originals if permitted by the privacy policy |

PostgreSQL remains the authoritative catalog of the chain and its lifecycle. The archive contains the full historical evidence removed from active storage.

## Online Relational Data

### Audit Records

Each active record contains at least:

- Stable record identifier.
- Chain identifier.
- Chain sequence.
- `eventType`.
- `actorId`.
- `resourceType`.
- `resourceId`.
- `payload`.
- Event timestamp.
- Content hash.
- Predecessor hash.
- Hash-algorithm identifier.
- Canonicalization or record-format version.

`AuditEvent` has no lifecycle status. Effective archival and redaction state is
derived from separate manifests and action records.

### Chain State

For each chain, retain:

- Chain identifier.
- Current final sequence.
- Current final record identifier.
- Current final record hash.

The chain-state record provides the append position and detects loss of the final active or archived range when verification compares stored evidence to the expected chain head.

### Lifecycle Evidence

Retention and redaction are explicit lifecycle actions, not general event updates.

Retain:

- Lifecycle action identifier.
- Action type.
- Target record or range.
- Action timestamp.
- Policy identifier or version.
- Processing status.
- Evidence needed for independent verification.
- Failure or recovery information where an action did not complete.

## Append-Only Controls

### Prevention

- The public API exposes no general update or delete operation.
- The application repository exposes append and read behavior, not generic save or delete behavior.
- Runtime database roles lack `UPDATE`, `DELETE`, and `TRUNCATE` on base audit records.
- The migrations create non-login runtime group roles. Separating table
  ownership into a non-login migration role is a production deployment
  requirement, not provisioned by this repository.
- Required-field, hash-format, and unique chain-position constraints reject malformed records.
- A blocking update/delete trigger is an optional production defense; the
  prototype relies on database grants and verification and does not install
  such a trigger.

These controls prevent ordinary or accidental mutation. A table owner or superuser may bypass privileges or disable a trigger.

### Detection

- Recompute every record content hash.
- Check every predecessor link and sequence.
- Compare the verified tail with chain state.
- Evaluate persisted retention and redaction evidence.
- Report the first inconsistency and violation type.

A hash chain detects modification only while at least one expected reference remains outside the attacker's rewrite boundary. An actor able to rewrite all records, chain state, and verification logic can evade internal-only verification.

## Archive Bundle Contents

The implemented `ArchiveBundle` contains a bundle format version and an ordered
list of archived events. Each entry contains the canonical event fields and its
stored content hash. Together these provide:

- Chain identifier.
- First and last sequence.
- Record count.
- Predecessor hash immediately before the range.
- Terminal hash of the range.
- Every archived record in chain order.
- Stable record identifiers.
- Original hash-covered event fields.
- Content hashes and predecessor hashes.
- Hash-algorithm identifier.
- Canonicalization or record-format version.

The object-level checksum, range boundaries, policy, storage locator, and
signature metadata are stored in the separate online `ArchiveManifest`, not
duplicated inside the bundle.

If protected original values remain in the archive after redaction, access to those values requires a separately documented privacy and authorization decision.

## Minimum Online Archive-Range Manifest

| Field | Purpose |
|---|---|
| Archive-range identifier | Stable lifecycle and verification reference |
| Chain identifier | Identifies the affected chain |
| First and last sequence | Defines the exact gap represented by the archive |
| Record count | Detects an incomplete range |
| Predecessor hash | Anchors the first archived record to the preceding range |
| First and last event hashes | Commits to both ends of the archived range |
| Bundle/checksum format versions | Selects the correct bundle verification rules |
| Archive object identifier | Locates the archived evidence |
| Archive object digest | Detects byte-level alteration or object replacement |
| Archived timestamp | Records when movement occurred |
| Retention-policy identifier/version | Explains why movement was legitimate |
| Signature algorithm, key ID, version, time, and value | Authenticates the manifest under the configured prototype key |

Preparation and failure state is represented by separate append-only
`archive_lifecycle_action` rows; the manifest itself has no lifecycle status.

## Boundary Verification

For a range containing records 1001–2000:

1. The online manifest retains the verified hash of record 1000.
2. Recompute archived record 1001 and confirm its predecessor hash equals that boundary.
3. Recompute every archived record in sequence.
4. Confirm the recomputed terminal hash equals the manifest hash for record 2000.
5. Confirm online record 2001, if present, references the same terminal hash.
6. Confirm the current chain state agrees with the final active or archived tail.

```text
online record 1000
        |
        | predecessor boundary
        v
archived records 1001–2000
        |
        | terminal boundary
        v
online record 2001
```

## Verification Modes

### Online Continuity Verification

This is a design option, not a separate implemented mode. The prototype verifier
retrieves and verifies archive bundles when it crosses archived ranges.

Without fetching archive contents, the verifier can establish that:

- A completed manifest explains the active-data gap.
- Sequence and record-count boundaries are coherent.
- Neighboring online records connect to the retained boundary hashes.
- The archive action corresponds to the documented retention policy.
- The chain state remains consistent.

This prevents legitimate archival from becoming a false-positive chain break. It does not revalidate every archived record.

### Full Archive Verification

To verify the archived records themselves:

1. Retrieve the archive object.
2. Verify its object digest.
3. Match its chain, range, count, and versions to the online manifest.
4. Start from the retained predecessor hash.
5. Recompute every record and predecessor link.
6. Compare the recomputed terminal hash with the online manifest.
7. Confirm the following online record references that terminal hash.

Only this process supports a claim that the archived range itself was fully reverified.

## Safe Archival Sequence

1. Select one contiguous, eligible range.
2. Record a preparing lifecycle action.
3. Read the records in authoritative order.
4. Construct the self-contained archive object.
5. Write it through the configured archive adapter. The prototype adapter uses
   create-only local files; production should use independently protected,
   retention-locked storage.
6. Confirm the stored object and its digest.
7. Recompute and verify the archived range.
8. Atomically create the completed online manifest and mark the range archived.
9. Only then remove full active records if physical removal is part of the chosen behavior.
10. Verify the chain across the new boundary.

Failures before completion leave active records authoritative. An incomplete lifecycle action does not justify missing records.

## Failure Interpretation

| Condition | Verification result |
|---|---|
| Active gap with no completed manifest | Broken: unexplained missing range |
| Incomplete archive action | Broken or incomplete lifecycle action |
| Manifest covers a different range | Broken: range mismatch |
| Online successor has the wrong predecessor hash | Broken: boundary mismatch |
| Archive object is missing | Online continuity may be explainable; full verification fails |
| Archive digest differs | Broken archive artifact |
| Archived record content differs | Full verification fails |
| Range and both boundaries verify | Legitimate archived range |

## Limitation

An online range manifest is a cryptographic commitment to expected archived content, not proof that every archived record remains readable and intact at the present moment. Full verification requires retrieving and recomputing the archived range.
