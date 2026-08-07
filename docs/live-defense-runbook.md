# Live defense runbook

Use this sequence for a short, repeatable walkthrough.

1. Explain the evidence objective and implemented boundary from the README.
2. Start the clean Compose environment and confirm `/actuator/health` is `UP`.
3. Append two synthetic events and show their contiguous sequence numbers and
   different content hashes.
4. Repeat the first request with the same `Idempotency-Key` and payload; show
   HTTP 200, the original receipt, and `Idempotency-Replayed: true`.
5. Query the tenant chain and run chain verification; show `valid=true`.
6. Run `sh scripts/demo-tampering.sh`; explain the first-invalid-sequence result
   for modification and deletion.
7. Point to the test traceability, security review, and known limitations. Do not
   describe documented production controls as implemented.

If startup fails, use `docker compose ps`,
`docker compose logs postgres`, and `docker compose logs audit-service` before
changing configuration. Finish with `docker compose down`; add `--volumes` only
when intentionally resetting all local demonstration data.
