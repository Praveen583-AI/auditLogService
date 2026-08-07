# Threat model

## Security objective

The platform provides tamper-evident audit evidence. Hash chaining makes modification, deletion, insertion and reordering detectable during verification; it does not make database compromise or a privileged full-history rewrite impossible.

## Protected assets

- Audit-event content and trusted actor/tenant provenance
- Per-chain sequence order, previous hashes and chain heads
- Idempotency receipts
- Archive objects and archive manifests
- Verification outputs and implemented audit-of-audit action records
- Database credentials, archive credentials and signing keys

## Trust boundaries and threat actors

The table describes the target control set. External anchors,
retention-locked object storage, non-exportable KMS keys, and independent
key-use auditing are **production enhancements**, not implemented prototype
controls. Implemented status is summarized in `security-review.md`.

| Boundary or actor | Threat | Primary control | Detection or recovery |
|---|---|---|---|
| Producer to public API | Spoofed tenant/actor, oversized or malicious payload | Verified identity context, authorization and bounded validation | Rejected-request metrics and sanitized logs |
| Application runtime | Bug or compromised runtime attempts event mutation | `audit_app` cannot update/delete `audit_event` | Database permission error and operational alert |
| Database administrator | Direct row modification, deletion or chain-head rewrite | Separate privileged access and accountable operations | Streaming chain verification and external anchors |
| Retention service / future worker | Deletes before archive is durable or exceeds policy scope | Implemented maintenance role and range validation; production worker authorization is future | Archive manifest, lifecycle action record and boundary verification |
| Archive operator | Replaces or removes archived ranges | Immutable/retention-locked objects and separate access control | Manifest digest, online boundary metadata and external anchor |
| Log/monitoring operator | Learns regulated payload or identity data | Logging allowlist and centralized sanitization | Log scanning and access review |
| Signing-key operator | Signs a rewritten chain history | KMS-controlled non-exportable key and separated authorization | Independent anchor store and key-use audit |

## Database privilege model

- Migration ownership is separate from runtime identities.
- `audit_app` may insert and read events, update the chain head and complete idempotency records. It cannot update, delete or truncate audit events.
- `audit_verifier` is read-only.
- `audit_maintenance` is reserved for approved retention/archive workflows and is never used by public requests.
- Production login roles and credentials are provisioned outside migrations. The migration creates only `NOLOGIN` group roles.
- Every future migration must explicitly review and grant privileges for new tables; ownership is not delegated to runtime roles.

Database privileges prevent normal runtime mutation. They do not prevent the table owner, superuser or compromised migration identity from rewriting history.

## Archive and external trust

Archived event ranges and manifests should be stored in immutable or retention-locked storage under credentials separate from the API runtime. The online database retains chain ID, tenant, sequence range, first previous hash, final content hash, manifest digest, archive object identifier and integrity/canonicalization versions.

Production may periodically sign chain-head or archive-manifest digests with a non-exportable key in a key-management system and publish the signed anchor to an independently controlled location. Anchors strengthen detection of privileged full-history rewrites; they do not provide distributed consensus.

## Response

A failed verification is evidence requiring investigation. Preserve the first failure reason and sequence, freeze relevant retention jobs, protect database/archive snapshots, rotate compromised credentials where applicable and compare the chain against the latest independent anchor.
