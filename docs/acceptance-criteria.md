# Acceptance Criteria

A prototype criterion is accepted only when it is observable through an API call, database inspection, automated test, exported artifact, or required documented assumption.

## Scenario A — Core Audit Log Service

### Implemented Acceptance Criteria

| ID | Criterion | Evidence |
|---|---|---|
| A-1 | A valid event containing `eventType`, `actorId`, `resourceType`, `resourceId`, `payload`, and `timestamp` is accepted and stored. | Write API response and database inspection. |
| A-2 | Missing or invalid required fields are rejected and no event is stored. | Negative API test and database inspection. |
| A-3 | The documented timestamp policy is applied consistently. | API call, stored record, and documented assumption. |
| A-4 | The service exposes no event update or delete operation. | API definition and negative tests. |
| A-5 | Events can be filtered by `actorId`, `resourceType` plus `resourceId`, `eventType`, and `from` / `to`, individually and in supported combinations. | Query API integration tests. |
| A-6 | Pagination retrieves a multi-page result set without unexpected omissions or duplicates. | Pagination integration test. |
| A-7 | Each stored event has its content hash and preceding-record hash; the first uses the documented genesis value. | Database inspection. |
| A-8 | `GET /v1/audit/events/chains/{chainId}/verification` reports an intact chain for unmodified records. | Verification API test. |
| A-9 | Direct modification of a stored event causes verification to report a broken chain. | Database modification followed by API verification. |
| A-10 | Verification identifies the first inconsistent record and a supported violation type. | Tampering integration test. |
| A-11 | Hash inputs, ordering, genesis value, and supported violation types are documented. | Architecture documentation. |

### Architecture-Only Recommendations

Not required for prototype acceptance:

- Partitioned or tenant-specific chains.
- Distributed or multi-region ordering.
- Asynchronous high-volume ingestion.
- Continuous verification and automatic alerting.
- External identity-provider integration.
- Quantitative production service-level objectives.

## Scenario B — Retention, Redaction, and Bulk Export

### Application-service acceptance criteria

These capabilities are implemented and tested below the HTTP layer. The
prototype does not expose retention, redaction, or export controllers.

| ID | Criterion | Evidence |
|---|---|---|
| B-1 | An explicit closed contiguous range can be archived under a supplied policy identifier. Automatic cutoff calculation from a configurable retention window is not implemented. | Retention service integration test and documented limitation. |
| B-2 | An eligible explicit range can be archived and removed from hot storage only after bundle verification and manifest publication. | Retention action and database inspection. |
| B-3 | An invalid, partial, head-inclusive, overlapping, or legally held range is rejected. | Negative retention tests and repository constraints. |
| B-4 | Legitimately retained records do not create a false chain break. | Retention integration test and verification API call. |
| B-5 | Unauthorized direct removal or modification remains detectable. | Database tampering and verification test. |
| B-6 | A configured sensitive `payload` field can be redacted through the approved prototype behavior. | Redaction action and retrieval inspection. |
| B-7 | The redacted value is absent from the query-service view and generated export. | Service response and exported artifact inspection. |
| B-8 | Redaction does not modify the original event or its hash-chain fields. A dedicated redaction-plus-chain-verification test is not present. | Original-row inspection in `PrivacyIntegrationTest` and separate chain-verification coverage. |
| B-9 | Redactable fields, authorization assumption, approach, trade-offs, and limitations are documented. | Architecture documentation. |
| B-10 | The export service returns records selected by either `resourceId` or `actorId`. | Export service integration test. |
| B-11 | The export is self-contained and contains the metadata required by its verifier. | Artifact inspection. |
| B-12 | An unchanged export passes independent verification. | Export verification test. |
| B-13 | Altering an exported record causes verification to fail. | Modified artifact and verification test. |
| B-14 | Redacted active records and archive manifests are included under documented rules. Exporting the full contents of archived ranges is not implemented or tested. | `PrivacyIntegrationTest`, export implementation, and documented limitation. |

### Architecture-Only Recommendations

Not required for prototype acceptance:

- Multiple archival tiers or both retention mechanisms.
- Production legal-hold approval and case workflow. A simple append-only hold
  action model and retention precedence check are implemented.
- Enterprise privacy-request workflow.
- External archive-provider integration.
- External trust infrastructure beyond the selected export design.
- Regulator-specific delivery channels.
- Proof of export completeness beyond required proof of non-alteration.

## Scenario C — Compliance Reporting

### Documented and partial acceptance criteria

Scenario C is not a completed regulator-reporting implementation. The
assignment statement, ambiguities, and boundaries are documented; ordinary
events can represent account access, and compliance roles can create bounded
exports. A confirmed compliance population, reporting-period contract, and
regulator-facing endpoint are not implemented.

| ID | Criterion | Evidence |
|---|---|---|
| C-1 | “Audit access to client account data” is retained as an unresolved assignment statement rather than silently converted into code. | Requirements and open-questions documents. |
| C-2 | “Access,” “client account data,” relevant actors, and reporting period are recorded as unresolved questions. | `open-questions.md`. |
| C-3 | Ambiguities and unanswered product-owner questions are recorded. | `open-questions.md`. |
| C-4 | Implemented and excluded behavior is stated with rationale. | Scope documentation. |
| C-5 | Representative account-access-shaped events can be recorded using the generic event contract; this does not establish the regulatory population. | Write API tests and stored records. |
| C-6 | No confirmed compliance-population query is implemented. Generic filters and bounded actor/resource exports are reusable building blocks only. | Explicit documented limitation. |
| C-7 | Generic results contain actor, resource, and time fields, but no regulator-approved report exists. | API contract and documented limitation. |
| C-8 | In-scope/out-of-scope regulatory classification is not tested because its business definition is unresolved. | Explicit documented limitation. |
| C-9 | Generic query and export results retain event identifiers; regulator evidence traceability remains unvalidated. | Response/artifact inspection and documented limitation. |
| C-10 | Any partial implementation has an explicit, reasoned scope boundary. | Scenario C documentation and final summary. |

### Architecture-Only Recommendations

Not required without further clarification:

- Regulator self-service portal or direct external-regulator login.
- Jurisdiction-specific report packages.
- Automated regulatory submission.
- Case-management or investigation workflows.
- Scheduled compliance reporting.
- Enterprise access certification.
- Legal-discovery workflows.
- Broad analytics or dashboards.
- External compliance-platform integration.

## Acceptance Boundary

Architecture recommendations are not implemented requirements. They must not be presented as complete, tested, or mandatory within the two-to-three-day prototype.
