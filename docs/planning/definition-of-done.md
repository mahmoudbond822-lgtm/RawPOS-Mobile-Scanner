# Definition of Done

**Status:** proposed
**Applies to:** v1.0 release and each phase gate
**Related:** `implementation-roadmap.md`, `../quality/test-matrix.md`,
`../quality/qa-strategy.md`

---

## 1. Release Definition of Done

v1.0 is done only when **all** of the following are true and evidenced. A verbal report is not
evidence.

### 1.1 Functional

- [ ] Android pairs with RawPOS using the QR flow, with desktop approval and a matching 6-digit code.
- [ ] Android scans EAN-13, EAN-8, UPC-A, and numeric Code 128 (and any shop-approved extras).
- [ ] RawPOS accepts only authenticated, correctly framed scan events.
- [ ] The existing checkout pipeline adds/merges the line and shows the existing status.
- [ ] The phone shows `added` only after the desktop confirms the cart mutation.
- [ ] Not found, inactive, rejected, duplicate, and uncertain outcomes are distinct and truthful.
- [ ] Duplicate camera frames and retries never duplicate a cart line.
- [ ] A delayed scan never lands in a different invoice.
- [ ] Manual "scan again" and continuous re-arm both work.

### 1.2 Non-regression

- [ ] USB scanner behavior is unchanged; the wedge tests pass unmodified.
- [ ] Keyboard-only checkout works end to end.
- [ ] Human typing is never misclassified as a barcode.
- [ ] Focus restoration in the tender/quantity fields is unchanged.
- [ ] Product activation/deactivation, inventory validation, discounts, payments, invoice finalization,
      shifts, audit, and Arabic/English behavior are unchanged.
- [ ] The RawPOS test suite is green with the exact command and interpreter recorded.
- [ ] No database migration or schema change (`sqlite_master` identical).

### 1.3 Security

- [ ] No unauthenticated scan submission is possible.
- [ ] TLS pinning is enforced; a mismatch fails closed with no trust-anyway path.
- [ ] Device tokens are hashed at rest on the desktop and Keystore-protected on the phone.
- [ ] Pairing expires, is one-use, and can be denied; devices can be revoked immediately.
- [ ] Replay/duplicate protection is demonstrated.
- [ ] The receiver is off by default and binds only to a private interface.
- [ ] No token, pairing secret, private key, or barcode appears in logs.
- [ ] The parser survives the fuzz corpus (or `waitress` is adopted).
- [ ] No High/Critical finding remains open.

### 1.4 Performance

- [ ] Every target in `performance-requirements.md` is met or waived in writing.
- [ ] Results are recorded with device, network, sample size, p50/p95, and date.
- [ ] No ANR, crash, or camera error in a 30-minute session.
- [ ] Battery/memory are within target on the budget device.

### 1.5 Build and distribution

- [ ] Android release build succeeds with R8, installs, and runs on a real device.
- [ ] The RawPOS Nuitka build includes the mobile module and runs on a clean Windows VM.
- [ ] Signing keys are outside the repo, with documented custody.
- [ ] Version numbers are monotonic and match the release notes.
- [ ] No unapproved dependency is present.

### 1.6 Documentation and support

- [ ] Installation guide covers prerequisites, firewall, pairing, revoke, troubleshooting, and uninstall.
- [ ] Arabic and English quick reference exists.
- [ ] Known limitations and unsupported networks are documented.
- [ ] Rollback (disable receiver, revoke devices, uninstall) is verified.
- [ ] The owner has run the install once with the document.

### 1.7 Evidence

- [ ] JUnit XML / Gradle results for every automated layer.
- [ ] Signed manual checklists M-01…M-13 with device serials, builds, photos, and initials.
- [ ] Performance CSV/report.
- [ ] Security/fuzz report.
- [ ] Final test matrix with PASS/FAIL/waiver per row.

---

## 2. Phase gates

| Phase | Gate | Blocking evidence |
|---|---|---|
| 0 | Architecture approved | Signed approval checklist; this document set |
| 1 | Android foundation | **Met 2026-11-20:** `assembleDebug`, `lintDebug`, 10 unit tests, 1 instrumented test, install/launch on API 36 emulator; evidence in `../phase-1/build-evidence.md` |
| 2 | Receiver + pairing | Loopback pairing test, TLS matrix, registry/log scan, GUI tests |
| 3 | Camera + scanning | JVM dedupe/validator tests, device camera smoke, latency baseline |
| 4 | Protocol | Contract fixtures both sides, retry/idempotency/context tests |
| 5 | Checkout integration | Wedge regression, parity, thread-affinity, admission, full suite |
| 6 | Integration + regression | Full matrix, M-01…M-13, performance report |
| 7 | Security + performance | Fuzz report, threat-model coverage, tuned benchmarks |
| 8 | Release | Clean-VM install, shop install, rollback, signed artifacts |

No phase starts before its approvals in `risks-and-decisions.md` §6.

---

## 3. Definition of "tested"

A test is "tested" only when:

1. the command is recorded;
2. the environment (OS, interpreter/JDK, device, API) is recorded;
3. the result (pass/fail/count) is recorded;
4. the artifact (XML, screenshot, video, CSV) exists;
5. the commit/build SHA is recorded;
6. a human has looked at failures, not just the summary line.

A manual test additionally records the device serial, Windows build, router/network, date, and tester.

---

## 4. What v1 explicitly does not require

- Product names, prices, stock, or cart contents on the phone.
- Offline queueing or exactly-once across a desktop crash.
- Multiple simultaneous scanners per desktop.
- iOS, Bluetooth HID, USB tethering, or NFC.
- Cloud relay, remote support, telemetry, or analytics.
- A Windows service or headless receiver.
- A database migration.
- A Play Store release (side-load is acceptable for v1).
- Pixel-perfect screenshot testing.

Each is a deliberate exclusion, not an unfinished item.

---

## 5. Sign-off

| Role | Name | Scope | Date | Signature |
|---|---|---|---|---|
| Project owner | | Scope, admissions, release | | |
| Principal architect | | Architecture, integration | | |
| Desktop engineer | | Receiver, checkout seam | | |
| Android engineer | | App, camera, transport | | |
| QA | | Matrix, evidence | | |
| Security reviewer | | Threat model, fuzz, crypto use | | |
| Arabic reviewer | | Copy, RTL | | |

Release is blocked until every row is signed or a written waiver is attached.
