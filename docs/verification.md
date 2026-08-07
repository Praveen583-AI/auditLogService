# Chain verification

## Meaning

A valid result means the verifier successfully recalculated and linked the records available in the requested chain snapshot. It is tamper evidence, not proof that modification was impossible.

## Streaming algorithm

The verifier reads records in ascending sequence order under a repeatable-read transaction. It retains only the expected sequence, expected previous hash, last verified hash and counters. For each event it:

1. Confirms supported hash and canonicalization versions.
2. Confirms the first sequence and detects sequence gaps.
3. Compares `previousHash` with the last verified content hash.
4. Canonicalizes all integrity-protected fields.
5. Recalculates and compares `contentHash`.
6. At the end, compares the verified count and final hash with `ChainHead`.

The synchronous prototype stops at the first inconsistency and returns HTTP 200 with `valid=false`, a stable failure reason and `failureSequence`. HTTP errors indicate that verification could not be performed.

## Stable failure reasons

- `UNEXPECTED_FIRST_SEQUENCE`
- `SEQUENCE_GAP`
- `PREVIOUS_HASH_MISMATCH`
- `CONTENT_HASH_MISMATCH`
- `CHAIN_HEAD_MISMATCH`
- `UNSUPPORTED_CANONICALIZATION_VERSION`
- `UNSUPPORTED_HASH_ALGORITHM`
- `MISSING_ARCHIVE_PROOF`
- `ARCHIVE_CHECKSUM_MISMATCH`
- `ARCHIVE_SIGNATURE_INVALID`
- `ARCHIVE_RANGE_INVALID`
- `ARCHIVE_CHAIN_INVALID`
- `ARCHIVE_BOUNDARY_MISMATCH`

Direct database tamper tests cover payload modification, deletion and reordering and assert the first invalid sequence. Runtime-role tests separately prove that the normal application role cannot update or delete an event.

The repeatable demonstration first verifies a legitimately archived range, then proves that direct payload modification produces `CONTENT_HASH_MISMATCH` and deletion produces `SEQUENCE_GAP`. Run it with `bash scripts/demo-tampering.sh`; the fixture truncates PostgreSQL state before each test and removes its temporary archive directory after the suite.

## Archives

Verification of an archived range requires the immutable event object or range digest, manifest digest, sequence boundaries, boundary hashes, versions and archive identity. Legitimate archival is not tampering when the online boundary metadata and immutable manifest verify the movement.

## Signed anchors

A production strengthening option is to periodically sign a canonical tuple containing tenant/chain ID, latest sequence, latest hash, algorithm/canonicalization versions and anchor timestamp. Store the signature and key identifier outside the primary database. Verification can compare current history with the latest anchor to detect a privileged rewrite of both events and chain head.
