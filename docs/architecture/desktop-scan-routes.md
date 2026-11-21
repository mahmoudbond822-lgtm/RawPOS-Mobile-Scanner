# Desktop Scan Routes — Approved Design (Phase 4, increment 2)

**Status:** **approved by the owner, 2026-11-21.** These decisions bind the implementation of
`POST /v1/scans` and `GET /v1/context`.
**Basis:** decisions A12 (D10/D15/D20), A19 (D2), A20 (D5), A21 (D11), A22 (D13), A23, A24 — all
approved 2026-11-21.
**Boundaries:** no checkout invocation, no basket mutation, no barcode normalization, no A10/A11
assumptions, no schema change.

---

## 1. Module map

| Module | Responsibility |
|---|---|
| `mobile/protocol.py` | scan request validation, response builders (extend) |
| `mobile/scans.py` | dedupe table and scan service (new) |
| `mobile/context.py` | context derivation and per-device memory (new) |
| `mobile/dispatch.py` | `Dispatcher` / `Outcome` protocol and `FakeDispatcher` (new) |
| `mobile/server.py` | the two routes, authentication, rate limiting (extend) |

`devices.py`, `pairing.py`, and `tls.py` are unchanged.

---

## 2. Approved decisions

### Q1 — Revoked tokens answer `401`, not `403`

The registry stores revocation as name and time only, so a revoked token is indistinguishable from an
unknown one. **Both return 401** with an identical body. This is a recorded deviation from the
protocol's `403 device_revoked` row; storing revoked hashes would require a registry schema version.
*Not worth it for a status code.*

### Q2 — Post-restart context acceptance

Per-device context memory is in-process and therefore empty after a restart, which is exactly when a
stale scan is most dangerous. **A `null` context is accepted only when the receiver has no active
invoice.** Any non-null mismatch is always `rejected_stale_context`, never "first scan, accept it".

### Q3 — What counts as "same payload" for the conflict check

Digest over `barcode + format + context`. **`sequence` and `scanned_at` are excluded** as diagnostics,
so a retry that re-stamps its clock cannot produce a false `409`.

### Q4 — `completed(unknown)` never expires

Within the process lifetime, an entry that entered dispatch and never returned an outcome is final.
Expiring it would allow a late retry to dispatch a second time, which is the one mutation this design
exists to prevent.

### Q5 — Context in Phase 4 comes from a provider

No checkout exists in Phase 4. Context is obtained through the `Dispatcher` interface; the fake returns
a fixed token. Real derivation from invoice identity is Phase 5.

### Q6 — A retry arriving while reserved waits, then answers

The request waits up to the remaining dispatch deadline and returns either the outcome or `202 unknown`.
It never dispatches a second time.

### Q7 — Invalid tokens do not consume per-token budget

Rate limiting for unauthenticated traffic is per source IP only, so a bad token cannot lock out a good
device.

### Q8 — `sequence` is not validated for monotonicity

Any `uint64` is accepted. Gaps are logged as diagnostics. It is explicitly not replay defence; the event
id is.

### Q9 — Missing fixtures are a failure when the path is configured

`RAWPOS_MOBILE_FIXTURES` set but unusable → the test fails. Neither the variable nor the sibling
directory present → skip with a visible reason.

### Q10 — `202` echoes the current context

So the phone keeps its token fresh while waiting.

---

## 3. Dedupe table

| Property | Value |
|---|---|
| Key | `(device_id, event_id)` |
| Scope | one receiver process; never shared, never durable |
| Lifetime | 24 h from reservation, or capacity |
| Capacity | 50 000; at capacity refuse, never evict a live entry |
| Concurrency | one lock around reserve / complete / lookup |
| States | `RESERVED` → `COMPLETED(outcome)`; `RESERVED` → `RELEASED` only before dispatch |

Not keyed by barcode. Two deliberate scans of one product are two units.

## 4. Reserve-before-dispatch

```
ABSENT ──reserve──► RESERVED ──outcome──► COMPLETED(outcome)
                       ├──pre-dispatch failure──► RELEASED (retry may dispatch)
                       └──entered dispatch, no answer──► COMPLETED(unknown)  [final]
```

**Once dispatch is entered, the reservation is never released.** A pre-dispatch failure — paused
receiver, no dispatcher, shutdown, validation — may release it, because nothing was dispatched.

## 5. Response semantics

| Case | HTTP | Body |
|---|---|---|
| Outcome in time | 200 | real status |
| Retry while reserved | 200 or 202 `unknown` | after waiting |
| Retry after completed | 200 `duplicate` + `original_status` | no second dispatch |
| Retry after completed(unknown) | 202 `unknown` | cashier decides |
| Same key, different digest | 409 `event_id_conflict` | never dispatched |
| Table full | 503 `dedupe_capacity` | fail closed |
| Paused / no dispatcher | 503 `service_unavailable` + `Retry-After: 5` | retryable |

## 6. Client retry contract (increment 3)

Same `event_id` and payload; backoff 250 ms → 4 s with ±20 % jitter; total budget 15 s. A **changed**
`server_instance_id` stops retries and shows "Delivery uncertain — check the desktop"; the phone never
replays across a restart. Budget exhausted → "Scan expired — scan again". No queue.

## 7. Authentication and context

Bearer token → `DeviceRegistry.authenticate`; `body.device_id` must equal the token's device, else 401.
Context: accept `null` only with no active invoice; require an exact match otherwise; mismatch →
`rejected_stale_context` with no dispatch, response carries the current token.
Rate limits: 5/s burst 10 per token, 25/s burst 50 per IP, context 30/min.

## 8. Fixtures in pytest

`conftest.py` resolves `RAWPOS_MOBILE_FIXTURES` → sibling path → skip. Tests verify every manifest hash,
parse each request fixture through the real parser, and build each response through the real builder
comparing structures. No byte-order coupling.

## 9. Non-invocation guarantees

The route stops at `Dispatcher`. Increment 2 does not import `CheckoutService`, `CheckoutView`, any
repository, or any Qt widget; does not open the database; does not call `resolve_scan()` or
`resolve_barcode()`; does not touch a cart, price, stock, shift, or invoice.

Guard tests: static import scan of `src/rawpos/mobile/*`; route inventory of exactly four paths; a
dispatch test asserting one call per event.

---

## 10. Approval record

| Item | State |
|---|---|
| Owner accepted the proposal and all ten recommendations | **2026-11-21** |
| Implementation | **not started** — awaits a separate instruction |
