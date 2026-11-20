I'll start with the shared facts file, then check the RawPOS test layout and the spec so the matrix matches what's actually in the repo.The fact pack is thorough. Next I'll find the project brief with the 13 acceptance criteria, and check the test config, CI, and the named test files.The briefs folder holds the other agents' briefs. The 13 acceptance criteria should be in one of them, so I'll search for them. I'll also confirm the no-network tests and wedge tests named in the fact pack.The no-network guard patches `socket` module attributes, which matters for the amendment plan. Next I'll look at how the wedge tests simulate input, and find the acceptance criteria with a corrected search.No `.github/` directory exists in RawPOS, so there is no CI config for the desktop either. The 13 acceptance criteria aren't in the agent-6 brief, so I'll search the other briefs and the new project folder for them.The 13 numbered acceptance criteria are not in any file I can read, so I'll say so in the deliverable instead of inventing them. Next I'll read the other agents' briefs to pin down their dependencies, check the repo docs for any mobile-scanner requirements, and pull the test names I need to cite.Agents 2–5 are scoped to Android, barcode policy, protocol, and UI, so my dependencies are clear. The repo specs also say "no mobile target", which the desktop's own governance will need an exception for. Next I'll get the exact test names to cite, plus git state and the handover gate.The handover sets the commit-evidence rules (named tests, before/after counts, red-first runs), which I'll adopt. Next: git state, a static test count to sanity-check the 1,450 figure, the Android toolchain on this machine, and the handover's 1,450 context.Verified so far: HEAD matches `797167a`, the working tree is clean, and 215 test files hold 1,190 `def test_` definitions. The 1,450 figure can't be confirmed statically, so I'll flag it as a measurement I didn't reproduce. Android tooling is not on PATH. Next I'll spot-check the wedge constants and barcode rule that the matrix will cite, and the local network setup.Environment facts so far: no Android SDK or JDK env vars, six IPv4-bearing interfaces including a Hyper-V `vEthernet` adapter, and all three Windows Firewall profiles enabled. Last, I'll check whether RawPOS has an injectable clock, since the deterministic-clock fixture depends on it.I now have the core facts. One more check matters for the test design: whether a numeric unknown barcode can fall through to `resolve_scan`'s name search. I'll read that code path and the search query before writing the matrix.The code confirms a real risk. For an unknown numeric token, `resolve_scan` runs a name `LIKE` search and adds the first match, so a short code could add the wrong product. That's a finding for the matrix, not a bug I'll fix. Last, I'll find where the project states its no-network principle, so the amendment cites the right governance document.The constitution is the governing document: offline-first is non-negotiable, and the test docstrings cite a different principle number. Before writing, I'll confirm three helper and source references I plan to cite.# QA Strategy and Test Matrix: RawPOS Mobile Scanner v1.0

*Agent 6, read-only review. Baseline: RawPOS `797167a`, clean tree. Nothing below is implemented. The only runs were read-only inspections (§13). Figures marked **proposed** are targets, not measurements.*

**Read first: four findings that change the plan**

- **A numeric unknown barcode can add the wrong product today.** `resolve_scan` falls back to a name `LIKE %token%` search and adds the first hit (`checkout_service.py:527–530`). The mobile path must use exact lookup (BUS-05, D2).
- **The no-network guarantee cannot be kept by editing the old guard.** It patches `socket.socket`, which would block the receiver. It must be amended deliberately (§4).
- **The 13 numbered acceptance criteria are not in any file I could read.** §10 traces the user's stated scope constraints instead. Please send the list.
- **No Android tooling is installed and no CI is configured.** `adb`, `java` and `gradle` are not on PATH, `ANDROID_HOME` and `JAVA_HOME` are unset, and there is no `.github/`.

## 1. Quality strategy

Two independent suites meet at one contract.

- **Desktop** (Python, pytest, pytest-qt offscreen) owns every business outcome. Assertions check invoice lines, GUI state and audit rows in the database, never the phone's own state.
- **Android** (Kotlin, JVM and instrumented) owns capture, validation, dedupe and transport. Its tests use a fake camera, recognizer, transport and clock.
- **Protocol contract:** one set of JSON fixtures, parsed by both sides. It is the only shared artifact and the drift detector.

The highest risks are business integrity (duplicate lines, wrong product, sales without a shift), protocol correctness, and regression of USB and keyboard flows. All are deterministic and need no hardware, so they carry most of the matrix. Hardware-only risks (camera latency on real labels, ML Kit on real print, Wi-Fi roaming, battery, thermal) get a small set of scripted, evidence-producing device tests.

Two techniques do most of the work:

- **Parity testing.** Each business scenario runs through the existing USB/keyboard path and the new mobile path. Lines, quantities, totals, messages and audit rows must match (PAR-01).
- **Negative tests for invariants.** Rules the design depends on (no business call from a non-GUI thread, no synthetic key events, no non-digit forwarding) are tested by trying to break them.

## 2. Test levels

New markers `receiver`, `security`, `contract` and `lan` are registered in `pyproject.toml`. Nothing else in tooling changes.

| Level | Scope | Tooling and location | Mocked | Budget (proposed) | Gate |
|---|---|---|---|---|---|
| Desktop unit | Receiver parsing, validation, token hashing, pairing state, rate limiter, dedupe window. No Qt, no DB | pytest, `tests/unit/test_receiver_*.py` | Clock, RNG, scan sink (Protocol fake) | <30 s | Blocking |
| Desktop integration | Receiver to real `CheckoutService` on a migrated SQLCipher DB | pytest, `tests/integration/test_receiver_*.py`, `app_db` fixture | Nothing on the DB; real loopback HTTP | <90 s | Blocking |
| Desktop GUI | Bridge to `CheckoutView`, focus, wedge, remount, pairing dialog | pytest-qt, `tests/gui/test_mobile_*.py`; existing wedge tests unchanged | Fake receiver emits the bridge signal; one loopback test | <3 min | Blocking; wedge tests never quarantined |
| Desktop failure | Kill receiver, close DB, restore, disk full, corrupt registry | pytest, `tests/failure/test_receiver_*.py`; subprocess kill | DB hooks; real process kill | <2 min | Blocking |
| Protocol contract | Same fixtures parsed, validated, serialized on both sides | `protocol/fixtures/` with SHA-256 manifest | None | <10 s | Blocking on both sides |
| Android JVM | ViewModel states, barcode validator, dedupe state machine, backoff, token-store interface | JUnit, coroutines test with virtual time | Camera, recognizer, transport, clock, token store | <60 s | Blocking |
| Android instrumented | CameraX on fixture bitmaps, Keystore AES-GCM, TLS pin, Compose UI, rotation, process death | `androidTest`; API 34 emulator plus one phone; emulator reaches host at `10.0.2.2` | No sensor; desktop is a host test double | ~15 min | Advisory until flake rate <2% over 20 runs, then blocking |
| Security | Malformed, oversize, unauthenticated, replay, forged, log injection; route inventory | pytest, new `tests/security/`; raw-socket client | Loopback only | <5 min | Automated blocking; manual red-team checklist before release |
| Performance | Latency, CPU, memory, battery, reconnect | Stdlib harness in `scripts/`; Android profiler; `dumpsys`; 240 fps video | None; realistic setup | Hours, nightly on lab | Advisory until baseline, then release-blocking |
| End-to-end hardware | Phone, Windows POS, router, USB scanner | Scripts M-01 to M-11 with evidence pack | None | ~1 day per device pair | Release-blocking |

## 3. Full test matrix

**Preconditions:** P0 receiver off (default). P1 receiver on, one phone paired, Sales open, shift open, draft active. P2 no open shift. P3 read-only. P4 recovery mode (`checkout_service` is `None`). P5 Sales not mounted.

**Layers:** Unit, Integ, GUI, Fail (failure and crash), Contract, JVM, Instr (instrumented), Static, Manual.

**Status:** A automate (planned). E existing RawPOS test. M manual or hardware. S static gate. HTTP codes are placeholders until Agent 4 fixes them.

| ID | Requirement and steps | Layer | Pre | Expected | Status |
|---|---|---|---|---|---|
| **Pairing** | | | | | |
| PAIR-01 | Pair with fresh secret; inspect registry | Integ | P0, window open | 200; token shown once; hash only stored; device listed | A |
| PAIR-02 | Pair with an expired secret, then a used one | Unit | Window | Both refused; no device created; first device unaffected | A |
| PAIR-03 | Ten wrong secrets, then the right one | Unit | Window | Wrong ones refused; throttle engages; right one refused while throttled | A |
| PAIR-04 | Phone B submits phone A's secret | Integ | Window | A paired; B refused; one device record | A |
| PAIR-05 | Revoke phone A; phone B keeps scanning | Integ | Two paired | A gets 401 on next request; B accepted | A |
| PAIR-06 | Close pairing dialog mid-handshake | GUI | Window | Later pairing refused; registry unchanged | A |
| **TLS** | | | | | |
| TLS-01 | Pinned certificate matches; scan | Instr | P1 | Accepted once | A |
| TLS-02 | Desktop presents a different certificate | JVM, Instr | P1 | Refused before body sent; "re-pair" message; no unpinned retry | A |
| TLS-03 | Pinned leaf expired by fixture clock | Unit, Instr | P1 | Behaviour per D1; test asserts the recorded decision | A |
| TLS-04 | Desktop IP changes (M-03) | Integ, Manual | P1 | "Not found at last address"; no unpinned fallback | A, M |
| TLS-05 | Certificate rotated, phones still paired | Integ | P1 | Mismatch leads to re-pair; no trust-on-first-use | A |
| TLS-06 | Plain HTTP to receiver port | Instr, Static | Any | Refused; cleartext disabled in network security config | A |
| **Auth** | | | | | |
| AUTH-01 | No token; bit-flipped token | Integ | P1 | 401; body not parsed or logged | A |
| AUTH-02 | Revoked token; expired token (fixture clock) | Integ | P1 | 401; phone shows "access removed, pair again" | A |
| AUTH-03 | Call every route with a valid token | Static, Integ | P1 | Only health, pair and scans exist; others 404; no product, price or sale field in any response | A |
| AUTH-04 | Inspect registry and logs after a full session | Integ | P1 | No plaintext token or pairing secret | A |
| **Barcode** | | | | | |
| BC-01 | Each of the 8 formats enabled, then disabled | JVM | Fake recognizer | Disabled formats emit nothing; enabled ones emit | A |
| BC-02 | `AB-2002` (Code 128), QR URL, Arabic-Indic EAN digits | JVM, Integ | P1 | Phone sends nothing; forced POST gets 422; `ProductRepository.search` never called | A |
| BC-03 | Store and scan `0212345678909` | Contract, Integ | P1 | Exact string match; no integer parsing | A |
| BC-04 | Stored `212345678909`; phone sends `0212345678909` | Integ | P1 | Outcome per D2 and Agent 3 policy | A |
| BC-05 | Stored `2002`; unknown `1` | Integ | P1 | `2002` matches; `1` per D2 (see BUS-05) | A |
| BC-06 | 65 digits; 1,025-byte body | Contract, Integ | P1 | Phone refuses; desktop 413 or 422; no write | A |
| BC-07 | `2002` plus newline; NUL; U+202E bidi override | JVM, Unit | None | Rejected on both sides; logged escaped | A |
| BC-08 | Empty; spaces only; trailing space | JVM, Integ | P1 | Empty never sent; trailing space trimmed as `resolve_scan` trims | A |
| BC-09 | EAN-13 `2123456789011` (bad check digit) | Unit | None | Per Agent 3; recommended: send, do not reject | A |
| **Duplicates and replay** | | | | | |
| DUP-01 | 60 identical frames of one label | JVM | None | Exactly one event | A |
| DUP-02 | Scan; wait past cooldown; scan again | Integ, parity | P1 | Two events; one line; quantity 2 | A |
| DUP-03 | Commit; drop response; retry same `event_id` | Fail | P1 | Quantity +1 once; retry returns cached outcome | A |
| DUP-04 | Two in-flight requests, same `event_id` | Integ | P1 | One commit; second gets in-progress or same outcome | A |
| DUP-05 | Scan; restart desktop; retry old event | Fail | P1 | Refused as stale session (D5); new draft unchanged | A |
| DUP-06 | Two different labels in one frame | JVM | None | No event; "aim at one code" message | A |
| **Business state** | | | | | |
| BUS-01 | Unknown EAN-13 `2000000000008` | Integ, GUI | P1 | Not found on both screens; no line; one beep (`checkout_view.py:454`) | A |
| BUS-02 | Inactive product (P1); no open shift (P2); read-only (P3) | Integ | Per row | Translated refusal; no line; no invoice row; row counts unchanged | A |
| BUS-03 | Recovery mode | Integ | P4 | 503 "desktop not ready"; no traceback | A |
| BUS-04 | Sales screen not mounted | GUI | P5 | Queued or refused per D6; no exception | A |
| BUS-05 | Stored "Pack 12"; unknown numeric `12` | Integ | P1 | Not found. Fails if the mobile path reuses `resolve_scan`, which name-matches today (`checkout_service.py:529`) | A |
| BUS-06 | Low-stock product | Integ | P1 | Warning on desktop; phone shows added | A |
| PAR-01 | BUS-01 to BUS-06, plus no active draft, through USB and mobile | Integ | P1 | Identical lines, totals, messages, audit rows | A |
| **Keyboard and focus** | | | | | |
| KBD-01 | Existing wedge and typing tests (§4), unchanged | GUI | None | Pass without edits | E |
| KBD-02 | Keyboard-only sale: scan, quantity, tender, finalize | GUI | P1 | Finalized; totals correct | E |
| KBD-03 | Mobile scan while quantity editor is open | GUI | P1 | Editor value unchanged; new line; scan field focused | A |
| KBD-04 | Mobile scan while tender holds "50" | GUI | P1 | Tender unchanged; no finalize; focus per D3 | A |
| KBD-05 | Mobile scan while discount-approval modal is open | GUI | P1 | Queued or refused; nothing applied under the modal | A |
| KBD-06 | Search receiver package for key-event APIs | Static | None | Zero matches: `QKeyEvent`, `postEvent`, `sendEvent` | S |
| **Language and shell** | | | | | |
| LANG-01 | Switch ar to en while paired; then scan | GUI, Integ | P1 | Bridge rebinds to new view; scan lands once | A |
| LANG-02 | `mobile.*` keys in desktop and Android catalogs | Unit | None | Identical key sets; no raw key shown | A |
| LANG-03 | Pairing dialog and status chip: light, dark, Arabic | GUI | None | Both themes render; mirrored in Arabic; barcode text LTR | A |
| **Environment and device events** | | | | | |
| ENV-01 | Pull network after send (F6) | Instr, Manual | P1 | "Not confirmed" within 3 s; retry on restore with same ID; no burst (D4) | A, M |
| ENV-02 | Switch phone Wi-Fi (M-03) | Manual | P1 | Reconnects, or "not found" with guidance | M |
| ENV-03 | Desktop sleeps 5 min, then wakes | Manual | P1 | First acknowledgement within 5 s of wake (proposed) | M |
| ENV-04 | Rotate phone mid-scan | Instr | P1 | Camera rebinds; no duplicate; state kept | A |
| ENV-05 | Kill app in background (`adb shell am kill`) | Instr, Manual | P1 | Pairing kept; camera does not restart itself | A, M |
| ENV-06 | Battery at 15%, power saver on | Manual | P1 | Works or warns; no crash | M |
| ENV-07 | Thermal: 15 min continuous scanning | Manual | P1 | No ANR; no stuck state; latency logged | M |
| ENV-08 | Incoming call during scan (M-11) | Manual | P1 | Camera released; no scans during call; resume needs a tap | M |
| ENV-09 | Kill desktop after receipt, before commit | Fail | P1 | Retry adds one line; no partial state | A |
| **Malicious input** | | | | | |
| SEC-01 | 10 MB request body | Integ | P1 | 413 before full read; memory bounded | A |
| SEC-02 | Malformed JSON; `text/plain` body | Integ | P1 | 400 or 415; no traceback | A |
| SEC-03 | Raw request lines: `/v1/../../x`, `%2e%2e/`, encoded slashes | Integ | None | 404; nothing read from disk | A |
| SEC-04 | 5,000 unauthenticated requests/s for 30 s, then one legitimate scan | Integ, load | P1 | Flood limited; legitimate scan within 1 s | A |
| SEC-05 | Replay captured request; device B's ID with device A's token | Integ | P1 | Replay deduped; forged ID refused with 401 | A |
| SEC-06 | Barcode `2002` plus `\n[INFO] fake`; then grep logs | Unit, Integ | P1 | Escaped; zero token or secret hits | A |
| SEC-07 | Extra fields `"price":0`, `"cart_id":1` | Contract | None | Rejected by strict schema or ignored; never applied | A |
| SEC-08 | Pairing-secret guessing inside the window | Unit | Window | Throttled; success odds per Agent 4 entropy | A |
| **Concurrency** | | | | | |
| CON-01 | Two phones, 500 unique events each, concurrent | Integ | P1 | Quantity exactly 1,000; no exception | A |
| CON-02 | Receiver thread calls `CheckoutService` directly | Unit | None | Passes only if the call is refused; red until the guard exists | A |
| **Regression and gates** | | | | | |
| REG-01 | Full suite with JUnit output | Automated | HEAD | At least baseline passed; zero failed | A |
| REG-02 | `ruff check .`, `mypy src`, `check_no_float.py` | Automated | None | All pass | A |
| REG-03 | Dump `sqlite_master` before and after | Integ | None | Identical | A |
| REG-04 | Diff `pyproject.toml` against HEAD | Static | None | Only approved additions | S |

## 4. Non-regression, and the amended no-network guarantee

**Must stay green, unmodified, on every change:**

- **Wedge and typing** (`tests/gui/test_checkout_ui.py`): `test_wedge_scan_in_the_quantity_editor_lands_in_the_scan_field`, `test_wedge_scan_from_the_grid_opens_no_quantity`, `test_wedge_scan_in_the_tender_field_does_not_confirm_the_sale`, `test_slow_hand_typed_quantity_is_never_a_scan`, `test_scan_adds_a_line_and_finalize_posts_a_sale`, `test_unknown_scan_shows_a_message_and_alerts`.
- **Checkout and shell:** `tests/gui/test_checkout_inactive_product.py`, `tests/gui/test_checkout_approval_flow.py`, `tests/gui/test_returns_ui.py::test_switching_from_checkout_to_returns_and_back_keeps_the_draft`, `tests/gui/test_language_switch_shell.py::test_language_switch_reinstantiates_and_persists`.
- **Services:** `tests/unit/test_checkout_service.py` (including `test_resolving_by_name_adds_a_line`, which the mobile route must not reuse), `tests/unit/test_checkout_discounts.py`, `tests/integration/test_checkout.py`, `test_product_flags.py`, `test_discount_approval.py`, `test_activation_flow.py`.
- **Barcode rules:** `tests/unit/test_product_service.py::test_barcodes_must_be_ascii_digits`, `tests/migrations/test_products_inventory_schema.py::test_barcode_is_ascii_digits_and_unique`.
- **Crash safety:** `tests/failure/test_checkout_rollback.py`, `test_checkout_idempotency.py`, `test_checkout_readonly.py`, `tests/integration/test_recovery_quiescence_integration.py`.
- **Gates:** `ruff check .`, `mypy src`, `py scripts/check_no_float.py`, `py -3 -m pytest -q`.

**No-network amendment.** `tests/integration/test_no_network.py` (patches `socket.socket`, `create_connection`, `getaddrinfo`, `gethostbyname`) and `tests/failure/test_no_network.py` prove that core flows open no sockets. Relaxing them would weaken the guarantee. Add assertions beside them instead:

1. **Keep both files unchanged (default-off).** If the receiver starts automatically, or a core flow opens a socket, they fail.
2. **`NET-STATIC`** (unit). Outside the proposed `rawpos/receiver/` package, no module imports client networking (`urllib.request`, `http.client`, `ftplib`, `smtplib`, `requests`, `httpx`). Inside it, only server primitives are allowed.
3. **`NET-RUNTIME`** (integration, receiver on). Records all socket calls: one listener, on the configured port and interface; no `connect()` or `getaddrinfo()` to a non-loopback address.
4. **`NET-LOOPBACK`.** Receiver tests bind `127.0.0.1` on port 0. A conftest guard fails any test that binds `0.0.0.0` or a LAN address unless marked `lan`, and `lan` tests run only in the lab.
5. **`NET-SELFTEST`.** The old guard must still raise on `socket.socket()` inside a core flow, and its patch list must still contain all four names. This catches silent neutering.
6. **Governance.** Amend Principle II (NON-NEGOTIABLE, `.specify/memory/constitution.md:53–54`) in the same ADR. Proposed wording: core functions never need a network; the receiver is optional, off by default, LAN-only and owner-enabled. Also fix the stale "Principle V" citation in `tests/failure/test_no_network.py:2`.

## 5. Performance benchmarks

**Method for every row.** Nearest-rank percentiles. Report p50, p95, max, n and failures. p95 needs n ≥ 100, otherwise report "insufficient". Each run records commit SHAs for both repos, device model, Android build and API, Windows build, router model, Wi-Fi band, battery and temperature. **Every threshold below is proposed. None has been measured.** A threshold becomes blocking after two clean baseline runs.

| Metric | How | Sample and environment | Proposed threshold |
|---|---|---|---|
| Camera detection | Analyzer timestamps in logs, checked against 240 fps video of label and screen | 100 presentations per phone; EAN-13 at 10–15 cm; three light levels; budget and mid phones | p50 ≤ 150 ms; p95 ≤ 300 ms |
| Analyzer cost | Instrumented test on 1280×720 fixture bitmap | 500 frames, mid phone | p95 ≤ 40 ms |
| Network RTT | `GET /v1/health` on warm TLS connection | 1,000 requests; same subnet; Wi-Fi 5 or 6 router | p50 ≤ 30 ms; p95 ≤ 80 ms; cold handshake p95 ≤ 200 ms |
| Scan to cart | 240 fps video: T0 = phone overlay shows decode, T1 = POS row appears. No clock sync needed | 100 scans, two phones | p50 ≤ 400 ms; p95 ≤ 800 ms |
| Duplicate rate | Printed labels held 1.5 s; count events per presentation | 1,000 presentations | ≤ 0.5% of presentations give more than one event; zero duplicate cart effects |
| Reconnect | Toggle phone Wi-Fi; sleep and wake desktop | 20 cycles | First acknowledgement p95 ≤ 5 s; "not confirmed" shown ≤ 3 s after failure |
| Android memory | `adb shell dumpsys meminfo` every 60 s | 30 min scanning, mid phone | PSS ≤ 200 MB; growth ≤ 10% from minute 5 to 30 |
| Camera stability | Frame count per 10 s window; camera error callbacks | 30 min continuous | Zero camera errors; ≥ 95% of windows have frames; no crash or ANR |
| Battery | `dumpsys battery` level samples; `dumpsys batterystats` for wake locks | 60 min scanning; 60 min idle | Scanning ≤ 15% per hour; idle background ≤ 2% per hour |
| Desktop CPU idle | `typeperf` on the Python process counter | 10 min, receiver on, no phone | ≤ 1% of one core |
| Desktop CPU at 10 scans/s | 3,000 scans over 5 min from two simulated phones; GUI-thread lag probe | Laptop on mains power | ≤ 10% of one core; GUI stall ≤ 100 ms |

## 6. Failure injection

| # | Fault | How injected | Required safe behaviour |
|---|---|---|---|
| F1 | Kill receiver mid-request | Subprocess kill after body read: once before commit, once after | Before commit: retry adds one line. After commit: the draft died with the process, so retry is refused as stale (D5). Never a partial line |
| F2 | Drop the response | Proxy closes socket after commit | "Not confirmed" on phone; retry adds nothing extra (DUP-03) |
| F3 | Close the DB mid-request | `begin_critical_operation`, then close during request | 503 busy; no partial write; no exception leaves the server thread |
| F4 | Start a restore while scanning | Trigger the restore path | Scans refused during quiescence; resume afterward; nothing written to the replaced DB |
| F5 | Language-switch remount mid-scan | `_switch_language` during in-flight event | One commit; bridge rebinds; phone message still correct |
| F6 | Pull the network cable | Desktop NIC disabled; phone Wi-Fi off | "Not sent" within 3 s; no offline queue (D4); no burst on return |
| F7 | Fill the dedupe cache | Insert capacity plus one event IDs | Oldest evicted; in-flight IDs never evicted; memory bounded |
| F8 | Corrupt the device registry | Truncate, garble, or drop the table or file | Fail closed: all tokens refused; GUI says "re-pair"; never "trust all" |
| F9 | Revoke mid-session | Revoke during a scan loop | Next request 401; no further events applied |
| F10 | Clock skew | Desktop +10 min; phone −10 min | Scans still applied (timestamps diagnostic); TLS per D1; proposed warning above 5 min |
| F11 | Low disk | Patched write raises `OSError` mid-write | Pairing fails with a message; previous registry intact (atomic write, `infra/atomic_io.py`) |

## 7. Manual test scripts

Each script lists duration, steps, pass criteria and what to record.

- **M-01 First pairing** (5 min). 1) POS: Sales, then *Connect phone*. 2) Phone: *Pair*, scan the QR. 3) Both show the same 4-digit code. 4) Tap *Confirm* on the phone. **Pass:** both show *Connected* within 30 s. **Record:** photo of both screens.
- **M-02 Daily scanning** (5 min). Scan products A, B, C, D, E once each; then A and B once more; then one unknown label. **Pass:** POS shows five lines, A and B at quantity 2, the others at 1. The unknown label adds nothing and shows *Not found*.
- **M-03 Wi-Fi change** (5 min). Put the phone on guest Wi-Fi and scan. Switch to staff Wi-Fi and scan. **Pass:** the guest scan shows a clear message, not silence or a crash. The scan after switching back is added within 30 s. **Record:** timestamps and both screenshots.
- **M-04 Revoke and re-pair** (5 min). POS: revoke the phone. Scan on the phone, then pair again with M-01. **Pass:** the revoked phone shows "access removed" and adds nothing; the re-paired phone works.
- **M-05 USB scanner regression** (5 min). Scan 10 labels with the USB scanner, with the phone connected and disconnected. **Pass:** each label adds one line; focus returns to the scan box; no mobile message appears.
- **M-06 Keyboard-only sale** (5 min). Do not touch the mouse or screen. Scan, change one quantity, enter cash, finalize. **Pass:** the sale finalizes with correct total and change.
- **M-07 Arabic layout** (5 min). Set phone and POS to Arabic. **Pass:** layouts mirror; barcode digits read left to right; no clipped text.
- **M-08 Dark and light** (3 min). Switch both to dark, then light. **Pass:** text and status chips are readable in both.
- **M-09 Low battery** (10 min). Set the phone to 15% or enable battery saver; scan 10 labels. **Pass:** scanning works or a clear warning shows; no crash; no lost scans.
- **M-10 Unknown item** (2 min). Scan a label that is not in the POS. **Pass:** both screens say *Not found*; no line; POS beeps once.
- **M-11 Phone call** (5 min). Start scanning; take a call on the phone; hang up. **Pass:** no scans during the call; scanning resumes only after tapping *Resume*.

Every record includes the date, build numbers, device serials, photos and tester initials.

## 8. Device and environment matrix

- **Android, mandatory:** one budget phone (≤ 3 GB RAM, fixed or weak macro focus), one mid-range, one current flagship. Test minSdk (Agent 2), one mid API and the latest stable. One phone in Arabic locale with Arabic-Indic numerals. One tall 20:9 or foldable for layout.
- **Android, best effort:** no Google Play services; 5-inch phone; tablet.
- **Emulator:** API 34 x86_64 for pipeline and UI only. It is not evidence for camera, thermal, battery or radio. It needs hardware virtualization on the host, which I did not verify.
- **Windows POS, mandatory:** Windows 11 at 100% and 150% DPI; one touchscreen POS panel at 1366×768 (assumed, confirm with the shop); Firewall on with Private profile and Public refused; a consumer router with AP/client isolation, which must produce a clear explanation.
- **Windows, best effort:** Windows 10 22H2 (past end of mainstream support; only if a shop runs it); 125% and 200% DPI; firewall off; VPN active.
- **Networking, testable here:** this dev machine has six interfaces with IPv4 addresses: Bluetooth PAN, Ethernet, two "Local Area Connection*", Hyper-V "vEthernet (Default Switch)", and Wi-Fi. The QR must advertise the LAN address, never the vEthernet one.
- **Scanners:** one USB HID scanner model (mandatory); a second model or a 2D imager (best effort).

## 9. CI and evidence

**Can run in CI** (needs a host; none is configured):

- Windows runner: all desktop layers with offscreen Qt, ruff, mypy, no-float, desktop contract tests, certificate matrix, automated security tests, and a desktop perf smoke test without thresholds.
- Linux runner with hardware virtualization: Android JVM tests, and `connectedAndroidTest` on an API 34 emulator (advisory until stable).

**Cannot run in CI:** real camera optics and ML Kit on real labels; real Wi-Fi, AP isolation and roaming; battery and thermal; USB hardware; touch panels and DPI; sleep and wake; phone calls; Firewall prompts (dev machine only, with consent and rollback).

**Evidence:**

- JUnit XML (`pytest --junitxml`, Gradle `build/test-results`), with commit SHA, exact command and counts.
- Receiver logs as JSON lines: event ID, device ID, status, latency. No tokens or pairing secrets.
- Screenshots for GUI tests and manual scripts; 240 fps videos; performance CSV.
- Signed manual checklists with date, build, serials, photos and initials.

**Rule:** no QA pass is claimed without an artifact. A claim without commit SHA, command and output file is reported as "not verified." This is the bar the handover already sets for task reports (`RawPOS_Project_Master_Handover(1).md:1522`).

## 10. Entry, exit, Definition of Done, traceability

Phase names are provisional; align them with the Architect's plan, which is not in my fact pack.

- **Entry to implementation:** baseline JUnit artifact at HEAD; no-network ADR accepted (D8); protocol fixtures v1 frozen; D1 to D7 recorded.
- **Exit per phase:** every row for that phase passes as A or E with artifacts; no open Sev1 or Sev2; REG-01 to REG-04 green.
- **Release:** every mandatory row passes; M-01 to M-11 completed on mandatory devices; performance thresholds met or waived in writing by the owner; automated security suite green and red-team checklist signed; Arabic copy reviewed by a native speaker; no unapproved dependency; each of the 13 acceptance criteria mapped to PASS evidence once the list is supplied.

**Traceability to the user's stated scope constraints** (from `shared-facts.md` §8, since the 13 numbered criteria are missing):

| Constraint | Tests |
|---|---|
| Android scans and transmits only | AUTH-03, BC-02, SEC-07, KBD-06 |
| Desktop is sole authority | PAR-01, BUS-01 to BUS-06, DUP-03 |
| No new framework or unnecessary dependency | REG-04 |
| No schema change expected | REG-03 |
| USB scanner preserved | KBD-01, M-05 |
| Keyboard-only checkout preserved | KBD-02, M-06 |
| Focus restoration | KBD-03 to KBD-05 |
| Barcode lookup preserved | BC-03, BC-04, BUS-05 |
| Activation rules preserved | `test_activation_flow.py`, `tests/failure/test_no_network.py` |
| Inventory validation and pricing preserved | BUS-06, `test_product_flags.py`, `test_checkout_discounts.py` |
| Payments and invoice finalization preserved | KBD-02, `test_checkout_rollback.py`, `test_checkout_idempotency.py`, DUP-03 |
| Arabic and English preserved | LANG-01 to LANG-03, M-07 |

## 11. Test data

- **Barcodes** (valid check digits; prefix 2 is GS1's in-store range, so no real product collides): EAN-13 `2123456789010` (valid), `2123456789011` (bad check), `2000000000008` (unknown). UPC-A `212345678909` and its EAN-13 form `0212345678909`. EAN-8 `21234569`. ITF-14 `10012345678902`. Short codes `2002`, `1001`, `1002` (reused by existing tests). Arabic-Indic variants are generated from these.
- **Products:** synthetic English and Arabic names, integer minor-unit prices, built with `build_checkout` and `stock_product` (`tests/fakes/checkout.py:66, 209`). Variants: active, inactive, low stock.
- **Device registry:** three devices (active, revoked, token expired). Tokens are generated per session; fixtures hold SHA-256 hashes only.
- **Certificates:** generated at test time with `cryptography`: valid, expired, not yet valid, wrong SAN, rotated, CA-signed. No private keys committed.
- **Clock:** fixed `2026-11-20T09:00:00Z` through an injected clock, following the only clock abstraction I found (`licensing/license.py:39–49`). Never patch `datetime` globally. Wedge timing uses `time.monotonic_ns` directly (`checkout_view.py:305, 319`), so those tests keep real time.
- **Protocol fixtures:** versioned request and response JSON with a SHA-256 manifest, read by both sides.
- **Random barcodes:** seeded stdlib `random`. No property-testing dependency without approval.

## 12. Gaps and risks (top 10)

1. **No Android environment.** `adb`, `java` and `gradle` are not on PATH; `ANDROID_HOME` and `JAVA_HOME` are unset. *Mitigation:* decide tooling and procurement before Phase 1. Android results stay "not verified" until then.
2. **No CI.** No `.github/`; remote CI status unknown. *Mitigation:* pick a host now; start with local scripts that emit the same artifacts.
3. **Camera not reproducible in emulator.** *Mitigation:* fixture-bitmap pipeline tests in CI; a real-device latency lab for camera numbers.
4. **ML Kit vendor behaviour** (UPC-A to EAN-13 expansion; format detection changes by version). *Mitigation:* pin the version; build a golden set of at least 100 photographed labels on target devices; rerun on every ML Kit upgrade.
5. **TLS pinning complexity** (rotation, expiry, IP change, two trust stores). *Mitigation:* generated certificate matrix; one pin-check class with table tests; real-device checks for expiry and clock.
6. **Flaky GUI tests.** Wedge timing is real-time (`checkout_view.py:305, 319`; tests `qtbot.wait(80)` against a 50 ms gap). *Mitigation:* never loosen the wedge tests; new tests use `qtbot.waitUntil`; a flake is fixed or quarantined with an owner and a 7-day expiry.
7. **Over-mocking.** A fake desktop would hide BUS-05. *Mitigation:* every business rule has one real-stack integration test; mocks only at camera, transport and clock.
8. **Localization drift.** `mobile.*` keys split between Android resources and the desktop catalog; Arabic copy is unreviewed. *Mitigation:* key-parity tests on both sides; `[needs review]` strings block release; native reviewer sign-off.
9. **No-network guarantee weakened.** *Mitigation:* §4; old tests unchanged; static gate; self-test.
10. **Stale retries after restart.** Cart and dedupe state are in memory, so a retry after restart can land in an empty new draft. *Mitigation:* DUP-05, F1 and ENV-09; decision D5.

Other risks: fixture drift between repos (manifest check in both builds); device registry location forces either a schema change or a new file (D7); benchmark noise on a shared Windows machine (record Defender and Update state); unverified 1,450 baseline (§13).

## 13. Verified vs assumed, dependencies, decisions

**Verified in this review (read-only):**

- HEAD `797167acbcb14d96b476891a573a7e31638acf12`; clean tree (`git rev-parse HEAD`, `git status --short`).
- 215 `test_*.py` files with 1,190 `def test_` definitions (unit 465, gui 341, integration 268, failure 65, migrations 51). Parametrized cases are not counted.
- Every named test in §4 exists. Guard patches: `tests/integration/test_no_network.py:29–35`, `tests/failure/test_no_network.py:22–26`.
- Name fallback `checkout_service.py:527–530`; digit-only CHECK `0004_products_inventory.py:47`; beep `checkout_view.py:454`; wedge constants `checkout_view.py:60–61`; time source `checkout_view.py:305, 319`.
- No `.github/`; no Android tools on PATH; `ANDROID_HOME` and `JAVA_HOME` unset; six IPv4 interfaces; all three firewall profiles enabled.
- Gates and evidence rule: handover lines 169 and 1522. Constitution: `.specify/memory/constitution.md:53–54`. Repo spec 014 excludes mobile targets: `specs/014-frontend-redesign-audit/spec.md:334`.

**Assumed or not verified:**

- **The 1,450-pass, 134.97 s baseline** comes from the brief, which calls it measured today. The fact pack (§7) calls the same figure a handover report. Other documents give different totals at other milestones (the handover says 1,450 at feature 014; spec 014 records 1,214 on 2026-10-28, line 112). I did not run pytest, which this review excludes because it writes caches. Re-run with JUnit output before Phase 1.
- All thresholds and budgets; emulator behaviour; ML Kit behaviour; the `10.0.2.2` alias; Windows 10 support status (from general knowledge).
- Whether the repo's remote has CI attached.
- I did not use any other agent's output. I read only the briefs and the fact pack.

**Dependencies on other agents:**

- **Agent 1 (architecture):** bridge and session model (DUP-05, BUS-03, BUS-04, LANG-01, CON-02); device registry location (F8).
- **Agent 2 (Android):** HTTP stack, minSdk, test seams (fake transport and clock), lifecycle rows (ENV).
- **Agent 3 (barcode):** enabled format set, length rules, leading-zero policy, duplicate parameters (BC and DUP expected values).
- **Agent 4 (networking and security):** field names, status codes, pairing window, rate and size limits, pin rules (TLS, AUTH and SEC values).
- **Agent 5 (UI/UX):** screen states and `mobile.*` copy keys (LANG rows, manual-script wording).

**Decisions needed.** Each one changes matrix rows.

- **D1** Expired pinned leaf: refuse, or accept on pin with a warning? (TLS-03)
- **D2** Unknown numeric token: exact lookup only (recommended), or name-match parity? (BUS-05, BC-04)
- **D3** After a mobile scan while typing, restore focus to that field, or move it to the scan box? (KBD-04)
- **D4** Offline queue: none (recommended), or store-and-forward? (F6, ENV-01)
- **D5** Stale session after restart: reject events from a previous session by session ID? (DUP-05, F1)
- **D6** Sales not mounted: queue, refuse, or open Sales? (BUS-04)
- **D7** Device registry: encrypted DB table (a schema change to escalate), or a protected file (a new corruption path)? (F8)
- **D8** Approve §4 and the constitution wording change?

**Questions for you:**

1. Send the 13 numbered acceptance criteria. I will map each one to test IDs.
2. Which phones exist or are planned, and which Android versions do shops run? Is Windows 10 still in use?
3. Which CI host, if any: a GitHub remote, or a self-hosted Windows machine with virtualization?
4. May I re-run the baseline with `--junitxml` before implementation? It writes cache files.
5. Who is the native Arabic reviewer?