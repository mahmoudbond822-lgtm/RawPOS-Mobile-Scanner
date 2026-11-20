# Implementation Roadmap

**Status:** Phase 0 approved; Phase 1 implemented and verified; Phase 2 awaiting authorization.
**Rule:** no phase starts until its dependencies and approvals are satisfied. Each phase ends in a
demonstrable, reversible state.
**Related:** `risks-and-decisions.md` (approval checklist), `../quality/qa-strategy.md`

---

## Phase 0 — Architecture and repository audit

**Status:** complete and approved. Phase 1 was subsequently authorized.
**Duration:** done.

### Objectives

- Establish verified facts about RawPOS at a pinned revision.
- Define the project boundary, architecture, protocol, threat model, design language, and QA plan.
- Identify every approval and unresolved question before any code is written.

### Deliverables

- `docs/architecture/repository-audit.md`
- `docs/architecture/system-architecture.md`
- `docs/architecture/desktop-integration.md`
- `docs/architecture/networking-protocol.md`
- `docs/architecture/security-design.md`
- `docs/architecture/barcode-scanning.md`
- `docs/design/mobile-ux-specification.md`
- `docs/design/mobile-design-system.md`
- `docs/quality/qa-strategy.md`
- `docs/quality/test-matrix.md`
- `docs/quality/performance-requirements.md`
- `docs/planning/implementation-roadmap.md`
- `docs/planning/risks-and-decisions.md`
- `docs/agents/agent-1…6` + `consolidation-notes.md`
- `README.md`, `PROJECT_CONSTITUTION.md`, `AGENTS.md`

### Acceptance

- Every verified claim cites a file, a measurement, or a decision id.
- The measured RawPOS baseline is recorded (1,450 passed / 134.97 s).
- The six independent reports are preserved.
- Conflicts are resolved and recorded.
- No RawPOS file was modified; no dependency installed; nothing committed.

### Risks

- Stale or incomplete facts → audit re-run before Phase 1.
- The shop's hardware/data is unknown → assumptions register.

### Definition of Done

Owner approves the architecture and the approval checklist; Phase 1 is authorized.

---

## Phase 1 — Android project foundation

**Status:** **implemented and verified** (2026-11-20). Evidence: `docs/phase-1/build-evidence.md`,
`docs/phase-1/scope-compliance.md`.
**Duration:** 1–1.5 weeks (one Android engineer).
**Depends on:** Phase 0 approval (A1, A13); Android toolchain + at least one device.

### Objectives

- Create a buildable, testable Kotlin app with **no scanning, networking, or camera logic**.
- Establish navigation, MVVM/StateFlow, manual DI, theme, localization, and test seams.

### Delivered

- Gradle project: `android/settings.gradle.kts`, `android/build.gradle.kts`,
  `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`, wrapper.
- `AndroidManifest.xml` with **no permissions** (no CAMERA, no INTERNET); backup disabled.
- Single `MainActivity`, Compose navigation: Connection, Scanner, Settings, Diagnostics.
- `ui/theme/MobileTokens.kt`, `Theme.kt`; light/dark/system, Material 3 schemes, typography, shapes.
- `values/` + `values-ar/` strings, runtime language switch, RTL, LTR technical values.
- Placeholder screens with final layout and Phase 1 states (no camera, no network).
- Manual `AppContainer` + `AppViewModelFactory`; screen ViewModels with immutable state and `StateFlow`.
- `SettingsRepository` interface + platform `SharedPreferences` implementation.
- JVM tests: settings state, localization parity, token contrast.
- Instrumented Compose smoke test.
- `android/README.md` and `docs/phase-1/*`.

### Deferred to later phases (not missing)

- `ScanEvent`, `ScanStatus`, `BarcodeFormat`, `BarcodeValidator`, `ScanGate` → Phase 3.
- `CredentialStore`/Keystore, TLS client, pairing client → Phase 2/4.
- CameraX/ML Kit and camera permission → Phase 3.
- Internet permission and LAN client → Phase 2/4.
- Bundled Noto Sans Arabic and finalized component previews → design hardening/Phase 3.
- Release signing/R8 → Phase 7/8.

### Files changed

All new under `android/`, plus Phase 1 documentation. No RawPOS files.

### Unit tests (executed)

- Settings ViewModel: initial state, language write-through, theme write-through.
- Localization: Arabic/English key parity, no blank values.
- Theme: light/dark body text ≥ 4.5:1, outlines ≥ 3:1, primary labels ≥ 4.5:1.

### Integration tests (executed)

- `assembleDebug`, `testDebugUnitTest`, `lintDebug`.
- Instrumented smoke: app launches and the Connection screen is present.

### Acceptance criteria — met

- Debug APK builds and installs on an API 36 emulator.
- Every implemented screen renders in both themes and both languages.
- No network or camera code or permission.
- Tests pass with artifacts.

### Risks

- Toolchain/SDK versions drift → pinned and documented.
- Arabic font is currently the system font; bundled metrics deferred.
- Only API 36 emulator tested; physical devices/minSdk 26+ remain Phase 3/6 work.

### Definition of Done — met

Buildable app, design tokens applied, navigation and language/theme flow working, tests green with
artifacts, no business logic present.

---

## Phase 2 — Desktop receiver and secure pairing

**Duration:** 2 weeks (one desktop engineer).
**Depends on:** A3 (listener), A4 (QR dependency), A5 (pairing flow), A6 (DPAPI), A7 (port/firewall),
A8 (server hardening path).

### Objectives

- Run an authenticated, pinned TLS listener on the LAN.
- Pair a phone with a QR, a one-time secret, desktop approval, and a 6-digit code.
- Persist a device registry with token hashes only.
- Expose no business operation yet.

### Deliverables

- `src/rawpos/mobile/protocol.py` — constants, enums, strict validation, error mapping.
- `src/rawpos/mobile/pairing.py` — session state machine, TTL, one use, SAS derivation.
- `src/rawpos/mobile/devices.py` — registry (atomic write, hashes, revoke, last seen).
- `src/rawpos/mobile/tls.py` — ECDSA P-256 cert, SANs, SPKI pin, DPAPI/ACL key storage.
- `src/rawpos/mobile/server.py` — `ThreadingHTTPServer` + TLS, routes, limits, rate limiter,
  `GET /v1/health`, `POST /v1/pair`.
- `src/rawpos/mobile/controller.py` — GUI-thread controller skeleton (no checkout yet).
- `src/rawpos/ui/mobile_scanner_dialog.py` — QR, countdown, approve/deny, device list, revoke.
- `app.py`/`app_shell.py` wiring: explicit enable, stop on sign-out/recovery/shutdown.
- `pyproject.toml`: `segno` pinned (approved).
- Desktop keys in both catalogs.

### Tests

- Unit: secret comparison, expiry, one-use, attempt conflict, SAS vector, token hashing, registry
  atomicity/corruption, rate limiter, strict JSON.
- Integration: loopback TLS handshake with a generated cert; pair via a Python client; pin mismatch;
  expired/used secret; revoke; no data routes.
- GUI: dialog states, countdown, approve/deny, revoke, light/dark, RTL, focus return.
- Failure: corrupt registry, DPAPI failure, disk full, port in use.
- Static: route inventory, no business imports, no outbound network.

### Acceptance criteria

- A Python test client can pair over TLS with pin verification and receive a token.
- A wrong pin, expired secret, reused secret, or denied approval fails closed.
- Registry contains only hashes; logs contain no secrets.
- Receiver is off by default; enabling and disabling are explicit.
- `GET /v1/health` returns only version/status/instance id.
- RawPOS suite remains green.

### Risks

- TLS/SAN/pin complexity → certificate fixture matrix.
- `http.server` hardening → fuzz early, `waitress` contingency.
- QR dependency packaging (Nuitka) → test the frozen build.
- Firewall confusion → diagnostics + exact endpoint display.

### Definition of Done

Pairing works end-to-end with a test client; security tests pass; no checkout code touched; docs and
catalogs updated; evidence recorded.

---

## Phase 3 — Android camera and barcode scanning

**Duration:** 2 weeks (Android engineer).
**Depends on:** Phase 1; a real device; format decision (A11).

### Objectives

- Live CameraX preview and bundled ML Kit decoding.
- Digit-only validation, format allowlist, duplicate suppression, feedback.
- A fake transport so scanning is fully testable without the desktop.

### Deliverables

- CameraX `Preview` + `ImageAnalysis` (720p, YUV, KEEP_ONLY_LATEST, ~10 fps, manual analyzer, 2-frame stability).
- ML Kit bundled barcode scanning; format mapping; rotation.
- `core/barcode/ScanGate.kt` — stability, cooldown, re-arm, one-in-flight.
- `core/barcode/Validation.kt` — full predicate and rejection reasons.
- Scanner screen: viewport, target, torch, pause, zoom, result card, states.
- Feedback: sound, haptics, reduced motion.
- `FakeTransport` + `FakeClock` test doubles.
- Instrumented camera smoke with fixture images.

### Tests

- JVM: validator table; ScanGate state machine (60 frames → one event; re-arm; two codes in frame);
  backoff; status mapping.
- Instrumented: permission flow, camera bind/unbind, rotation, torch, pause, process death.
- Manual: real labels in three light levels; latency capture.

### Acceptance criteria

- One physical label produces one event while held; a re-presented label after re-arm produces a second.
- Non-digit/alphanumeric/QR payloads are rejected with the right message.
- Leading zeros preserved.
- No network call when offline (fake transport records zero).
- Camera releases on background/pause.

### Risks

- Device camera variance → device matrix and tuning.
- ML Kit version behavior → pin and golden labels.
- Battery/thermal → 10 fps and camera stop.

### Definition of Done

Scanning works on the test device with fake transport; tests pass; no product data on the phone;
performance baseline recorded.

---

## Phase 4 — Communication and acknowledgment protocol

**Duration:** 1.5 weeks (Android + desktop).
**Depends on:** Phase 2 (server) and Phase 3 (scanner).

### Objectives

- Implement the v1 protocol end-to-end with the truthful status ladder.
- Idempotency, retries, context, uncertain delivery.
- Contract fixtures shared by both sides.

### Deliverables

- Android `core/net/ScanClient.kt`: OkHttp, pinning, timeouts, retry, event persistence.
- Android `core/net/ContextClient.kt`: `GET /v1/context`.
- Desktop `POST /v1/scans`, `GET /v1/context`, dedupe store, context derivation.
- Shared `protocol/fixtures/*.json` + SHA-256 manifest.
- Status rendering on the phone; no optimistic success.
- `server_instance_id` handling; "delivery uncertain" path.

### Tests

- Contract: every request/response fixture parsed/serialized on both sides.
- Desktop: idempotency reserve-before-dispatch, concurrent same id, conflict, TTL/capacity, context
  mismatch, instance id, rate limit.
- Android: retry policy, same event id, no new id on retry, context refresh, uncertain state.
- End-to-end loopback: phone fake client → receiver → fake dispatcher.

### Acceptance criteria

- A lost response followed by a retry produces one dispatch and a cached outcome.
- A finalize between capture and send yields `rejected_stale_context` and no line.
- A desktop restart changes `server_instance_id`; the phone does not auto-replay.
- The phone never shows "added" for `processing`/`unknown`.
- No business data in any response.

### Risks

- Clock/ordering assumptions → server instance + context.
- Retry storms → one in-flight + backoff + rate limit.
- Fixture drift → manifest check in both builds.

### Definition of Done

Both sides implement v1 and pass the contract suite; an end-to-end harness demonstrates add/not
found/reject/duplicate/uncertain; evidence recorded.

---

## Phase 5 — RawPOS integration adapter

**Duration:** 1.5 weeks (desktop engineer).
**Depends on:** **A2 approval** (`resolve_barcode`); Phase 4.

### Objectives

- Dispatch authenticated scans into the live checkout on the GUI thread.
- Preserve USB/keyboard behavior exactly.
- Enforce the mobile admission policy.

### Deliverables

- `CheckoutService.resolve_barcode()` (additive; exact-only) + tests.
- `CheckoutView.submit_scan()` + `_render_scan_result()` refactor.
- `MobileScanController` dispatch: live-view provider, critical-op bracket, deadline/cancel, outcome
  mapping, context.
- Admission: active Sales + full mode + open shift + no transition.
- `CheckoutView` status chip/button and desktop status-bar entry.
- No synthetic key events; import-architecture test.

### Tests

- Unit: `resolve_barcode` exact-only; name collision (`"Pack 12"` vs `12`) returns not found;
  `resolve_scan` unchanged (existing name-search test).
- GUI: added/merged/not found/inactive; focus in tender/quantity; modal open; language remount;
  admission refusals; off-thread call refused.
- Integration: real service + DB; hold/resume/finalize context changes; finalize after mobile adds.
- Failure: restore mid-scan, shutdown mid-scan, slow GUI timeout.

### Acceptance criteria

- A mobile scan adds exactly one line and renders the existing status.
- USB and keyboard-only checkout are unchanged (wedge tests pass unmodified).
- No business call from a non-GUI thread.
- A stale context never mutates the new invoice.
- Read-only/no-shift scans are refused with clear statuses.
- The full RawPOS suite is green.

### Risks

- Widget refactor regression → wedge tests gate.
- Context derivation edge cases → dedicated tests.
- Admission policy dispute → recorded decision.

### Definition of Done

End-to-end scan reaches the cart; parity tests pass; regression suite green; no schema or business rule
change; evidence recorded.

---

## Phase 6 — Integration and regression testing

**Duration:** 2 weeks (QA + both engineers).
**Depends on:** Phase 5.

### Objectives

- Execute the full matrix on real hardware.
- Prove non-regression and the acceptance criteria.
- Measure performance.

### Deliverables

- Executed test matrix with artifacts.
- Manual scripts M-01…M-13 completed.
- Performance report (proposed vs measured).
- Defect log and fixes.
- Updated docs for any behavior discovered.

### Tests

- All automated layers; security suite; failure injection; performance benchmarks.
- USB/keyboard regression; Arabic/English; light/dark; low battery; thermal; calls; Wi-Fi changes.

### Acceptance criteria

- All 13 acceptance criteria have PASS evidence or a written waiver.
- Existing suite green; schema unchanged; no new dependency beyond approved.
- No Sev1/Sev2 open.

### Risks

- Hardware availability → schedule early.
- Flaky tests → quarantine with owner/expiry.
- Performance misses → tune thresholds/levers.

### Definition of Done

Signed test report with artifacts; known issues documented; release candidate identified.

---

## Phase 7 — Security hardening and performance optimization

**Duration:** 1.5 weeks.
**Depends on:** Phase 6.

### Objectives

- Fuzz the receiver; close gaps.
- Verify TLS/pairing/revocation under adversarial tests.
- Tune latency, battery, memory.

### Deliverables

- Fuzz corpus + results; fix list.
- Security test report (threat model coverage).
- Performance tuning decisions (fps, resolution, ROI) with measurements.
- Hardened server or `waitress` decision.
- Log-redaction audit.

### Acceptance criteria

- No unresolved High/Critical security finding.
- Parser survives the fuzz corpus without crash/hang.
- Performance targets met or waived.
- No secret/barcode in logs.

### Risks

- Fuzzing finds architectural issues → may require a server swap (planned contingency).
- Tuning trades accuracy/battery → document.

### Definition of Done

Security and performance reports signed; release candidate hardened.

---

## Phase 8 — Release preparation and installation documentation

**Duration:** 1 week.
**Depends on:** Phase 7.

### Objectives

- Produce installable artifacts and shop-ready instructions.
- Train/support the first install.

### Deliverables

- Signed Android APK/AAB; versioning policy; signing key custody.
- RawPOS build with the mobile module (Nuitka), tested on a clean Windows VM.
- `docs/install/mobile-scanner-install.md`: prerequisites, firewall rule, pairing, revoke,
  troubleshooting, uninstall.
- Shop checklist and a one-page quick reference (Arabic + English).
- Release notes and known limitations.
- Rollback plan (disable receiver, revoke devices, uninstall app).

### Acceptance criteria

- A non-developer can install and pair following the document.
- Clean-VM install works; firewall guidance is accurate.
- Rollback is verified.
- All release gates pass.

### Risks

- Firewall/AV interference → test on a clean VM and a real shop PC.
- Signing key loss → custody procedure.
- Shop network quirks → documented unsupported cases.

### Definition of Done

Release artifacts + docs published to the owner; first shop install supported; no open release blocker.

---

## Cross-phase workstreams

| Workstream | Phases | Owner |
|---|---|---|
| Documentation and ADR updates | 1–8 | Architect |
| Localization parity (ar/en) | 1, 2, 5, 8 | UX + devs |
| Threat model review | 2, 4, 7 | Security reviewer |
| Performance measurement | 3, 4, 6, 7 | QA |
| Regression suite | 5, 6, 8 | QA |
| Shop hardware/data audit | 0, 3, 6 | Owner + Barcode |

---

## Complexity estimate

Assumptions: one senior Android engineer, one senior Python/PyQt engineer, part-time QA, access to one
budget and one mid-range Android device, one Windows POS test machine, and a simple flat LAN.

| Phase | Effort (person-weeks) | Notes |
|---|---:|---|
| 0 | done | Architecture |
| 1 | 1–1.5 | Android foundation |
| 2 | 2 | Receiver, TLS, pairing, registry, dialog |
| 3 | 2 | Camera, ML Kit, dedupe, UX |
| 4 | 1.5 | Protocol both sides, retries, context |
| 5 | 1.5 | Checkout seam, admission, parity |
| 6 | 2 | Matrix, manual, performance |
| 7 | 1.5 | Fuzz, security, tuning |
| 8 | 1 | Release, docs |
| **Total** | **~12–13 person-weeks** | sequential |

With Android and desktop in parallel (Phases 1/3 vs 2/5), elapsed time is roughly **8–9 weeks** plus
shop-hardware delays. Add 20–30% contingency for device/firmware/network surprises. This is an
estimate, not a commitment; it excludes shop data cleanup and any business-rule change.

**Largest unknowns:** real camera/ML Kit behavior, the shop's barcode data (UPC-A representation), and
Windows firewall/network quirks. Each can add a week.

---

## Independently testable milestones

1. **M1 (end of Phase 1):** Android app builds and navigates; no network.
2. **M2 (end of Phase 2):** Python client pairs over TLS; no checkout.
3. **M3 (end of Phase 3):** phone scans with fake transport; no desktop.
4. **M4 (end of Phase 4):** loopback end-to-end add/not-found/duplicate/uncertain.
5. **M5 (end of Phase 5):** real checkout gets a scan; USB/keyboard unchanged.
6. **M6 (end of Phase 6):** full matrix + manual QA.
7. **M7 (end of Phase 7):** security/perf reports.
8. **M8 (end of Phase 8):** shop install package.

Each milestone is demonstrable without the next phase.
