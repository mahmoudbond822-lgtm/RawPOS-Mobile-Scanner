# Phase 4 — Build Evidence

**Phase:** 4 — communication and acknowledgment protocol (authorized 2026-11-21, A24).
**Scope:** `approval-packet-phase-4.md`. No checkout, no admission rules, no dispatch into the cart.

---

## Increment 1 — the shared contract

### What was added

| Path | Purpose |
|---|---|
| `protocol/fixtures/*.json` | 18 fixtures: health, pairing, scan request and every documented status, context, and three error shapes |
| `protocol/fixtures/manifest.json` | SHA-256 of each fixture, so a one-sided edit fails a build |
| `tools/check_fixtures.py` | Verifies the manifest and JSON validity; `--write` regenerates |
| `android/.../core/protocol/ContractFixturesTest.kt` | 8 tests: manifest hashes, protocol version, request fields, status set, duplicate, domain rejection, context, errors |
| `android/app/build.gradle.kts` | Fixtures wired as test resources from `../../protocol/fixtures` — one copy of the contract |
| `android/gradle/libs.versions.toml` | `org.json` as a **test-only** dependency (Android's stub `org.json` throws in JVM tests) |

### Drift guard, proven rather than assumed

Appending a single space to `scan.request.json` without regenerating the manifest:

```
ContractFixturesTest > everyFixtureMatchesTheManifest FAILED
8 tests completed, 1 failed
```

File restored; the suite returns green and `tools/check_fixtures.py` reports `fixtures OK: 18 files`.
A contract change that is not applied to both sides is now a failing test, not a shop discovery.

### Gates

| Gate | Command | Result |
|---|---|---|
| Fixtures | `py -3 tools/check_fixtures.py` | `fixtures OK: 18 files, manifest matches` |
| Unit tests | `.\gradlew.bat --no-daemon testDebugUnitTest` | **53 tests**, 0 failed (8 new) |
| Lint | not re-run this increment; no production code changed | — |

No production code was touched: the app still ships with no network client, and the manifest still has
no `INTERNET` permission. This increment defines the contract before either side consumes it.

### Honest limitation

The desktop side does **not** read these fixtures yet. Until increment 2 lands its route tests, the
contract is enforced on one side only. That is expected at this point, and it is the reason increment 2
is the desktop routes rather than the Android client.

---

## Increment 2 — scan and context routes (desktop)

Implemented against the approved design in `../../docs/architecture/desktop-scan-routes.md`.

### What was added

| Module | Content |
|---|---|
| `mobile/scans.py` | `ScanTable` (reserve-before-dispatch, TTL, capacity, digest), `ScanService` (the status ladder) |
| `mobile/context.py` | `derive_context`, `ContextGate` implementing decision Q2 |
| `mobile/dispatch.py` | `Dispatcher` protocol, `Outcome`, `FakeDispatcher` |
| `mobile/protocol.py` | `ScanRequest` validation, `ScanStatus`, `ScanFormat`, response builders, `format_utc_millis` |
| `mobile/server.py` | `POST /v1/scans`, `GET /v1/context`, bearer auth, per-token and per-IP token buckets |

### Approved decisions, and where each is proven

| Decision | Proof |
|---|---|
| Q1 — revoked and unknown tokens both answer 401 | `test_scan_requires_a_token`, `test_scan_rejects_an_unknown_token` |
| Q2 — `null` context refused while an invoice is active | `test_missing_context_with_an_active_invoice_is_refused`, `test_stale_context_is_refused_without_dispatch` |
| Q3 — digest ignores `sequence`/`scanned_at` | `test_digest_ignores_diagnostics` |
| Q4 — `completed(unknown)` never expires and never re-dispatches | `test_dispatch_failure_returns_202_and_never_redispatches`, `test_expired_completed_entry_is_swept_but_unknown_never_is` |
| Q5 — context comes from the dispatcher | `test_context_route_reports_no_invoice` |
| Q6 — retry while reserved waits, then answers | `test_in_flight_retry_returns_unknown_after_waiting` |
| Q7 — invalid tokens are IP-limited only | `test_scan_rate_limit_is_enforced` |
| Q8 — no `sequence` monotonicity check | `ScanRequest` accepts any `uint64` |
| Q9 — missing fixtures fail when configured | `test_contract_fixtures.py` resolution rules |
| Q10 — `202` echoes the context | `build_scan_unknown` |

### Boundaries — proven, not asserted

| Guard | Test |
|---|---|
| No checkout/UI/repository import in `mobile/` | `test_mobile_package_does_not_import_checkout_or_qt` |
| No `resolve_scan` / `resolve_barcode` / `CheckoutView` reference | `test_mobile_package_does_not_reference_checkout_entry_points` |
| Exactly four routes | `test_route_table_is_exactly_the_approved_paths`, `test_route_inventory_is_exactly_four_paths` |
| Status ladder matches the approved set | `test_status_ladder_matches_the_approved_set` |

The dispatcher in every test is `FakeDispatcher`. Nothing in this increment can create a cart line.

### Two small contract corrections

- `scan.duplicate.response.json` gained `message_code`, because every 200-class response carries one;
  the fixture and the manifest were regenerated together.
- Scan responses now render timestamps with milliseconds (`format_utc_millis`), matching §5.2. Health
  keeps second precision.

### Gates

| Gate | Command | Result |
|---|---|---|
| Lint | `py -3 -m ruff check .` | `All checks passed!` |
| Types | `py -3 -m mypy src` | `Success: no issues found in 160 source files` |
| Money-path guard | `py -3 scripts/check_no_float.py` | `no-float gate: PASS` |
| Full suite | `py -3 -m pytest -q` | **1658 passed in 171.37s** |

1658 = 1604 after Phase 2/3 + 54 new (25 unit, 18 route/boundary, 11 contract).

---

## Increment 3 — Android client logic, pin, and protocol

### What was added

| File | Purpose |
|---|---|
| `core/net/SpkiPin.kt` | Pin computation, pin-only `X509TrustManager`, TLS 1.2+ socket factory |
| `core/net/HttpTransport.kt` | `HttpRequest`/`HttpResponse`, `HttpTransport`, `Endpoint` |
| `core/net/ScanModels.kt` | `ScanEvent` (identity that survives retries), `Delivery`, `DeliveryError` |
| `core/net/ScanProtocol.kt` | Request body builder and response/context parsing against the fixtures |
| `core/net/ScanClient.kt` | One in flight, backoff, 15 s budget, instance-change detection |

28 new tests: 6 pin, 13 protocol, 9 client.

### The pin is cross-checked, not self-referential

The test certificate was generated by desktop-side code, and its pin was computed with the desktop's
own algorithm in Python:

```
expected pin: zYzwWOVoxUrCExm5ZEsUWA6ymg0nxppAXjCKr5hUhpU
```

Both implementations produce it, so a phone paired with a desktop will compute the same value. The
trust manager accepts that certificate, refuses a different pin, and refuses an empty chain.

### The client parses the same fixtures the desktop builds

`ScanProtocolTest` reads `scan.added.response.json`, `scan.duplicate.response.json`,
`scan.rejected_domain.response.json`, `scan.rejected_stale_context.response.json`,
`scan.unknown.response.json`, the error fixtures, and both context fixtures from the shared directory.
A status the desktop can emit but the phone cannot parse now fails a test on this side.

### Retry and uncertainty

| Behaviour | Test |
|---|---|
| Transport error then success reuses the same event id | `retryAfterATransportErrorReusesTheSameEventId` |
| `202 unknown` then success is decided | `unknownThenSuccessIsDecided` |
| Persistent `202` becomes uncertain | `persistentUnknownBecomesUncertain` |
| Offline from the start becomes expired | `offlineFromTheStartBecomesExpired` |
| 401 stops immediately | `unauthorizedStopsImmediately` |
| 409 is a refusal, not a retry | `conflictIsARefusalNotARetry` |
| 429 is retried | `rateLimitIsRetried` |
| **Instance change becomes uncertain and stops** | `instanceChangeMidRetryBecomesUncertainAndStops` |

The last one is D20 in code: the client detects the restart and refuses to keep retrying, because the
event may have landed.

### Lint finding, handled rather than muted

Lint raised `CustomX509TrustManager` — a check that exists because most custom managers disable
verification. This one verifies exactly one public key and throws otherwise, so the suppression carries
a comment saying so. Lint is back to 19 warnings, all version notices.

### Gates

| Gate | Result |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | **82 tests**, 0 failed (28 new) |
| `lintDebug` | 0 errors, 19 warnings (version notices only) |

### What this increment deliberately does not do

- **No `INTERNET` permission and no OkHttp yet.** A23 approved both, but nothing would use them in
  this slice: adding a network capability before the transport exists increases surface for nothing.
  They land with the transport wiring.
- **No Keystore credential store.** The client takes the token as a constructor argument; persistence
  arrives with the wiring.
- **The client is not reachable from the app.** No scanner integration yet, so behaviour is unchanged
  on the device.

---

## Increment 3b — transport, credentials, and submission

### What was added

| File | Purpose |
|---|---|
| `core/net/OkHttpTransport.kt` | OkHttp with the pin as the only trust anchor |
| `core/net/Credentials.kt` | Credentials, `CredentialCodec`, Keystore-backed `CredentialStore` |
| `core/net/ClientScanTransportHolder.kt` | The single place that can build a desktop transport |
| `core/transport/ScanTransport.kt` | Now returns `Delivery`; `ClientScanTransport` owns sequence and context |
| `ScannerViewModel` | Submission states, one in flight, no optimistic success |
| `ScannerScreen` | Renders the desktop's decision, or why there was none |
| Manifest | `INTERNET` permission (A23) |

### Choices worth recording

- **OkHttp 4.12.0, not 5.5.1.** OkHttp 5 requires `compileSdk 37`; A13 fixed compile/target at 36, and
  D21 says target 37 adds `ACCESS_LOCAL_NETWORK`. The SDK pin won. 4.12.0 is stable and sufficient.
- **`retryOnConnectionFailure(false)`.** OkHttp's silent retry would break the event-identity guarantee
  the client exists to provide.
- **No redirects, no cookies, no proxy auth.** One host, one pin, nothing else.
- **Keystore AES-GCM, backup disabled.** A lost key means "not paired", never a silent fallback.

### Behaviour

`SubmissionStatus` is `LOCAL_ONLY` without credentials — identical to Phase 3 — and
`IDLE → WAITING → DECIDED | FAILED` with them. While `WAITING`, a new emit is dropped rather than
queued (D15), and the screen shows `added`/`merged`/`not_found` only when the desktop said so (D13).

### Gates

| Gate | Result |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | **92 tests**, 0 failed (10 new) |
| `lintDebug` | 0 errors, 20 warnings (version notices only) |

**Bug found and fixed by the tests:** the first version initialised the submission state to `WAITING`,
so every scan was dropped as "one already in flight". Four view-model tests failed; the state now starts
`IDLE`.

### The gap this exposed

**There is no Android pairing client.** Phase 4's scope never included one, and the desktop's `/v1/pair`
has no caller on the phone. Consequences:

- no credentials can exist on the device, so `ClientScanTransportHolder` always returns `null`;
- the scanner therefore still behaves exactly as Phase 3;
- `INTERNET` is now declared but unreachable — a capability with no code path to use it;
- the phase's end-to-end acceptance cannot be demonstrated on hardware without it.

Pairing also needs a QR scan mode, because the analyzer deliberately restricts ML Kit to product
formats (QR is pairing-only per D14). This is a scope decision, not a defect: it must be approved, not
assumed.

---

## Increment 3c — the Android pairing client

### What was added

| File | Purpose |
|---|---|
| `core/net/PairingUri.kt` | Parses `rawpos://pair?...`, rejects malformed and expired payloads |
| `core/net/PairingClient.kt` | POST `/v1/pair`, retries while the cashier decides, maps refusals |
| `core/DeviceIdentity.kt` | Stable per-installation device id (not a secret) |
| `ScannerViewModel` | Pairing mode beside product scanning, since both need the camera |
| `ScannerScreen` | Pair action, pairing states, QR options when pairing |
| `AppContainer` | Pairing client, transport rebuilt when the token changes |

16 new tests: 6 URI, 7 client, 3 view model.

### Behaviour

- **Pairing mode restricts ML Kit to QR**, because product formats exclude QR by design (D14). The
  analyzer is rebuilt when the mode changes.
- **The pin comes from the QR**, so the TLS client is constructed *after* the scan — that is what makes
  the first connection trustworthy.
- **The read timeout for pairing is 65 s**, because the desktop deliberately holds the request while a
  human reads the code and taps Approve.
- **`202 waiting` retries the identical body**, as the protocol requires; `403/409/410` stop with a typed
  error; an expired QR is refused before any request is sent.
- **Credentials land in the Keystore store**, and the transport is rebuilt on the next scan without an
  app restart.

### Gates

| Gate | Result |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | **108 tests**, 0 failed (16 new) |
| `lintDebug` | 0 errors, 20 warnings (version notices only) |

### What is still unproven

The pairing path has **not run against a real desktop**. It is covered by unit tests with a scripted
transport, but two things only hardware can show:

1. that the phone can decode the desktop's QR through its camera, and
2. that the pinned TLS handshake succeeds against the desktop's self-signed certificate end to end.

That is the next check, and it needs the RawPOS app running with a pairing window open.

---

## Open items

1. **Desktop route tests** — consume the same fixtures, including `POST /v1/scans` and `GET /v1/context`
   (increment 2).
2. **Windows Firewall / shop LAN** — A7 approved the port and rule; still untested on a shop network.
3. **`device_already_paired`** — the imprecise `409 pairing_used` mapping from Phase 2 gets its own typed
   code in the Phase 4 fixtures if the contract needs it.
4. **Pre-pilot label capture** — still a blocking gate for any deployment (R13).
5. **Arabic review** — the mobile strings, including the new scan statuses (Q22).
