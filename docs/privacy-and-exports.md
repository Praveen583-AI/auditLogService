# Privacy overlays and verifiable exports

## Prototype behavior

Redaction is an append-only overlay. `AuditEvent` and its content hash remain unchanged. A normal query replaces an authorized JSON Pointer with `[REDACTED]`; a compliance officer or audit administrator can recompute the stored HMAC-SHA-256 commitment against the immutable original. The per-record random nonce prevents equality disclosure, while the secret commitment key prevents practical offline guessing of low-entropy values.

Exports are tenant-scoped and require `COMPLIANCE_OFFICER` or `AUDIT_ADMIN`. A bundle contains the selected records as viewed through redaction overlays, redaction proofs, archive manifests, chain-head snapshot boundaries, section checksums, expiry metadata, verification instructions, and an Ed25519 signature. The public key allows verification without access to the live service. Download tokens are random, stored only as hashes, and expire. Request, completion, failure, download, and denied-download actions are append-only records.

## Explicit limits

- An overlay hides data from supported views; it is not irreversible deletion or crypto-shredding. The original remains in the hot event or its protected archive.
- This prototype supports redacting existing object fields only. It does not redact array elements or prove that an archived payload was erased.
- Export expiry prevents later service downloads but cannot revoke a bundle already downloaded.
- File storage is a local prototype adapter. Production requires immutable object storage, isolated signing keys in a key-management system, and durable job execution.
- An export proves integrity of its included sections and the chain-head boundaries captured at `snapshotAt`; it does not by itself prove that the service operator included every event unless boundaries and archive evidence are independently trusted.

## Secret handling

`audit.privacy.commitment-key-base64` and `audit.export.signing-private-key-base64` must come from environment-backed secret management and must never be committed. The Ed25519 public verification key and key identifier may be distributed with verification tooling. Keys are versioned so old commitments and bundles remain verifiable after rotation.
