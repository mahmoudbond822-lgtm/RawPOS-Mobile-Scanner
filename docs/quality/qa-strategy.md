# QA Strategy

**Status:** proposed
**Baseline:** RawPOS `797167a`, measured 1,450 passed / 134.97 s (Python 3.14.3, global interpreter)
**Related:** `test-matrix.md`, `performance-requirements.md`, `../architecture/desktop-integration.md`

---

## 1. Principles

1. **Two independent suites meet at one contract.** Desktop tests own business outcomes; Android tests
   own capture, validation, dedupe, and transport; shared JSON fixtures detect drift.
2. **Parity is a test, not an assumption.** Every business scenario runs through the existing USB path
   and the mobile path; lines, quantities, totals, messages, and audit rows must match.
3. **No claim without an artifact.** A pass is a command, a commit SHA, and an output file. Manual QA is
   a dated checklist with device serials and photos.
4. **Mocks only at the edges.** Fake the camera, recognizer, transport, clock, and Keystore. Never fake
   `CheckoutService`, pricing, or the database in an integration test.
5. **The dangerous paths get negative tests.** Thread affinity, name-search leakage, duplicate
   delivery, replay, stale context, and non-digit payloads are tested by trying to break them.
6. **Existing tests are a contract.** The wedge/focus tests, checkout tests, and no-network tests are
   not "adjusted to pass". They are either unchanged or deliberately amended with a recorded reason.

---

## 2. Test levels

| Level | Scope | Tooling / location | Mocked | Budget | Gate |
|---|---|---|---|---|---|
| Desktop unit | Protocol parsing, validation, token hashing, pairing state, rate limiter, dedupe, context derivation | pytest; `tests/unit/test_mobile_*.py` | Clock, RNG, dispatcher | < 30 s | Blocking |
| Desktop integration | Receiver → controller → real `CheckoutService` on a migrated SQLCipher DB; loopback TLS | pytest; `tests/integration/test_mobile_*.py`, `app_db` | Nothing in the DB; real loopback HTTP | < 90 s | Blocking |
| Desktop GUI | Bridge → `CheckoutView`, focus, wedge regression, remount, pairing dialog | pytest-qt; `tests/gui/test_mobile_*.py`; existing wedge tests unchanged | Fake receiver emits the bridge signal; one loopback test | < 3 min | Blocking |
| Desktop failure | Kill receiver, close DB, restore, corrupt registry, disk full | pytest; `tests/failure/test_mobile_*.py` | DB/clock hooks; real process kill for one test | < 2 min | Blocking |
| Protocol contract | Same fixtures parsed/validated/serialized on both sides | `protocol/fixtures/` + SHA-256 manifest | None | < 10 s | Blocking on both sides |
| Android JVM | ViewModel states, validator, dedupe state machine, retry policy, token-store interface, context handling | JUnit + coroutines-test with virtual time | Camera, recognizer, transport, clock, Keystore | < 60 s | Blocking |
| Android instrumented | CameraX on fixture bitmaps, Keystore AES-GCM, TLS pin, Compose UI, rotation, process death | `androidTest`; API 34 emulator + one real phone; host at `10.0.2.2` | No sensor; desktop is a host test double | ~15 min | Advisory until flake rate < 2% over 20 runs, then blocking |
| Security | Malformed/oversized/unauthenticated/replay/forged/log-injection; route inventory | pytest; `tests/security/` | Loopback only | < 5 min | Blocking automated; manual red-team before release |
| Performance | Latency, CPU, memory, battery, reconnect | Scripts + Android profiler + `dumpsys` + 240 fps video | None | Hours, nightly | Advisory until baseline, then release-blocking |
| End-to-end hardware | Phone + Windows POS + router + USB scanner | Manual scripts M-01…M-11 | None | ~1 day per pair | Release-blocking |

New pytest markers: `mobile`, `contract`, `security`, `lan`. Register them in `pyproject.toml`.

---

## 3. Environments

### 3.1 Desktop

- Windows 11 (mandatory), 100% and 150% DPI.
- One touchscreen POS panel at the shop's actual resolution (confirm; assume 1366×768 until then).
- Windows 10 22H2 only if a shop still runs it (best effort).
- Firewall on, Private profile allowed, Public profile refused.
- A consumer router with AP/client isolation to prove the failure is explained, not silent.
- Multiple NICs: the QR must advertise the LAN address, never Hyper-V/vEthernet/VPN/Bluetooth.

### 3.2 Android

- Mandatory: one budget phone (≤ 3 GB RAM, weak macro focus), one mid-range, one current flagship.
- API levels: minSdk (26) and the latest stable; one mid API.
- One phone in Arabic locale; one tall 20:9 or foldable for layout.
- Best effort: no Play Services, small 5-inch phone, tablet.
- Emulator API 34 x86_64 for pipeline/UI only; never evidence for camera, thermal, battery, or radio.

### 3.3 Network

- Same subnet, Wi-Fi 5 or 6, no client isolation.
- One run through a second AP/roaming.
- One run with the phone on a guest network (must show a clear failure).
- One run with the desktop on Ethernet and the phone on Wi-Fi.

---

## 4. Test data

- **Barcodes** (GS1 in-store prefix 2 avoids collisions): EAN-13 `2123456789010` (valid),
  `2123456789011` (bad check), `2000000000008` (unknown); UPC-A `212345678909` and EAN-13
  `0212345678909`; EAN-8 `21234569`; ITF-14 `10012345678902`; short codes `2002`, `1001`, `1002`.
- **Products:** synthetic English/Arabic names, integer minor-unit prices, built with
  `tests/fakes/checkout.py` (`build_checkout`, `stock_product`). Variants: active, inactive, low stock,
  numeric name (`"Carton 1000"`) for the name-collision test.
- **Devices:** active, revoked, expired; tokens generated per test; fixtures store hashes only.
- **Certificates:** generated at test time with `cryptography`: valid, expired, not-yet-valid, wrong
  SAN, rotated, CA-signed. No private keys committed.
- **Clock:** fixed `2026-11-20T09:00:00Z` through an injected clock. Do not patch `datetime` globally.
  Wedge timing uses `time.monotonic_ns` and keeps real time.
- **Protocol fixtures:** versioned request/response JSON with a SHA-256 manifest, read by both sides.

---

## 5. Non-regression and the no-network amendment

### 5.1 Must stay green

- Wedge/focus: `tests/gui/test_checkout_ui.py` —
  `test_wedge_scan_in_the_quantity_editor_lands_in_the_scan_field`,
  `test_wedge_scan_from_the_grid_opens_no_quantity`,
  `test_wedge_scan_in_the_tender_field_does_not_confirm_the_sale`,
  `test_slow_hand_typed_quantity_is_never_a_scan`,
  `test_scan_adds_a_line_and_finalize_posts_a_sale`,
  `test_unknown_scan_shows_a_message_and_alerts`.
- Checkout/shell: `tests/gui/test_checkout_inactive_product.py`,
  `tests/gui/test_checkout_approval_flow.py`,
  `tests/gui/test_returns_ui.py::test_switching_from_checkout_to_returns_and_back_keeps_the_draft`,
  `tests/gui/test_language_switch_shell.py::test_language_switch_reinstantiates_and_persists`.
- Services: `tests/unit/test_checkout_service.py` (including
  `test_resolving_by_name_adds_a_line`, which proves why mobile must not reuse it),
  `test_checkout_discounts.py`, `tests/integration/test_checkout.py`,
  `test_product_flags.py`, `test_discount_approval.py`, `test_activation_flow.py`.
- Barcode rules: `tests/unit/test_product_service.py::test_barcodes_must_be_ascii_digits`,
  `tests/migrations/test_products_inventory_schema.py::test_barcode_is_ascii_digits_and_unique`.
- Crash safety: `tests/failure/test_checkout_rollback.py`, `test_checkout_idempotency.py`,
  `test_checkout_readonly.py`, `tests/integration/test_recovery_quiescence_integration.py`.
- Gates: `ruff check .`, `mypy src`, `py scripts/check_no_float.py`, `py -3 -m pytest -q`.

### 5.2 Amending the no-network guarantee

The current tests patch `socket.socket`, `create_connection`, `getaddrinfo`, and `gethostbyname` and
prove core flows open no sockets. They must **not** be deleted or weakened. Add a precise amendment:

1. **Keep both files unchanged for default-off.** If the receiver starts automatically, they fail.
2. **`NET-STATIC`** (unit): outside `rawpos/mobile/`, no module imports client networking
   (`urllib.request`, `http.client`, `ftplib`, `smtplib`, `requests`, `httpx`). Inside the mobile package,
   only server primitives are allowed.
3. **`NET-RUNTIME`** (integration, receiver on): record socket calls; exactly one listener on the
   configured port/interface; no `connect()` or `getaddrinfo()` to a non-loopback address.
4. **`NET-LOOPBACK`**: receiver tests bind `127.0.0.1:0`; a conftest guard fails any test binding
   `0.0.0.0` or a LAN address unless marked `lan`; `lan` tests run only in the lab.
5. **`NET-SELFTEST`**: the old guard still raises on `socket.socket()` inside a core flow, and its patch
   list still contains all four names. This catches silent neutering.
6. **Governance:** record the amendment in the RawPOS constitution/ADR in the same implementation
   change (not in this architecture phase). Proposed wording: core functions never need a network; the
   receiver is optional, off by default, LAN-only, owner-enabled.

The new guarantee is: **no outbound internet; no inbound listener unless explicitly enabled and bound to
a private interface.**

---

## 6. Failure injection

| # | Fault | Injection | Required behavior |
|---|---|---|---|
| F1 | Kill receiver mid-request | Subprocess kill before/after commit | Before: retry adds one. After: process died, cart gone, retry refused as stale. Never a partial line |
| F2 | Drop the response after commit | Proxy closes socket | Phone shows "not confirmed"; retry returns cached outcome; one line |
| F3 | Close the DB mid-request | Critical-op/close hook | 503; no partial write; server thread survives |
| F4 | Start a restore while scanning | Trigger restore | Scans rejected during quiescence; resume after; nothing written to the replaced DB |
| F5 | Language remount mid-scan | `_switch_language` during in-flight event | One commit; bridge rebinds; correct phone message |
| F6 | Pull the network | NIC/Wi-Fi off | "Not sent" ≤ 3 s; no queue; no burst on return |
| F7 | Fill the dedupe cache | 50,001 event ids | `503 dedupe_capacity`; in-flight ids never evicted; bounded memory |
| F8 | Corrupt the registry | Truncate/garble/decrypt failure | Fail closed: receiver off, all tokens refused, "re-pair" |
| F9 | Revoke mid-session | Revoke during scan loop | Next request 403; no further adds |
| F10 | Clock skew | Desktop +10 min, phone −10 min | Scans still applied (timestamps diagnostic); TLS behaves per cert validity; warn > 5 min |
| F11 | Disk full | Patch write to raise OSError | Pairing fails with a message; previous registry intact (atomic write) |
| F12 | Stale context | Finalize between capture and send | `rejected_stale_context`; no mutation; phone refreshes context |
| F13 | Desktop restart | Restart between send and retry | `server_instance_id` changes; no auto-replay; "check desktop" |

---

## 7. Manual scripts

Each script records date, build SHA, device serials, photos, and tester initials.

- **M-01 First pairing (5 min).** Open Mobile Scanner → Pair; phone scans QR; compare the 6-digit code;
  approve on desktop. Pass: connected ≤ 30 s; QR cannot be reused.
- **M-02 Daily scanning (5 min).** Scan A–E once, A–B again, one unknown label. Pass: 5 lines, A/B qty 2,
  unknown adds nothing and shows Not found.
- **M-03 Wi-Fi change (5 min).** Guest Wi-Fi scan (must show a clear failure), then staff Wi-Fi scan
  (must work ≤ 30 s).
- **M-04 Revoke and re-pair (5 min).** Revoke; scan (must be refused); re-pair; scan (must work).
- **M-05 USB regression (5 min).** Scan 10 labels with USB, phone connected and disconnected. Pass: one
  line each; focus returns; no mobile message.
- **M-06 Keyboard-only sale (5 min).** No mouse. Scan, edit quantity, tender, finalize. Pass: correct
  total/change.
- **M-07 Arabic (5 min).** Phone and POS in Arabic. Pass: mirrored layout; digits LTR; no clipping.
- **M-08 Light/dark (3 min).** Switch both themes. Pass: readable text/status.
- **M-09 Low battery (10 min).** 15%/battery saver; 10 scans. Pass: works or warns; no crash/lost scan.
- **M-10 Unknown item (2 min).** Pass: both screens Not found; no line; one beep.
- **M-11 Phone call (5 min).** Scan, take a call, hang up. Pass: no scans during the call; resume
  requires a tap.
- **M-12 Stale context (5 min).** Scan; finalize before the send completes (artificial delay); pass:
  no line in the new invoice; phone asks to rescan.
- **M-13 Restore during scan (10 min).** Start a restore while scanning. Pass: scans refused; no
  corruption; receiver works after.

---

## 8. CI and evidence

**Can run in CI:**

- Windows runner: desktop unit/integration/GUI/failure, ruff, mypy, no-float, contract, certificate
  matrix, security suite, perf smoke (no thresholds).
- Linux runner with virtualization: Android JVM tests; `connectedAndroidTest` on an API 34 emulator
  (advisory until stable).

**Cannot run in CI:** real camera optics/labels, real Wi-Fi/AP isolation/roaming, battery/thermal, USB
hardware, touch panels/DPI, sleep/wake, phone calls, firewall prompts.

**Evidence:**

- JUnit XML (`pytest --junitxml`, Gradle `build/test-results`), with commit SHA, command, counts.
- Receiver logs as JSON lines: event id, device id, status, latency. No tokens, secrets, or barcodes.
- Screenshots for GUI/manual tests; 240 fps videos; performance CSV.
- Signed manual checklists.

A claim without an artifact is reported as **not verified**.

---

## 9. Entry/exit criteria

**Entry to implementation (Phase 1):**

- this architecture approved (see `../planning/risks-and-decisions.md` §6);
- baseline JUnit artifact at HEAD;
- no-network amendment approved;
- protocol fixtures v1 frozen;
- Android toolchain and at least one device available.

**Exit per phase:**

- every matrix row for that phase passes (A or E) with artifacts;
- no open Sev1/Sev2;
- REG-01…REG-04 green;
- new tests fail without the change (red-first evidence).

**Release:**

- every mandatory matrix row passes;
- M-01…M-13 completed on mandatory devices;
- performance thresholds met or waived in writing;
- security suite green + red-team checklist signed;
- Arabic copy reviewed by a native speaker;
- no unapproved dependency;
- all 13 acceptance criteria mapped to PASS evidence.

---

## 10. Gaps and risks

| # | Gap | Mitigation |
|---|---|---|
| 1 | No Android SDK/JDK/emulator on this machine | Procure/install before Phase 1; Android results stay "not verified" until then |
| 2 | No CI configured | Start with local scripts that emit the same artifacts; add a host later |
| 3 | Camera not reproducible in emulator | Fixture-bitmap pipeline tests + real-device lab |
| 4 | ML Kit vendor behavior | Pin version/model; golden label set; rerun on upgrades |
| 5 | TLS pin matrix complexity | Generated cert fixtures; table tests; real-device expiry/clock checks |
| 6 | Flaky GUI timing | `qtbot.waitUntil`; never loosen wedge tests; flake quarantine with owner/expiry |
| 7 | Over-mocking hides name-search bug | One real-stack integration test per business rule; BUS-05 is mandatory |
| 8 | Localization drift | Key-parity tests both sides; native review gate |
| 9 | No-network guarantee weakened | §5.2; old tests unchanged; static gate; self-test |
| 10 | Stale retries after restart | F13/DUP-05; no auto-replay; "check desktop" |
| 11 | Fixture drift between repos | SHA-256 manifest checked in both builds |
| 12 | Shared Windows machine noise | Record Defender/Update state; repeat runs; report p50/p95 |
| 13 | The 1,450 baseline is Python 3.14, not 3.12 | Add a 3.12 CI run before release; do not claim 3.12 green until measured |
