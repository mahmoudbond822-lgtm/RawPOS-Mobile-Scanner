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

## Open items

1. **Desktop route tests** — consume the same fixtures, including `POST /v1/scans` and `GET /v1/context`
   (increment 2).
2. **Windows Firewall / shop LAN** — A7 approved the port and rule; still untested on a shop network.
3. **`device_already_paired`** — the imprecise `409 pairing_used` mapping from Phase 2 gets its own typed
   code in the Phase 4 fixtures if the contract needs it.
4. **Pre-pilot label capture** — still a blocking gate for any deployment (R13).
5. **Arabic review** — the mobile strings, including the new scan statuses (Q22).
