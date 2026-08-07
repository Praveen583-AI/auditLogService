# Final security review

## Security conclusion

The prototype demonstrates tamper evidence and layered access restrictions; it
does not claim absolute tamper prevention or production readiness. Runtime
database roles cannot update or delete audit evidence, but a sufficiently
privileged administrator remains inside the residual threat boundary.

`AuditEvent` is immutable evidence and has no lifecycle status. Redaction,
archive transitions, legal holds, and export access are represented separately.
`ChainHead`, `IdempotencyRecord`, and `ExportJob` are mutable operational state;
their mutation does not rewrite an accepted audit event.

Retention, redaction, and export are implemented and tested as application
services. The current application does not expose production internal
controllers for them. Regulator identity, report population, and the external
access contract remain unresolved.

## Residual risks

| Risk | Disposition | Control status |
|---|---|---|
| A privileged administrator can rewrite database history and chain heads together | Mitigated, not eliminated | Runtime role restrictions and verification are implemented. **Production enhancement:** publish signed chain-head anchors outside the database administration boundary. |
| The authentication-disabled local profile could be deployed accidentally | Mitigated | Security is enabled by default. **Production enhancement:** deployment admission policy must reject the local profile. |
| Archive and export artifacts use local storage and application-held keys | Deferred | Checksums and signatures are implemented. **Production enhancement:** retention-locked object storage and KMS/HSM key custody. |
| Redaction hides a value but retains the immutable original | Accepted prototype limitation | Overlay and HMAC commitment are implemented. **Production enhancement:** field encryption and crypto-shredding if irreversible deletion is confirmed. |
| Full verification and export can consume substantial memory or time | Accepted for bounded prototype data | **Production enhancement:** confirmed workload limits, rate limits, asynchronous jobs, and streaming artifacts. |
| Regulator identity and reporting scope are not confirmed | Unresolved requirement | Do not describe regulator-ready access as implemented. |

## Repository-sharing checklist

- No bearer token, password, private key, database dump, populated `.env`,
  keystore, cloud credential, or signed download URL is tracked.
- Database credentials, redaction commitment keys, archive credentials, and
  export private keys are injected as secrets; none appears in a database URL.
- Test identities and payloads are synthetic and cannot be mistaken for client
  or employee data.
- Generated archives, exports, reports, and performance data contain no real
  account, tenant, actor, or resource identifiers.
- Public errors contain stable codes, generic messages, and correlation IDs;
  they contain no SQL, parameters, stack traces, tokens, or payload values.
- Operational logs exclude payloads, claims, request fingerprints, idempotency
  keys, download tokens, original redacted values, and export contents.
- Secret scanning covers the complete Git history. Any previously committed
  credential is revoked and rotated even if it was later removed.
- Production deployment cannot activate `application-local.yml`; actuator
  endpoints and internal routes are network restricted.

## Controls intentionally left as documentation

External anchors, immutable cloud archive adapters, managed key custody,
production rate limiting, durable job workers, step-up approval, multi-region
deployment, and a regulator portal are future controls. Implementing them in
this prototype would not strengthen its required integrity demonstration enough
to justify their operational complexity.
