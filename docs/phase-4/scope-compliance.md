# Phase 4 — Scope Compliance Report

**Authorization:** A24 (Phase 4) with A12 (D10/D15/D20), A19 (D2), A20 (D5), A21 (D11), A22 (D13), A23
(`INTERNET` + OkHttp) — all owner-approved 2026-11-21.
**Design:** `../architecture/desktop-scan-routes.md` (ten decisions, Q1–Q10).
**Result:** compliant on scope. No checkout invocation, no basket mutation, no schema change, no barcode
normalization.

---

## 1. Approved scope vs delivered

| Approved deliverable | Delivered | Evidence |
|---|---|---|
| Desktop `POST /v1/scans` | Route with auth, rate limits, idempotency | `mobile/scans.py`, `mobile/server.py` |
| Desktop `GET /v1/context` | Route with auth and rate limit | `mobile/server.py` |
| Dedupe table (A12) | Reserve-before-dispatch, 24 h TTL, 50 000 cap, fail closed | `ScanTable`, 12 unit tests |
| Context token (A21) | Derivation + acceptance rule Q2 | `context.py`, 9 tests |
| Shared fixtures + manifest (A19) | 18 fixtures, SHA-256 manifest, both sides verify | `protocol/fixtures`, `ContractFixturesTest`, `test_contract_fixtures.py` |
| Android `ScanClient` | Pin-only TLS, one in flight, retry, uncertain path | `core/net/*`, 28 tests |
| Android `ContextClient` | `fetchContext()` on the same pinned client | `ScanClient.fetchContext` |
| Event identity (A12) | One id for the life of a retry; never regenerated | `ScanEvent`, client tests |
| Status rendering (A22) | `LOCAL_ONLY/IDLE/WAITING/DECIDED/FAILED`, no optimistic success | `ScannerViewModel`, screen strings |
| **Android pairing client** | Added by owner decision after the gap was found | `PairingUri`, `PairingClient`, 16 tests |

The pairing client was **not** in the original packet; it was added after increment 3b showed the
transport was unreachable without it. That is a recorded scope addition, approved in session.

---

## 2. Exclusions — verified

| Exclusion | Verification | Result |
|---|---|---|
| No checkout import | `test_mobile_package_does_not_import_checkout_or_qt` | Pass |
| No `resolve_scan`/`resolve_barcode`/`CheckoutView` reference | `test_mobile_package_does_not_reference_checkout_entry_points` | Pass |
| No database access | The mobile package imports no repository or protection module | Pass |
| No schema change | `git diff` touches no migration | Pass |
| No barcode normalization | `barcode` validated as `^[0-9]{1,64}$` and passed through unchanged | Pass |
| No A10/A11 assumption | `format` is diagnostic; never used for lookup | Pass |
| Exactly four routes | `test_route_table_is_exactly_the_approved_paths` | Pass |
| No business data in responses | Fixtures carry status/context only; no product, price, or stock | Pass |

---

## 3. Gates — measured

| Repository | Gate | Result |
|---|---|---|
| RawPOS | `ruff check .` | All checks passed |
| RawPOS | `mypy src` | no issues in **160** files |
| RawPOS | `scripts/check_no_float.py` | PASS |
| RawPOS | `pytest -q` | **1661 passed in 114.76s** |
| Android | `assembleDebug` | BUILD SUCCESSFUL |
| Android | `testDebugUnitTest` | **110 tests**, 0 failed |
| Android | `lintDebug` | 0 errors, 20 warnings (version notices only) |
| Android | `connectedDebugAndroidTest` | 3 tests passed earlier this phase; not re-run after 3c |

---

## 4. Hardware verification

| Item | Result |
|---|---|
| Pairing over the real QR | **owner-observed: successful** |
| Credentials stored and reused | implied by the paired state persisting |
| Scan reaches the desktop | answered `503 not_ready` — the Phase 4 boundary, not a transport failure |
| Cart mutation | **none, by design** |

The pairing success is the strongest evidence in this phase: it exercised the QR camera path, the
pinned TLS handshake against the desktop's self-signed certificate, the pairing state machine, and the
Keystore store — on hardware.

**A defect found only in use:** switching to pairing mode handed a shut-down executor to the new
analyzer, so no frame was ever decoded. Fixed by binding the executor and ML Kit client to the camera
binding. No unit test could have caught it; it is recorded in the evidence.

---

## 5. Deviations and judgment calls

1. **Revoked tokens answer 401, not 403** (decision Q1). The registry cannot distinguish a revoked token
   without storing revoked hashes; a schema change was not worth a status code.
2. **OkHttp 4.12.0, not 5.x.** OkHttp 5 requires `compileSdk 37`; A13 fixed 36 and D21 keeps target 36 to
   avoid `ACCESS_LOCAL_NETWORK`.
3. **`mobile.scan.not_ready`** was added so a desktop with no till says so, instead of the phone
   implying a scan might have landed.
4. **Two contract corrections**: the duplicate fixture gained `message_code`; scan timestamps now render
   milliseconds (§5.2).
5. **Manual APK install** remains necessary on this MIUI build; `pm grant` and `settings put system` are
   blocked.
6. **Pairing was added to the phase** after the gap was found, by explicit owner decision.

---

## 6. Acceptance criteria (roadmap Phase 4)

| Criterion | Status |
|---|---|
| Lost response + retry → one dispatch and a cached outcome | **met** — `test_full_scan_lifecycle_dispatches_exactly_once_per_event` |
| Finalize between capture and send → `rejected_stale_context`, no line | **met** — context mismatch refused without dispatch |
| Restart changes `server_instance_id`; no auto-replay | **met** — `instanceChangeMidRetryBecomesUncertainAndStops` |
| Phone never shows "added" for `processing`/`unknown` | **met** — `SubmissionStatus` separates decided from failed |
| No business data in any response | **met** — fixtures and route inventory |

---

## 7. What is not proven

- **The cart.** Phase 5 owns it; a scan currently stops at `FakeDispatcher` and is answered
  `not_ready`.
- **Windows Firewall and a real shop LAN** (A7 approved, untested on a shop network).
- **Performance**: latency and rate-limit behaviour under load (Phase 6).
- **Arabic review** of the scanner and pairing strings (Q22).
- **A10**: exact-only UPC/EAN remains unsigned; it is a Phase 5 gate.

---

## 8. Open items

1. **Phase 5 authorization** and **A10** — the two things between here and a scan reaching the cart.
2. **`connectedDebugAndroidTest`** re-run after 3c changes, on a kept-awake phone.
3. **Pre-pilot label capture** — still the blocking gate for any deployment (R13).
4. **Arabic review** (Q22).
5. **`device_already_paired`** still maps to `409 pairing_used`; a Phase 5 contract refinement.

---

## 9. Inventory

- **RawPOS:** `mobile/scans.py`, `mobile/context.py`, `mobile/dispatch.py` (new); `mobile/protocol.py`,
  `mobile/server.py` (extended); 5 new test files (scans, context, boundaries, routes, end-to-end) plus
  `tests/protocol/test_contract_fixtures.py`.
- **Android:** `core/net/*` (8 files), `core/transport/ScanTransport.kt`, `core/DeviceIdentity.kt`,
  scanner view model and screen updates, 6 new test classes.
- **Companion:** 18 fixtures + manifest, `tools/check_fixtures.py`, the design and evidence documents.

---

## 10. Closure statement

Phase 4 delivered the wire contract, the desktop routes with idempotency and context, the Android client
with pinning and retries, and — after the gap was found — the pairing client that makes the transport
reachable at all. A phone can now pair with a desktop, scan a label, and be told the truth about what
happened.

It cannot change a cart, and it does not pretend to. That is the next phase, and it needs A10 and its own
authorization.

**Phase 4 is complete. Phase 5 has not started.**
