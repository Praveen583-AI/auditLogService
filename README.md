# Audit Log Service

A Spring Boot and PostgreSQL prototype that records immutable audit events in a
per-tenant SHA-256 hash chain. It demonstrates atomic idempotent append,
deterministic verification, tenant-scoped authorization, verifiable archival,
redaction overlays, and signed bounded export bundles.

## Validation

Run the complete suite from a clean checkout with Java 17, Maven, and a running
Docker engine:

```sh
mvn --batch-mode --no-transfer-progress clean test
sh scripts/demo-tampering.sh
gitleaks git --config .gitleaks.toml --redact .
```

PostgreSQL-backed tests use Testcontainers. Requirement-to-test evidence is
listed in [docs/test-traceability.md](docs/test-traceability.md).

## Security and limitations

This is a bounded prototype, not a production audit platform. In particular:

- Hash chaining makes changes detectable; it does not prevent a privileged
  administrator from rewriting all database history and chain heads.
- Retention, redaction, and export are implemented and tested as application
  services but are not exposed through production internal controllers.
- Archive and export artifacts use local filesystem adapters; production needs
  durable retention-locked storage.
- Signing and commitment keys require external secret and key management in
  production. No real key belongs in this repository.
- Redaction is a presentation overlay and does not irreversibly delete the
  original value.
- Verification and export are bounded synchronous prototype operations, not
  demonstrated production-scale workers.
- Rate limiting, external chain-head anchors, deployment network policy,
  managed-key custody, multi-region recovery, and a regulator portal are
  documented production enhancements, not implemented controls.
- Regulator identity, reporting scope, and the external access contract remain
  unresolved; regulator-ready access is not claimed.

See [docs/security-review.md](docs/security-review.md) for the final residual
risk assessment and repository-sharing checklist.
