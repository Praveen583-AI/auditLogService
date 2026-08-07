# Domain model diagrams

The diagrams show logical ownership and evidence flow. A relationship to an archived or missing event is deliberately not always a physical foreign key.

## Entity relationships

```mermaid
erDiagram
    CHAIN_HEAD ||--o{ AUDIT_EVENT : "orders active events"
    CHAIN_HEAD ||--o{ ARCHIVE_MANIFEST : "owns archived ranges"
    CHAIN_HEAD ||--o{ VERIFICATION_RESULT : "is verification scope"
    AUDIT_EVENT o|--o| IDEMPOTENCY_RECORD : "may be accepted result"
    AUDIT_EVENT ||--o{ REDACTION_ACTION : "has presentation actions"
    ARCHIVE_MANIFEST }o--o{ AUDIT_EVENT : "describes logical range"
    EXPORT_JOB }o--o{ AUDIT_EVENT : "selects evidence"
    EXPORT_JOB }o--o{ ARCHIVE_MANIFEST : "includes archived evidence"
    VERIFICATION_RESULT }o--o{ ARCHIVE_MANIFEST : "checks archived ranges"

    CHAIN_HEAD {
        string chain_id PK
        bigint last_sequence
        uuid last_event_id
        string last_hash
        bigint version
        timestamp updated_at
    }

    AUDIT_EVENT {
        uuid event_id PK
        string chain_id
        bigint sequence
        string event_type
        int event_schema_version
        string actor_id
        string resource_type
        string resource_id
        json payload
        timestamp occurred_at
        timestamp recorded_at
        string previous_hash
        string content_hash
        string hash_algorithm
        int canonicalization_version
    }

    IDEMPOTENCY_RECORD {
        uuid idempotency_id PK
        string scope_id
        string operation
        string idempotency_key_hash
        string request_fingerprint
        string status
        uuid event_id
        timestamp expires_at
    }

    ARCHIVE_MANIFEST {
        uuid archive_id PK
        string chain_id
        bigint first_sequence
        bigint last_sequence
        bigint event_count
        string predecessor_hash
        string terminal_hash
        string object_locator
        string object_digest
        string status
    }

    REDACTION_ACTION {
        uuid redaction_action_id PK
        uuid event_id
        string chain_id
        bigint event_sequence
        json field_paths
        string redaction_method
        string policy_version
        string status
        json proof_metadata
    }

    VERIFICATION_RESULT {
        uuid verification_id PK
        string verification_type
        string chain_id
        string status
        bigint records_checked
        bigint first_inconsistent_sequence
        timestamp completed_at
    }

    EXPORT_JOB {
        uuid export_job_id PK
        string selector_type
        string actor_id
        string resource_type
        string resource_id
        string status
        bigint snapshot_sequence
        string snapshot_head_hash
        string bundle_locator
        string bundle_digest
    }
```

### Relationship notes

- `ChainHead` to active `AuditEvent` is enforced by chain identity and the unique `(chain_id, sequence)` constraint; an append changes both in one transaction.
- `ArchiveManifest` identifies an event range logically. Physical foreign keys to first or last events would prevent legitimate archival removal.
- References from `RedactionAction`, `VerificationResult`, and `ExportJob` must remain meaningful after archival or when verification reports a missing event. They therefore use stable identifiers and proof metadata; physical foreign keys are conditional on the chosen retention design.
- The many-to-many lines for exports and verification express evidence inclusion. Join tables are required only if the prototype persists item-level membership rather than reproducible selectors and boundary metadata.

## State and evidence flow

```mermaid
flowchart LR
    Producer["Producer request"] --> Idempotency["IdempotencyRecord"]
    Idempotency --> Append["Atomic append"]
    Head["ChainHead"] <--> Append
    Append --> Event["Immutable AuditEvent"]

    Event --> Query["Search and reporting"]
    Event --> Verify["Chain verification"]
    Archive["Completed ArchiveManifest"] --> Verify
    Verify --> Result["VerificationResult response (not persisted)"]

    Event --> ArchiveProcess["Archive operation"]
    ArchiveProcess --> ArchiveObject["Archive bundle (local prototype adapter)"]
    ArchiveObject --> Archive

    Event --> View["Authorized event view"]
    Redaction["Completed RedactionAction"] --> View
    View --> Export["ExportJob and artifact"]
    Archive --> Export
```

Only the append path requires a synchronous transaction for event creation and head advancement. Verification, archival, and export may run asynchronously if their APIs expose job state and preserve stable boundaries. Redaction authorization and its evidence record must complete before a redacted response is presented.
