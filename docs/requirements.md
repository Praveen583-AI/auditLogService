# Audit Log Service Requirements

## Objective

Build a working prototype of a tamper-evident audit log service that records an append-only history of events and makes modification or deletion of past records detectable.

## Functional Requirements

### Scenario A — Greenfield: Core Audit Log Service

#### Write API

Accept an event record containing, at minimum:

- `eventType` — what happened.
- `actorId` — who or what caused the event.
- `resourceType` — the type of resource affected.
- `resourceId` — the specific resource affected.
- `payload` — a structured object with event-specific detail.
- `timestamp` — when the event occurred.

Records are append-only. The API must not expose update or delete operations.

#### Query API

Retrieve events using any combination of:

- `actorId`.
- `resourceType` and `resourceId`.
- `eventType`.
- Time range using `from` / `to`.

Support pagination for large result sets.

#### Tamper Evidence — Hash Chain

Each stored record must include:

- A hash of its own content, covering the event fields above.
- A hash of the immediately preceding record, or a defined genesis value for the first record.

Modification of a past record must invalidate its own hash and every hash that follows it.

#### Chain Verification Endpoint

Expose `GET /audit/verify`. It must walk the full chain and report:

- Whether the chain is intact.
- If broken, the first inconsistent record.
- The type of violation detected.

The validation sequence is: write events, query them, verify the chain, modify a record directly in the data store, and verify again to confirm detection. No external application or consumer is required.

### Scenario B — Extend Your Own System: Retention and Redaction

#### Retention Policy

- Records older than a configurable window should be archivable or soft-deletable.
- Verification must handle archived records correctly.
- Legitimately archived records must not cause a false-positive chain break.

#### Structured Redaction

- Sensitive fields within `payload` must be redactable.
- Redaction must satisfy data privacy requirements without breaking the hash chain.
- Document the approach, considered trade-offs, and limitations.

#### Bulk Export

- Export all records for a given `resourceId` or `actorId`.
- Produce a self-contained, verifiable bundle.
- Include enough chain metadata for independent verification that the exported records have not been altered since export.

### Scenario C — Ambiguous: Compliance Reporting

Starting statement:

> Regulators need to be able to audit access to client account data.

Before writing Scenario C code:

- Clarify and normalize the requirement.
- Identify ambiguities.
- Record assumptions made or questions that would be asked.
- Translate the clarified requirement into a concrete technical design.
- State what is implemented and what is scoped out, with rationale.
- Include an implementation or a well-reasoned partial implementation with a documented scope boundary.

## Non-Functional Requirements

- Produce production-quality code, API/schema definitions, unit/integration tests, and supporting documentation.
- Use clean design and maintainability.
- Identify risks, trade-offs, and failure scenarios.
- Define validation and safety guardrails.
- Apply quality gates covering analysis, linting, tests, security, and performance.
- Interpret intent, identify ambiguity, and normalize requirements.
- Decompose work into actionable tasks with dependencies and sequencing.
- Define tasks with intent, constraints, acceptance criteria, and technical context.
- Use disciplined AI prompting with iterative refinement.
- Maintain AI-use traceability: generated, edited, and rejected output with rationale.
- Require human sign-off for high-impact changes.
- Retain engineer ownership of correctness, maintainability, production readiness, quality, and authorship.
- Keep the prototype runnable end-to-end with local setup instructions.

## Constraints

- Complete the work over 2–3 days.
- Cover Scenarios A, B, and C.
- Work in a Git repository from the start and submit the private repository with authentic development history.
- Submit the repository, not a zip, tarball, or snapshot.
- Complete the assignment individually on the candidate's own machine and accounts.
- Keep the assignment, problem, and solution confidential.
- The engineer leads and approves execution; AI assists within tasks.

The following choices must be made and documented:

- Caller-supplied or server-assigned `timestamp`.
- Genesis value.
- Hash algorithm and chain design.
- Archival, soft deletion, or both.
- Structured redaction scheme.
- Scenario C scope boundary.

## Cross-Requirement Dependencies

- Chain scope, ordering, timestamp ownership, canonical hashed content, genesis value, and record identity must be settled before defining storage.
- Append and chain verification should be implemented and validated together.
- Retention semantics must be defined before verification can correctly handle archived records.
- Redaction design must precede any transformation of hash-covered payload content.
- Bulk Export depends on stable chain, retention, and redaction semantics.
- Scenario C must be clarified before changing event capture or implementing compliance behavior.

## Requirement Tensions

- Append-only behavior conflicts with physical deletion unless sufficient verification evidence remains.
- Redacting a hash-covered value conflicts with simple hash recomputation.
- Full-chain verification depends on evidence retained after archival or redaction.
- A filtered export may contain non-contiguous records from a global chain.
- Regulator reporting may require event fields beyond Scenario A's minimum.
- Regulatory retention or legal hold may conflict with the configurable retention window.

## Explicit Deliverables

- `ATTESTATION.md`.
- Working prototype runnable end-to-end.
- Setup instructions.
- Architecture overview.
- API and schema definitions.
- Unit and integration tests.
- Three scenarios showing decomposition, execution, and validation.
- Testing approach, limitations, and trade-offs.
- AI usage log / traceability notes.
- Final engineering summary covering plan/rationale, artifacts, risks/trade-offs, validation, assumptions, and limitations.

## Business-Facing Success Criteria and Traceability

| ID | Demonstrable business outcome | Assignment requirement |
|---|---|---|
| BO-1 | Authorized applications can record what happened, who caused it, what was affected, relevant details, and when it occurred. | Scenario A — Write API and its minimum event fields. |
| BO-2 | Recorded events cannot be changed or deleted through the service. | Scenario A — records are append-only; no update or delete operation. |
| BO-3 | Authorized reviewers can find events by actor, affected resource, event type, and time range, including paging through large results. | Scenario A — Query API filters and pagination. |
| BO-4 | The service can confirm trustworthy history and identify the first affected record after direct data-store tampering. | Scenario A — hash chain, `GET /audit/verify`, and prescribed tampering validation. |
| BO-5 | Records can be handled under the configured retention policy without a false tampering result. | Scenario B — Retention Policy and verification of legitimately archived records. |
| BO-6 | Sensitive payload details can be redacted while the audit history remains verifiable. | Scenario B — Structured Redaction. |
| BO-7 | Records for a selected actor or resource can be exported in a bundle that a recipient can independently verify. | Scenario B — Bulk Export. |
| BO-8 | The prototype presents a clarified and demonstrable way to audit access to client account data, with explicit scope and exclusions. | Scenario C — Compliance Reporting clarification, design, and implementation or documented partial implementation. |

Each business outcome above maps to an explicit assignment requirement. Prototype acceptance checks should be derived from these outcomes without expanding their scope.
