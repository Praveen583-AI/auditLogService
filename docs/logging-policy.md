# Operational logging policy

## Allowlist

Operational logs may contain:

- Correlation ID
- Stable operation and error code
- Event ID, chain ID, sequence number and job ID
- Verification status, first failure reason and failure sequence
- Retry attempt and bounded timing information
- Authorization decision reason without token or claim contents

## Prohibited values

Never log:

- Audit payloads or removed/redacted values
- Bearer tokens, session identifiers, passwords or credentials
- Raw idempotency keys or request fingerprints
- Private/signing keys, encryption material or signed download URLs
- Full JWT claims, database exception SQL text or connection strings with credentials
- Arbitrary cursor values
- Regulator export contents
- Actor identity attributes beyond an approved pseudonymous support identifier

Hash values are returned where the API contract requires an integrity receipt, but routine operational logging should prefer event/chain/sequence identifiers and not emit full content or previous hashes.

## Failure handling

- Log one sanitized boundary event with correlation ID and stable code.
- Do not log request bodies or exception stack traces for expected validation, authorization, concurrency or database-availability failures.
- Framework and connection-pool loggers that print SQL exception details remain constrained by configuration.
- Unexpected exceptions use a fixed external error response; detailed diagnostics belong in access-controlled error monitoring with the same data restrictions.

## Audit-of-audit actions

The prototype persists append-only archive lifecycle actions, legal-hold
actions, redaction records, and export access actions. Verification produces a
sanitized operational log and response but no persistent verification-history
record. Privileged database maintenance and regulator evidence access do not
have implemented application workflows. Persisting those activities as
audit-of-audit events is a production recommendation. No action record should
contain exported or redacted payload values.

Log access is restricted and reviewed. Retention for operational logs is independent from regulated audit-event retention.
