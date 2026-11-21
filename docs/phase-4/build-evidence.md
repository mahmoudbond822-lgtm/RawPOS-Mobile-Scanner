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

## Open items

1. **Desktop route tests** — consume the same fixtures, including `POST /v1/scans` and `GET /v1/context`
   (increment 2).
2. **Windows Firewall / shop LAN** — A7 approved the port and rule; still untested on a shop network.
3. **`device_already_paired`** — the imprecise `409 pairing_used` mapping from Phase 2 gets its own typed
   code in the Phase 4 fixtures if the contract needs it.
4. **Pre-pilot label capture** — still a blocking gate for any deployment (R13).
5. **Arabic review** — the mobile strings, including the new scan statuses (Q22).
