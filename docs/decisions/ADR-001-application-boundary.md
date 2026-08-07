# ADR-001: Application Boundary

- **Status:** Accepted
- **Date:** 2026-08-06
- **Decision owners:** Prototype engineering owner
- **Related documents:** [Functional Architecture](../architecture/functional-architecture.md), [Scope](../scope.md), [Acceptance Criteria](../acceptance-criteria.md)

## Context

The assignment requires a working audit-log prototype in two to three days. The
prototype must demonstrate append-only writes, required queries, hash-chain
verification, direct tampering detection, retention, structured redaction, and
independently verifiable export. Scenario C remains a documented partial slice:
generic events and exports are reusable building blocks, but no confirmed
compliance population or regulator-facing report is implemented.

The write path must select one authoritative predecessor, construct integrity metadata, and persist the completed record before returning success. Retention and redaction must remain consistent with verification, query visibility, and export behavior.

No requirement establishes independent scaling, separate ownership, geographic distribution, or independent deployment as a prototype goal.

## Decision

Build a **modular monolith**:

- One application deployment.
- One authoritative audit-record persistence boundary.
- One atomic append path.
- Explicit internal modules for API Operations, Append Service, Integrity Rules, Audit Query, Chain Verification, Audit Record Lifecycle, Record View Policy, Bulk Export, Export Verification, and Compliance Query.
- Internal interfaces that prevent modules from bypassing another module's owned decisions.
- Tests aligned to module boundaries and end-to-end assignment scenarios.

The logical modules are not separate network services.

## Alternatives Considered

### Multiple Microservices

Rejected for the prototype.

Microservices would add:

- Network and partial-failure handling.
- Distributed transaction or compensation concerns.
- Cross-service ordering and consistency questions.
- Multiple local runtimes and deployment artifacts.
- Contract versioning and rollout coordination.
- Distributed observability requirements.
- Risk of duplicated integrity, retention, redaction, and view rules.

The append path would still require one authoritative owner, limiting the benefit of splitting it.

### Unstructured Single Application

Rejected.

A single application without explicit module boundaries would be quick initially but would obscure ownership of integrity, lifecycle, export, and compliance behavior. It would also make future extraction and focused testing harder.

## Consequences

### Positive

- More delivery time remains for required behavior and validation.
- Predecessor selection, hashing, and persistence can form one atomic operation.
- Local end-to-end testing requires one application.
- Retention, redaction, search, verification, and export can share consistent evidence rules.
- Deployment and diagnostics remain proportionate to the prototype.
- Clean internal boundaries demonstrate design discipline without operational overhead.

### Negative

- Modules cannot be independently deployed or scaled.
- All modules share an application release.
- Boundary discipline must be enforced through code structure, interfaces, and tests rather than network separation.
- Future extraction requires deliberate contract and data-ownership work.

### Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Modules become tightly coupled through direct data access. | Permit persistence access only through owned repository contracts. |
| Integrity rules are duplicated. | Use one deterministic Integrity Rules module for append, verification, and export proof behavior. |
| Search, compliance, and export expose inconsistent record views. | Use one Record View Policy. |
| Lifecycle modules approve their own evidence. | Chain Verification independently evaluates persisted lifecycle evidence. |
| The monolith is treated as permission to mix responsibilities. | Test module contracts and document owned decisions and dependencies. |

## Synchronization Decision

The following remain synchronous from the caller's perspective:

- Event append through durable persistence.
- Query and pagination.
- Chain verification.
- Prototype redaction.
- Compliance query.
- Prototype export.

Retention may be asynchronous, but the prototype may implement it synchronously to reduce state-transition complexity. Scheduled verification and production-scale export jobs are optional future behavior.

## Future Extraction Criteria

A module becomes an extraction candidate only after a demonstrated need such as:

- Independent scaling or availability.
- Independent ownership and release cadence.
- Security isolation.
- A stable contract with limited transactional coupling.
- Long-running workloads that should not share request capacity.

Bulk Export or Compliance Query may become candidates if those needs emerge. Append Service and Integrity Rules should remain together unless a future design preserves one authoritative append decision.

## Scope Exclusions

This decision does not require:

- Microservices.
- Kafka or another event-streaming platform.
- Elasticsearch or another separate search platform.
- Blockchain or distributed ledger.
- Multi-region deployment.
- Custom UI.
- Distributed tracing.

These remain optional production considerations only if future requirements justify them.
