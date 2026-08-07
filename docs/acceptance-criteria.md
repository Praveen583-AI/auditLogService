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

### Implemented Acceptance Criteria

| ID | Criterion | Evidence |
|---|---|---|
| B-1 | The retention window is configurable and its time semantics are documented. | Configuration and documentation inspection. |
| B-2 | A record outside the window can undergo the selected archival or soft-deletion behavior. | Retention action and database inspection. |
| B-3 | An ineligible record is not processed by retention. | Negative retention test. |
| B-4 | Legitimately retained records do not create a false chain break. | Retention integration test and verification API call. |
| B-5 | Unauthorized direct removal or modification remains detectable. | Database tampering and verification test. |
| B-6 | A configured sensitive `payload` field can be redacted through the approved prototype behavior. | Redaction action and retrieval inspection. |
| B-7 | The redacted value is absent from normal retrieval and export. | API response and exported artifact inspection. |
| B-8 | Verification succeeds after authorized redaction. | Redaction integration test. |
| B-9 | Redactable fields, authorization assumption, approach, trade-offs, and limitations are documented. | Architecture documentation. |
| B-10 | Bulk Export returns records selected by either `resourceId` or `actorId`. | Export API tests. |
| B-11 | The export is self-contained and contains the metadata required by its verifier. | Artifact inspection. |
| B-12 | An unchanged export passes independent verification. | Export verification test. |
| B-13 | Altering an exported record causes verification to fail. | Modified artifact and verification test. |
| B-14 | Export behavior for archived and redacted records follows a documented assumption and is tested. | Documentation and integration tests. |

### Architecture-Only Recommendations

Not required for prototype acceptance:

- Multiple archival tiers or both retention mechanisms.
- Legal-hold workflow.
- Enterprise privacy-request workflow.
- External archive-provider integration.
- External trust infrastructure beyond the selected export design.
- Regulator-specific delivery channels.
- Proof of export completeness beyond required proof of non-alteration.

## Scenario C — Compliance Reporting

### Implemented Acceptance Criteria

| ID | Criterion | Evidence |
|---|---|---|
| C-1 | “Audit access to client account data” is converted into a clear requirement statement before Scenario C code. | Requirements document and repository history. |
| C-2 | “Access,” “client account data,” relevant actors, and reporting period are defined or recorded as assumptions. | Documented assumptions. |
| C-3 | Ambiguities and unanswered product-owner questions are recorded. | `open-questions.md`. |
| C-4 | Implemented and excluded behavior is stated with rationale. | Scope documentation. |
| C-5 | Representative access events can be recorded using the clarified definition. | Write API calls and stored records. |
| C-6 | The implemented compliance population can be retrieved using documented criteria. | Query or reporting API test. |
| C-7 | Results show who accessed which defined client-account resource and when, if included in the clarified scope. | API response or exported report. |
| C-8 | Results include in-scope test events and exclude known out-of-scope events. | Seeded data and acceptance test. |
| C-9 | Compliance results remain traceable to their audit records. | Response identifiers or artifact inspection. |
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
