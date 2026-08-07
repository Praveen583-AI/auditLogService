# Requirement-based test traceability

Test completeness is evaluated against observable requirements rather than a
line-coverage percentage.

| Requirement | Automated or repeatable validation | Status and boundary |
|---|---|---|
| Atomic append-only writes | `AuditWriteIntegrationTest`, `AuditWriteServiceIntegrationTest`, `DatabaseRoleHardeningIntegrationTest`, and `ConcurrentWriteIntegrationTest` | Implemented: rollback, idempotent replay, runtime-role update/delete rejection, and contiguous per-chain ordering |
| Tamper detection | `HashChainTest`, `TamperDetectionIntegrationTest`, `ChainVerificationIntegrationTest`, and `scripts/demo-tampering.sh` | Implemented: modification, deletion, reordering, archive corruption, and first inconsistency |
| Retention and archival | `RetentionIntegrationTest` and archived-range cases in `ChainVerificationIntegrationTest` | Implemented as an application service: contiguous archive, legal-hold precedence, corrupted-object failure, and verification across a removed hot range |
| Redaction | `PrivacyIntegrationTest` | Implemented as an application service: normal view overlay, unchanged original evidence, and commitment verification; irreversible deletion is not claimed |
| Export | `PrivacyIntegrationTest` | Implemented for bounded prototype exports: authorization, expiry metadata, access actions, signature, and offline tamper rejection |
| Regulator access | `AuthorizationPolicyTest` and `SecurityIntegrationTest` | Partial: least-privilege compliance export authorization is validated; regulator identity, report scope, and a production endpoint are unresolved |

## Supporting quality checks

- Canonicalization vectors and version failure behavior:
  `CanonicalizerTest` and `CanonicalJsonAuditEventCanonicalizerV1Test`.
- Framework-independent chain behavior: `HashChainTest` and
  `AuditEventServiceTest`.
- Role, tenant, and resource scope: `AuthorizationPolicyTest` and
  `SecurityIntegrationTest`.
- Safe API errors and payload limits: `AuditEventControllerTest`.
- Cursor validation and deterministic pagination: `CursorCodecTest` and
  `ConcurrentWriteIntegrationTest`.
- Retry and uncertain database outcomes: `AuditWriteFailureMappingTest` and
  `AuditWriteFailureIntegrationTest`.
- PostgreSQL query-plan baseline: `QueryPlanBaselineIntegrationTest`.

Passing tests establish the listed prototype behavior only. They do not prove
production capacity, protection from a fully privileged administrator,
immutable external storage, managed-key isolation, or regulator suitability.
