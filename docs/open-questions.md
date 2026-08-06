# Open Questions

These questions are prioritized by their potential to materially change the architecture. Each question must be answered or resolved through a documented assumption before implementation proceeds.

## Priority Clarification Questions

1. **What event volume, peak write/query rates, and retention duration should the service support?**  
   **Risk of proceeding:** Storage, pagination, verification performance, and retention behavior may be unsuitable for the expected scale.

2. **Is ordering and hash chaining global, or scoped per tenant, account, resource, or another boundary?**  
   **Risk of proceeding:** This determines concurrency, failure isolation, chain verification, filtered export, and whether writes become unnecessarily serialized.

3. **What ordering guarantee is required for concurrent or late-arriving events, and is `timestamp` caller-supplied or server-assigned?**  
   **Risk of proceeding:** The authoritative chain order may conflict with the expected business-event order.

4. **What exact event representation is covered by the record hash?**  
   **Risk of proceeding:** Inconsistent serialization or field ordering can make valid records unverifiable.

5. **What may happen when the retention window expires: archival, soft deletion, payload removal, or physical deletion?**  
   **Risk of proceeding:** Retention could violate append-only behavior or make full-chain verification impossible.

6. **Must archived records remain queryable or exportable, and what verification evidence must survive archival?**  
   **Risk of proceeding:** The archive may not retain enough information for later audits, verification, or export.

7. **How does verification distinguish legitimate retention from unauthorized removal?**  
   **Risk of proceeding:** Verification may report false-positive breaks or incorrectly accept tampering.

8. **Which `payload` fields may be redacted, who may authorize redaction, and must the original value become permanently unrecoverable?**  
   **Risk of proceeding:** Different definitions of redaction require fundamentally different storage, authorization, and verification semantics.

9. **After redaction, must verification prove the original record existed unchanged, that the redaction was authorized, or both?**  
   **Risk of proceeding:** The design may satisfy privacy but fail tamper-evidence, or preserve tamper-evidence while failing privacy.

10. **Who are the “regulators,” and what does “audit access to client account data” mean in terms of actors, actions, data categories, reporting periods, and jurisdiction?**  
    **Risk of proceeding:** The service may capture insufficient evidence, expose prohibited information, or address the wrong compliance obligation.

11. **Do regulatory retention or legal-hold requirements override the configurable retention window or redaction requests?**  
    **Risk of proceeding:** Routine retention or redaction could destroy records required for regulatory review.

12. **For Bulk Export, does “all records” include archived or soft-deleted records, and must the bundle prove completeness as well as non-alteration?**  
    **Risk of proceeding:** A bundle may verify the records it contains without showing whether qualifying records were omitted.

13. **Must a recipient verify an export completely offline, and what establishes trust in the bundle's chain metadata?**  
    **Risk of proceeding:** The bundle may still depend on the service or lack a trustworthy verification basis.

14. **How should an export prove records selected by `resourceId` or `actorId` when they are non-contiguous in the stored chain?**  
    **Risk of proceeding:** A filtered bundle may be unverifiable without including additional chain evidence.

15. **What should `GET /audit/verify` return at the expected scale, and may it use retained evidence for archived or redacted records?**  
    **Risk of proceeding:** The endpoint may be operationally too slow or produce incorrect results after legitimate lifecycle operations.

## Additional Scenario A Questions

- What stable identifier is used to report the first inconsistent record?
- What violation types must verification distinguish?
- What is the defined genesis value?
- What pagination contract and default ordering are expected?
- Are `resourceType` and `resourceId` always used together?
- Are `from` and `to` inclusive or exclusive?
- What validation rules apply to the required fields and `payload`?
- How are concurrent writes assigned one authoritative predecessor?

## Additional Scenario B Questions

- What configuration and time semantics define the retention window?
- Is physical deletion ever permitted?
- Is a retention or redaction action itself represented in the append-only history?
- What data may remain as redaction metadata?
- How does authorization affect whether an export contains a redacted or original representation?
- What format constitutes the self-contained, verifiable bundle?
- Is bundle integrity verified as of export time or against the current service state?

## Additional Scenario C Questions

- What counts as “access”?
- Which resources constitute “client account data”?
- Which identities, roles, systems, or channels must be reported?
- Is the requirement for event capture, search, reporting, export, or all of these?
- What fields, filters, grouping, and output format are required?
- What authorization rules govern regulator access?
- What completeness, accuracy, reconciliation, or timeliness guarantees are expected?
- Does compliance reporting reuse the Query API or Bulk Export, or require separate behavior?

## Assignment-Level Clarifications

- The Objective references “multi-step autonomous orchestration” but also requires engineer-led execution “not autonomous orchestration.” Confirm that controlled, engineer-led AI assistance is the intended interpretation.
- Define “secure AI usage.”
- Define which changes are “high-impact” and require human sign-off.
- Provide quantitative production-readiness or performance expectations, if any.
