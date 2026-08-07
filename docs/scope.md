# Prototype Scope

## Scope Principle

The two-to-three-day prototype must demonstrate the assignment's required behavior with depth, tests, and clear assumptions. Optional production enhancements are not acceptance requirements.

## Implement in the Prototype

| Capability | Reason |
|---|---|
| Write API with the six minimum event fields | Explicit Scenario A requirement and foundation for all demonstrations. |
| Documented timestamp choice | Explicit decision required by the assignment. |
| Append-only API with no update or delete operation | Core Scenario A behavior. |
| Query filters and supported combinations | Explicit Scenario A requirement. |
| Pagination | Explicit requirement for large result sets. |
| Record content hash, preceding-record hash, and genesis value | Explicit tamper-evidence requirements. |
| Deterministic record order | Necessary to identify the immediately preceding record; the chosen rule must be documented. |
| `GET /v1/audit/events/chains/{chainId}/verification` for intact and broken chains | Implemented Scenario A endpoint and validation mechanism. |
| Direct data-store tampering demonstration | Explicitly prescribed validation sequence. |
| Explicit contiguous-range archival service | Implemented prototype retention mechanism. Automatic cutoff calculation from a configurable retention window remains unimplemented. |
| One retention behavior: archival or soft deletion | The assignment permits either; implementing both is unnecessary in this timebox. |
| Verification after legitimate retention | Explicit protection against false-positive chain breaks. |
| Structured redaction of selected `payload` fields | Explicit Scenario B requirement. |
| Verification after authorized redaction | Demonstrates that privacy handling does not destroy tamper-evidence. |
| Bulk Export by `resourceId` and `actorId` | Explicit Scenario B requirement. |
| Self-contained export verification and alteration detection | Explicit independent-verification requirement. |
| Documented partial Scenario C boundary | Scenario C remains unclarified; the prototype records questions and reuses generic query/export building blocks without claiming a regulator report. |
| Unit and integration tests, API/schema definitions, and runnable setup | Explicit engineering deliverables. |

## Design and Document Only

| Capability or decision | Reason |
|---|---|
| Complete Scenario C business definition | Intentionally under-specified; clarification, assumptions, and a scope boundary are required. |
| Regulator-facing access model | Regulator identity, authorization, report format, and access method are unresolved. |
| Production scale and performance sizing | No quantitative workload or service-level targets are supplied. |
| Chain scope, concurrent ordering, and canonical hash representation | Required design assumptions are not prescribed by the assignment. |
| Violation taxonomy beyond the implemented cases | The assignment requires a violation type but does not define a complete taxonomy. |
| Retention mechanisms not selected for the prototype | The assignment does not require both archival and soft deletion. |
| Production legal-hold policy and regulatory-retention overrides | A simple append-only legal-hold mechanism is implemented; jurisdictional policy, approval, and override semantics remain unresolved. |
| Enterprise redaction policy | Eligible fields, authorization, reversibility, and preservation obligations are unresolved. |
| Export completeness, delivery, trust, and chain-of-custody rules | The assignment requires non-alteration verification but leaves these broader expectations open. |
| Production security, deployment, recovery, and operating model | Document as limitations or future decisions; local end-to-end operation is the required deliverable. |
| AI-use governance evidence | Record prompts and accepted, modified, or rejected output; no governance product is required. |
| Architecture overview and final engineering summary | Explicit documentation deliverables, not runtime capabilities. |

## Out of Scope

- External application or consumer; the assignment says API validation is sufficient.
- Microservices.
- Kafka or another event-streaming platform.
- Elasticsearch or a separate search platform.
- Blockchain or distributed ledger; the required mechanism is a hash chain.
- Multi-region deployment and geographic failover.
- Custom UI or regulator portal.
- Production identity-provider integration or enterprise role-management product.
- General analytics, dashboarding, fraud detection, or anomaly detection.
- Real-time alert-delivery platform.
- Multiple retention mechanisms.
- Every possible redaction scheme.
- Full legal-hold workflow.
- Production deployment infrastructure.
- External archive, reporting, case-management, or regulatory-system integrations.
- General event update or delete APIs.
- Autonomous approval of high-impact changes.

## End-to-End Demonstration Boundary

The prototype should demonstrate:

1. Record representative events.
2. Query them through every required filter and pagination.
3. Verify an intact history.
4. Alter a stored record directly and identify the first inconsistency.
5. Apply the selected retention behavior and verify without a false-positive break.
6. Redact a designated sensitive field and demonstrate privacy behavior plus continued verification.
7. Export by actor and resource, verify the bundle, alter it, and detect the alteration.
8. Demonstrate one clarified Scenario C use case and document excluded behavior.

See [acceptance-criteria.md](acceptance-criteria.md) for observable checks.
