# Business Context

## Business Objective

The service exists to provide trustworthy evidence of activity, not merely centralized logging. It must preserve a reviewable history of what happened, who or what caused it, which resource was affected, the relevant event detail, and when it occurred. That history must support detection of unauthorized changes while still accommodating the assignment's retention, privacy, export, and compliance-reporting needs.

## Decisions and Investigations the Evidence Must Support

Authorized reviewers must be able to determine:

- What event occurred and when.
- Who or what caused the event.
- Which resource was affected.
- Whether related events can be found for an actor, resource, event type, or time range.
- Whether the recorded history remains intact.
- Where the first inconsistency appears when integrity verification fails.
- Whether missing or altered information resulted from tampering or an authorized retention action.
- Whether sensitive details were handled through the approved redaction behavior.
- Whether an exported set of records has changed since export.
- What “audit access to client account data” means after Scenario C is clarified, including implemented scope and exclusions.

## Business Risks Addressed

### Records changed without detection

The organization could no longer rely on the audit history to establish what occurred. The assignment specifically requires modification of a past record to invalidate its record hash and following hashes, and requires verification to identify the first inconsistency.

### Records lost without an accountable policy action

A reviewer may be unable to distinguish “no event occurred” from “evidence disappeared.” Scenario B therefore requires legitimate archival to be handled without a false-positive break; the precise distinction between authorized retention and unauthorized loss remains an open question.

### Sensitive information exposed or redacted without accountability

Audit payloads may contain account numbers or personal identifiers. Scenario B requires those fields to be redactable without breaking tamper-evidence. Who may authorize or view redaction is not specified and remains an open question.

### Evidence exported without independent verification

A recipient could receive records whose integrity cannot be established. Scenario B requires a self-contained bundle with enough chain metadata for independent verification.

## Stakeholder Needs

The needs below are limited to behavior supported by the assignment or necessary clarification of that behavior.

| Stakeholder | Write-path needs | Search needs | Evidence needs | Administrative needs |
|---|---|---|---|---|
| Application teams | Submit the required event fields; receive clear acceptance or validation results; no update/delete path. | Find events by actor, resource, event type, and time range with pagination. | Confirm that submitted events appear in the append-only history. | Maintain event definitions and document timestamp behavior. |
| Security | Capture security-relevant actions with attributable actors and affected resources. | Reconstruct activity across actors, resources, event types, and time periods. | Verify the chain and locate the first inconsistency after tampering. | Investigate integrity failures and control access to sensitive audit records. |
| Compliance | Capture sufficient information for the clarified client-account-access requirement. | Produce repeatable populations for the clarified reporting scope. | Demonstrate trustworthy records, authorized retention, redaction behavior, and verifiable exports. | Define reporting scope, assumptions, exclusions, and retention expectations. |
| Operations | Observe failed writes, delayed processing, and verification failures. | Diagnose service and data-store behavior affecting writes, queries, and verification. | Show that maintenance or recovery did not create an unexplained chain break. | Operate retention configuration and recovery procedures under controlled oversight. |
| Privacy | Limit unnecessary sensitive data and identify redactable payload fields. | Locate records containing data subject to approved privacy handling. | Show that sensitive values are no longer visible while integrity verification remains valid. | Define redaction authorization, scope, and limitations. |
| Internal auditors | Rely on systematic event capture independent of update/delete operations. | Filter and sample records across the supported criteria. | Independently assess chain integrity, lifecycle actions, and export verifiability. | Obtain appropriately scoped read access and review relevant control evidence. |
| External regulators | No ordinary event-writing need is stated; they need confidence that required activity was captured. | Review the clarified population of client-account-access events. | Receive evidence whose integrity and scope can be explained and independently checked. | Use a controlled access or export process defined during Scenario C clarification. |

## Cross-Stakeholder Decisions Requiring Ownership

- Application teams supply events, while compliance must clarify which events represent access to client account data.
- Operations may execute retention behavior, while compliance and privacy must define when retention or redaction is legitimate.
- Security investigates integrity failures, while internal auditors assess whether the evidence and controls are trustworthy.
- Compliance defines export scope, privacy constrains sensitive disclosure, and the recipient must be able to verify the bundle independently.
- Access to audit records, administrative actions, and regulator access require clarification because the assignment does not define authorization behavior.

## Business-Facing Prototype Outcomes

The measurable outcomes are maintained in [requirements.md](requirements.md#business-facing-success-criteria-and-traceability). Every outcome maps to an explicit Scenario A, B, or C requirement.
