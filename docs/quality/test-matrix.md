# Test Matrix

**Status:** proposed. **Nothing in this matrix has been run.** It is the plan that Phase 6 executes.
**Baseline:** RawPOS `797167a`, full suite measured 1,450 passed / 134.97 s (Python 3.14.3).
**Related:** `qa-strategy.md`, `performance-requirements.md`

---

## 1. How to read

**Layers:** `U` unit · `I` integration · `G` GUI (pytest-qt) · `F` failure/crash · `C` contract ·
`J` Android JVM · `X` Android instrumented · `S` static · `M` manual/hardware

**Status:** `P` planned automation · `E` existing RawPOS test · `M` manual · `S` static gate

**Preconditions:**

| ID | State |
|---|---|
| P0 | Receiver off (default), app signed in |
| P1 | Receiver on, one phone paired, Sales active, shift open, draft active, full mode |
| P2 | No open shift |
| P3 | Read-only license |
| P4 | Recovery mode (`checkout_service is None`) |
| P5 | Sales module not mounted / another module active |
| P6 | Invoice context changed (hold/resume/finalize/cancel) |
| P7 | Desktop restarted |
| P8 | Restore/backup transition in progress |
| P9 | A modal dialog is open (discount approval, hold/resume, settings) |

**Admission policy (D17):** a mobile scan is admitted only when Sales is active, the session is
full-mode, an open shift exists, and no transition is underway. These are **mobile admission rules**,
not changes to checkout business logic; USB/keyboard behavior is unchanged. This avoids telling the
phone "added" when the desktop cannot display or complete the sale.

---

## 2. Pairing

| ID | Test | Layer | Pre | Expected | Status |
|---|---|---|---|---|---|
| PAIR-01 | Pair with a fresh secret; inspect registry | I | P0 | 200; token returned once; only hash stored; device listed; QR unusable | P |
| PAIR-02 | Expired secret, then used secret | U | — | `410 pairing_expired` / `409 pairing_used`; no device | P |
| PAIR-03 | 10 wrong secrets, then the right one | U | — | Wrong refused; throttle engages; right refused while throttled | P |
| PAIR-04 | Phone B submits phone A's secret | I | Window | One device only; second refused; A unaffected | P |
| PAIR-05 | Deny on desktop | I/G | Window | `403 pairing_denied`; session consumed; no device | P |
| PAIR-06 | Close the dialog mid-handshake | G | Window | Pairing refused; registry unchanged | P |
| PAIR-07 | Mismatched 6-digit code | G | Window | Cashier can deny; no token issued | M |
| PAIR-08 | Replace the active device | I/G | P1 | Old token revoked immediately; new device works | P |
| PAIR-09 | Reinstall the app (token lost) | M | P1 | Old device revoked on desktop; re-pair works | M |
| PAIR-10 | Same `pairing_attempt_id`, different body | U | Window | `409 pairing_attempt_conflict` | P |
| PAIR-11 | Two phones scan one QR simultaneously | I | Window | One pending attempt; other `409 pairing_in_progress` | P |
| PAIR-12 | QR shoulder-surf: observer pairs first | G/M | Window | Desktop shows unexpected device name; cashier denies | M |

---

## 3. TLS and certificate lifecycle

| ID | Test | Layer | Pre | Expected | Status |
|---|---|---|---|---|---|
| TLS-01 | Pinned certificate matches | X | P1 | Scan accepted once | P |
| TLS-02 | Different certificate (same host/port) | J/X | P1 | Abort before body; "desktop identity changed"; no unpinned retry | P |
| TLS-03 | Expired / not-yet-valid certificate | U/X | P1 | Abort; clear clock/cert message; fail closed | P |
| TLS-04 | IP changes, same key, new SAN | I/X | P1 | Pin still matches; endpoint updated; scan works | P |
| TLS-05 | Key rotation | I/X | P1 | Pin mismatch; re-pair required; no TOFU | P |
| TLS-06 | Plain HTTP to the TLS port | I/X | P1 | Refused; no cleartext listener | P |
| TLS-07 | SAN does not include the connected IP | X | P1 | Hostname verification fails; clear error | P |
| TLS-08 | Certificate chain / self-signed handling | U | — | Self-signed accepted only via pin; CA-signed not trusted by accident | P |
| TLS-09 | TLS 1.0/1.1 client | I | P1 | Handshake refused; 1.2+ only | P |
| TLS-10 | Pin mismatch after a desktop reinstall | M | P1 | Re-pair required; no data leak | M |

---

## 4. Authentication and authorization

| ID | Test | Layer | Pre | Expected | Status |
|---|---|---|---|---|---|
| AUTH-01 | No token / empty bearer | I | P1 | 401; body not dispatched | P |
| AUTH-02 | Bit-flipped / malformed token | I | P1 | 401; no distinction between bad device and bad token | P |
| AUTH-03 | Revoked token | I | P1 | 403 `device_revoked`; phone clears credentials | P |
| AUTH-04 | Valid token, different `device_id` | I | P1 | 401; no dispatch | P |
| AUTH-05 | Route inventory: every method/path | S/I | P1 | Only pair/scans/context/health; all else 404; no product/price/sale fields | P |
| AUTH-06 | Registry/log scan for secrets | I | P1 | No plaintext token, pairing secret, or private key | P |
| AUTH-07 | Token replay from another IP | I | P1 | Accepted (token is device-bound, not IP-bound) or rejected per policy — test records the decision | P |
| AUTH-08 | Pairing token scope | I | P1 | Token cannot call any route beyond scans/context | P |

---

## 5. Barcode capture and validation

| ID | Test | Layer | Pre | Expected | Status |
|---|---|---|---|---|---|
| BC-01 | Each enabled format decoded | J/X | — | Emits one candidate after stabilization | P |
| BC-02 | Each disabled format decoded | J | — | No event; no request | P |
| BC-03 | Alphanumeric Code 128 `AB123` | J/I | P1 | Phone refuses; forced POST → 400; `ProductRepository.search` never called | P |
| BC-04 | QR URL in product mode | J/I | P1 | Refused; not opened; no request | P |
| BC-05 | Arabic-Indic digits | J | — | Refused; no transliteration | P |
| BC-06 | Leading zeros `0042` | I | P1 | Exact lookup; zeros preserved | P |
| BC-07 | UPC-A 12 vs EAN-13 13 representation | I | P1 | Exact only; mismatch → `not_found`; no alias | P |
| BC-08 | 1-digit and 4-digit internal codes | J/I | P1 | Predicate accepts; exact lookup | P |
| BC-09 | 65 digits / 1,025-byte body | J/I | P1 | Phone refuses; desktop 400/413; no write | P |
| BC-10 | Whitespace, newline, NUL, bidi override | J/U | — | Refused on both sides; escaped if logged | P |
| BC-11 | Unknown numeric `12` vs product name `Pack 12` | I | P1 | **Not found**; name search not used (BUS-05) | P |
| BC-12 | Bad EAN check digit returned by ML Kit | J/I | P1 | Accepted if ML Kit returns it; no extra local rejection | P |
| BC-13 | Two codes in the target region | J | — | No event; "frame one barcode" | P |
| BC-14 | QR pairing mode | X | P0 | Pairing QR decodes; product formats ignored in this mode | P |
| BC-15 | Mirrored/reflective/damaged label | M | P1 | Clear failure; no invented digits | M |

---

## 6. Duplicates, retries, and context

| ID | Test | Layer | Pre | Expected | Status |
|---|---|---|---|---|---|
| DUP-01 | 60 identical frames | J | — | Exactly one event | P |
| DUP-02 | Deliberate second scan after re-arm | I | P1 | Two events; one line; quantity 2 | P |
| DUP-03 | Response lost after commit; retry same id | F | P1 | Cached `duplicate`; one line | P |
| DUP-04 | Two concurrent requests, same id | I | P1 | One dispatch; one gets processing/cached | P |
| DUP-05 | Desktop restart between send and retry | F | P7 | No auto-replay; "delivery uncertain"; new draft unchanged | P |
| DUP-06 | Same id, different barcode | I | P1 | `409 event_id_conflict`; no dispatch | P |
| DUP-07 | Dedupe table at 50,000 | U/F | P1 | `503 dedupe_capacity`; in-flight never evicted | P |
| DUP-08 | Finalize between capture and send | I/G | P6 | `rejected_stale_context`; no line in the new invoice | P |
| DUP-09 | Hold/resume changes context | I | P6 | Old context rejected; new scans work after refresh | P |
| DUP-10 | Language remount does not change context | G | P1 | Same invoice, same context; scan works once | P |
| DUP-11 | Same barcode, two intentional events | I | P1 | Quantity 2; no value-based dedupe | P |

---

## 7. Business behavior and parity

| ID | Test | Layer | Pre | Expected | Status |
|---|---|---|---|---|---|
| BUS-01 | Unknown exact barcode | I/G | P1 | `not_found`; no line; desktop beeps once | P/E |
| BUS-02 | Inactive product | I/G | P1 | `inactive`; no line; translated message | P/E |
| BUS-03 | No open shift | I/G | P2 | `rejected_no_shift`; no dispatch; phone says open a shift | P |
| BUS-04 | Read-only license | I/G | P3 | `rejected_read_only`; no dispatch; no misleading add | P |
| BUS-05 | Recovery mode | I | P4 | `rejected_unavailable`; no traceback; receiver paused | P |
| BUS-06 | Sales module not mounted | G | P5 | `rejected_no_checkout`; no auto-navigation | P |
| BUS-07 | Low stock after add | I/G | P1 | Desktop warning; phone says added | P |
| BUS-08 | Negative stock after add | I/G | P1 | Desktop warning; add still allowed per existing rule | P |
| BUS-09 | USB vs mobile parity for BUS-01…BUS-08 | I/G | P1 | Same lines, totals, messages, audit rows (except admission gates) | P |
| BUS-10 | Finalize after mobile-added lines | I/G | P1 | Correct invoice, stock movement, audit | P/E |
| BUS-11 | Discount/approval after mobile add | G | P1 | Existing flow unchanged | E |
| BUS-12 | Hold/resume after mobile add | I/G | P1 | Quantity/totals preserved | P/E |
| BUS-13 | Shift rules unchanged for USB | G | P2 | Existing behavior unchanged | E |
| BUS-14 | Read-only rules unchanged for USB | G | P3 | Existing behavior unchanged | E |
| BUS-15 | No new DB tables/columns | I/S | — | `sqlite_master` identical | P |
| BUS-16 | Audit entry for each mobile add? | I | P1 | Decision: cart adds are not audited today; no new audit rule. Record the decision | P |
| BUS-17 | Domain refusal with a stable `reason_code` (e.g. NoBaseCurrency) | I | P1 | `rejected_domain`; generic/localized message; no line | P |
| BUS-18 | Modal discount/approval dialog open | G | P9 | `rejected_busy`; no mutation; discount base unchanged | P |

---

## 8. Keyboard, focus, and input regression

| ID | Test | Layer | Pre | Expected | Status |
|---|---|---|---|---|---|
| KBD-01 | Existing wedge/focus suite unchanged | G | — | All pass, no edits | E |
| KBD-02 | Keyboard-only sale | G | P1 | Finalizes; correct total/change | E |
| KBD-03 | Mobile scan while quantity editor open | G | P1 | Editor unchanged; new line; focus preserved | P |
| KBD-04 | Mobile scan while tender has `50` | G | P1 | Tender unchanged; no finalize; focus preserved | P |
| KBD-05 | Mobile scan while discount modal open | G | P9 | `rejected_busy`; no mutation under the modal | P |
| KBD-06 | Static scan for key-event APIs in mobile package | S | — | Zero `QKeyEvent`, `postEvent`, `sendEvent` | S |
| KBD-07 | Human typing at 80 ms gaps | G | — | Never a scan (existing test) | E |
| KBD-08 | USB scanner with phone connected | G/M | P1 | USB behavior identical | P/M |

---

## 9. Language, theme, and shell

| ID | Test | Layer | Pre | Expected | Status |
|---|---|---|---|---|---|
| LANG-01 | Switch ar→en while paired; scan | G/I | P1 | Bridge rebinds; one add; no stale view | P |
| LANG-02 | `mobile.*` key parity (desktop ar/en, Android values/values-ar) | U | — | Identical sets; no raw key | P |
| LANG-03 | Pairing dialog light/dark/Arabic | G | — | Renders; mirrored; barcode LTR | P |
| LANG-04 | Digit-style switch while paired | G | P1 | No disconnect; context stable | P |
| LANG-05 | Phone Arabic/English | X/M | — | Mirrored layout; codes LTR; no clipping | P/M |
| LANG-06 | 200% font | X/M | — | No truncation; buttons grow | M |

---

## 10. Environment and device events

| ID | Test | Layer | Pre | Expected | Status |
|---|---|---|---|---|---|
| ENV-01 | Pull network after send | X/M | P1 | "Not confirmed" ≤ 3 s; retry same id; no burst | P/M |
| ENV-02 | Switch phone Wi-Fi | M | P1 | Reconnect or clear "not found" | M |
| ENV-03 | Desktop sleep 5 min, wake | M | P1 | First ack ≤ 5 s after wake | M |
| ENV-04 | Rotate phone mid-scan | X | P1 | Camera rebinds; no duplicate | P |
| ENV-05 | Kill app in background | X/M | P1 | Pairing kept; camera does not auto-restart | P/M |
| ENV-06 | Battery 15% / saver | M | P1 | Works or warns; no crash | M |
| ENV-07 | Thermal 30 min continuous | M | P1 | No ANR/crash; latency logged | M |
| ENV-08 | Incoming call | M | P1 | Camera released; no scans; resume by tap | M |
| ENV-09 | Desktop restart | F | P7 | New instance id; no auto-replay | P |
| ENV-10 | App background/foreground | X | P1 | Camera off in background; resume by tap | P |
| ENV-11 | Device lock/unlock | M | P1 | No invisible scanning | M |
| ENV-12 | Low storage | M | P1 | Clear error; no corrupt registry | M |

---

## 11. Security

| ID | Test | Layer | Pre | Expected | Status |
|---|---|---|---|---|---|
| SEC-01 | 10 MB body | I | P1 | 413 before full read; bounded memory | P |
| SEC-02 | Malformed JSON / `text/plain` | I | P1 | 400/415; no traceback | P |
| SEC-03 | Path traversal / encoded slashes | I | — | 404; no filesystem access | P |
| SEC-04 | 5,000 unauth req/s for 30 s, then a legit scan | I/load | P1 | Flood limited; legit ≤ 1 s | P |
| SEC-05 | Replay + forged device id | I | P1 | Replay deduped; forged 401 | P |
| SEC-06 | Log injection via barcode | U/I | P1 | Escaped; no token/secret/barcode in logs | P |
| SEC-07 | Extra fields (`price`, `cart_id`) | C | — | Rejected/ignored; never applied | P |
| SEC-08 | Pairing brute force | U | Window | Throttled; infeasible odds | P |
| SEC-09 | No data endpoints | S/I | P1 | No product/price/sale/customer route or field | P |
| SEC-10 | No custom crypto | S | — | Only `ssl`, `cryptography`, `hashlib`, `hmac`, `secrets` | S |
| SEC-11 | Firewall public profile | M | P1 | No public inbound; clear setup error | M |
| SEC-12 | Bind interface selection | I/M | P1 | Only selected private IP; no wildcard/VPN | P/M |
| SEC-13 | Slowloris | I | P1 | Header/body timeouts; bounded workers | P |
| SEC-14 | Registry tamper | F | P1 | Fail closed; re-pair | P |
| SEC-15 | DPAPI failure | U/F | P1 | Receiver off; no plaintext fallback | P |

---

## 12. Concurrency, lifecycle, and restore

| ID | Test | Layer | Pre | Expected | Status |
|---|---|---|---|---|---|
| CON-01 | Two simulated clients, 500 unique each | I | P1 | Quantity exactly 1,000; no exception | P |
| CON-02 | Off-thread call into `submit_scan` | U/G | — | Refused/logged; cart unchanged | P |
| CON-03 | GUI busy beyond the 2 s deadline | I/G | P1 | `202 unknown`; dispatch cancelled if not started | P |
| CON-04 | Shutdown mid-request | F | P1 | Stop accept; cancel pending; DB closes cleanly | P |
| CON-05 | Restore mid-request | F | P8 | 503; no dispatch; DB replaced safely | P |
| CON-06 | Sign-out while paired | G/I | P1 | Receiver stops; phone sees disconnect | P |
| CON-07 | Recovery entry while paired | I | P4 | Receiver stops before DB close | P |
| CON-08 | Backup during idle | I | P1 | Backup proceeds; scans rejected while quiescent | P |

---

## 13. Build and static gates

| ID | Test | Layer | Expected | Status |
|---|---|---|---|---|
| AND-01 | `assembleDebug` and `assembleRelease` | build | **Debug passed**; release excluded from Phase 1 | **PASS (debug) / N/A (release)** |
| AND-02 | `lintDebug` | build | **Passed: 0 errors, 14 version notices** | **PASS** |
| AND-03 | `testDebugUnitTest` | build | **Passed: 10 tests, 0 failures** | **PASS** |
| AND-04 | `connectedDebugAndroidTest` | build | **Passed: 1 test on API 36 emulator** | **PASS** |
| AND-05 | R8/minify release build | build | Not run — release is explicitly excluded from Phase 1 | N/A |
| AND-06 | Permission inventory | S | **Passed: only the AndroidX signature-level dynamic-receiver permission; no CAMERA/INTERNET** | **PASS** |
| DESK-01 | Import architecture test | S | `rawpos.mobile` server/protocol never imports services/repositories/ui | P |
| DESK-02 | Thread-affinity test | U | Off-thread dispatch refused | P |
| DESK-03 | `ruff check .` | S | Pass | E |
| DESK-04 | `mypy src` | S | Pass | E |
| DESK-05 | `check_no_float.py` | S | Pass | E |
| DESK-06 | `pytest -q` | I | 1,450+ passed, 0 failed | E |

**Phase 1 note:** AND-01 (debug), AND-02, AND-03, AND-04, and AND-06 were executed on 2026-11-20.
Evidence: `../phase-1/build-evidence.md`. Release (AND-05) was explicitly excluded by the Phase 1
authorization. The matrix remains the plan for later phases; no other row is claimed as run.

---

## 14. Performance

The thresholds and methods are in `performance-requirements.md`. Matrix rows:

| ID | Test | Layer | Expected | Status |
|---|---|---|---|---|
| PERF-01 | Camera detection p50/p95 | M | ≤ 150 / ≤ 300 ms | M |
| PERF-02 | Analyzer cost p95 | X | ≤ 40 ms | P |
| PERF-05 | Warm RTT p50/p95 | I | ≤ 30 / ≤ 80 ms | P |
| PERF-10 | End-to-end p50/p95 | M | ≤ 400 / ≤ 800 ms | M |
| REL-01 | Duplicate cart mutations | F | 0 in 10,000 | P |
| REL-03 | Reconnect p95 | M | ≤ 5 s | M |
| RES-01 | Android PSS | M | ≤ 200 MB | M |
| RES-05 | Camera stability 30 min | M | 0 errors; ≥ 95% frame windows | M |
| RES-06 | Desktop CPU idle | M | ≤ 1% of a core | M |
| RES-09 | APK size | build | ≤ 30 MB | P |

---

## 15. Acceptance criteria traceability

The 13 criteria from the project brief:

| # | Criterion | Automated / manual evidence | Status |
|---|---|---|---|
| 1 | Android can pair with RawPOS | PAIR-01, PAIR-08, TLS-01, M-01 | Not run |
| 2 | Android can scan supported formats | BC-01…BC-14, M-02 | Not run |
| 3 | RawPOS receives authenticated scan events | AUTH-01…AUTH-08, SEC-04, SEC-05 | Not run |
| 4 | Existing checkout pipeline processes scans | BUS-09, BUS-10, DUP-02, BUS-12 | Not run |
| 5 | USB scanners continue working | KBD-01, KBD-08, M-05 | Not run |
| 6 | Keyboard input continues working | KBD-02, M-06 | Not run |
| 7 | Human typing not misclassified as barcode | KBD-07 (existing), KBD-04 | Not run |
| 8 | Duplicate camera detections do not duplicate cart entries | DUP-01…DUP-07, REL-01, M-02 | Not run |
| 9 | Network failures handled safely | ENV-01, ENV-09, DUP-03, DUP-05, CON-04 | Not run |
| 10 | No changes to existing business rules | BUS-13, BUS-14, BUS-15, DESK-01, REG-03 | Not run |
| 11 | Existing test suite remains green | DESK-03…DESK-06, REG-01 | **Baseline measured; post-change not run** |
| 12 | Android application builds successfully | AND-01…AND-05 | Not run |
| 13 | Manual testing on a real device and Windows PC | M-01…M-13 | Not run |

---

## 16. Evidence checklist

For each executed row, attach:

- [ ] commit SHA (RawPOS and Android), build number
- [ ] exact command and environment (OS, interpreter/JDK, device, API)
- [ ] JUnit XML / Gradle result / screenshot / video / CSV
- [ ] pass/fail and failure count
- [ ] tester initials and date
- [ ] for manual rows: device model/serial, Windows build, router, photos

A row without these is **not verified**, regardless of a verbal report.

---

## 17. Sign-off

| Gate | Owner | Date | Result |
|---|---|---|---|
| Architecture approved | Project owner | — | — |
| Phase 1 entry | Architect + QA | — | — |
| Desktop regression baseline | Desktop dev + QA | — | 1,450 passed (measured) |
| Post-integration regression | Desktop dev + QA | — | — |
| Android build | Android dev | — | — |
| Security review | Security reviewer | — | — |
| Manual QA | Owner/QA | — | — |
| Release | Project owner | — | — |
