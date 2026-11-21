# RawPOS Mobile Scanner

**Version:** 1.0 — architecture, specification, Phase 1 Android foundation, Phase 2 approved
**Status:** Phase 1 implemented and verified. Phase 2 **complete and merged 2026-11-21**. Phase 3
(Android camera and barcode scanning) **authorized 2026-11-21, not started**.
**Companion to:** RawPOS Desktop (PyQt6 + SQLCipher, Windows)

RawPOS Mobile Scanner turns an Android smartphone into a wireless barcode scanner for RawPOS Desktop.
The phone scans; RawPOS does everything else.

```
 Phone: CameraX → ML Kit → validator → authenticated LAN client
                                                    │
                                      TLS + pinned certificate
                                                    │
 Desktop: receiver → authenticated event → GUI-thread bridge
                                                    │
                          existing CheckoutView → existing CheckoutService
                                                    │
                                   existing cart, pricing, discounts, payments
```

The phone never sees a product, a price, a total, or a stock level. It sends a barcode and receives a
truthful outcome: added, merged, not found, inactive, or rejected.

---

## What this repository contains

The approved architecture and specification, plus the **Phase 1 Android foundation**:

```
README.md                     this file
PROJECT_CONSTITUTION.md       binding principles and change control
AGENTS.md                     instructions for future coding agents
android/                      Phase 1 Kotlin/Compose project
  README.md                   toolchain, build, install, emulator
docs/
  architecture/
    repository-audit.md       verified facts about the RawPOS codebase
    system-architecture.md    consolidated system architecture
    android-architecture.md   Android technical specification
    desktop-integration.md    exact integration point and thread model
    networking-protocol.md    v1 protocol, pairing, idempotency
    security-design.md        threat model and security architecture
    barcode-scanning.md       formats, validation, duplicate suppression
  design/
    mobile-ux-specification.md  screens, states, interactions, copy
    mobile-design-system.md     tokens, typography, components, themes
  quality/
    qa-strategy.md            test levels, environments, evidence rules
    test-matrix.md            traceable matrix of cases
    performance-requirements.md proposed targets and measurement methods
  planning/
    implementation-roadmap.md phased plan with files, tests, acceptance
    risks-and-decisions.md    ADR log, open questions, approval register
    definition-of-done.md     release and phase gates, evidence rules
  phase-1/
    environment-report.md     provisioned JDK/Gradle/SDK/emulator
    dependency-inventory.md   exact dependencies and why
    build-evidence.md         build, test, lint, install evidence
    scope-compliance.md       approved scope vs delivered
    evidence/                 six final screenshots; local-only dumps ignored
  agents/
    agent-1-architecture.md   independent review reports
    agent-2-android.md
    agent-3-barcode.md
    agent-4-networking-security.md
    agent-5-uiux.md
    agent-6-qa.md
    consolidation-notes.md    conflicts found and how they were resolved
```

---

## Implementation status

**Phase 1 — Android foundation — complete.**

- Kotlin 2.2.21, Compose/Material 3, single Activity, Navigation, MVVM/StateFlow, manual `AppContainer`.
- minSdk 26, target/compile SDK 36; no Camera or Network permission.
- Light/dark/system theme from RawPOS-derived semantic tokens.
- Arabic/English resources, runtime language switch, RTL, LTR technical values.
- 10 JVM tests + 1 instrumented smoke test; lint 0 errors / 14 version-notice warnings.
- Debug APK installs and launches on an API 36 emulator.

Evidence: `docs/phase-1/build-evidence.md` and `docs/phase-1/scope-compliance.md`.
Build instructions: `android/README.md`.

**Phase 2 — Desktop receiver and secure pairing — authorized 2026-11-20, not started.** Scope is fixed
by `docs/planning/approval-packet-phase-2.md`: TLS listener, QR pairing with desktop approval, device
registry, and the GUI-thread controller skeleton. No business operation, no checkout integration.

**Not implemented and not authorized:** CameraX, ML Kit, barcode scanning, the scan protocol
(`POST /v1/scans`), `resolve_barcode()`, RawPOS checkout integration, release APK. The desktop receiver
and QR pairing belong to the authorized Phase 2 and are still absent.

---

## The one-minute version

**Scope of v1**

- Android 8+ (API 26+) phone, camera, QR pairing, continuous scanning.
- Numeric retail barcodes (EAN-13/8, UPC-A/E, ITF, numeric Code 128) by default; alphanumeric and QR
  formats disabled unless explicitly enabled, because RawPOS stores barcodes as ASCII digits only.
- HTTPS over the local network with a certificate pinned from the pairing QR. No cloud, no internet.
- Desktop receiver, pairing dialog, device registry, and a narrow GUI-thread bridge into the existing
  checkout screen.
- USB scanners and keyboard-only checkout remain untouched.

**Explicitly out of scope for v1**

Product lookup on the phone, prices, cart, payments, invoices, inventory, customer data, offline cart,
cloud relay, multi-store, Windows service, iOS, Bluetooth/USB-tethered scanning, receipt printing.

**What must not change in RawPOS**

Money handling, pricing, discounts, stock rules, shift rules, invoice finalization, audit semantics,
database schema, the keyboard-wedge heuristic, focus restoration, Arabic/English localization.

---

## Measured baseline

Read-only audit of RawPOS `main` at `797167acbcb14d96b476891a573a7e31638acf12`:

- Working tree clean; 215 test files.
- `py -3 -m pytest -q` → **1,450 passed in 134.97 s** (Python 3.14.3, global interpreter).
- The RawPOS project `.venv` does not contain pytest; it cannot run the suite as-is.
- No network code exists today; two tests actively forbid sockets in core flows.
- RawPOS barcodes are ASCII digits only, unique, with no length or check-digit rule.
- `CheckoutService.resolve_scan()` falls back to a **name search** for unknown barcodes.

These facts shape the architecture. See `docs/architecture/repository-audit.md`.

---

## Design decisions at a glance

All decisions are proposals until the owner approves them.

| # | Decision | Rationale |
|---|---|---|
| D1 | HTTP/1.1 + JSON over TLS, request/response with per-event acknowledgment | Simple, testable, matches scanner semantics; WebSocket adds state without v1 benefit |
| D2 | Desktop is the server, phone is the client | Desktop owns identity, discovery surface, and authority |
| D3 | QR carries host, port, one-time pairing secret, and certificate pin | Bootstraps trust without a CA or internet |
| D4 | Self-signed certificate generated locally; pin the SPKI hash | No CA on a LAN; no custom crypto |
| D5 | Device token: 256-bit random, stored as SHA-256 hash on desktop, Keystore-protected on phone | Revocable, not recoverable from disk |
| D6 | Receiver **off by default**, LAN-only, private-firewall rule | Minimizes attack surface and preserves the offline guarantee |
| D7 | Network thread never touches checkout; a queued Qt signal hands off to the GUI thread | The service and widgets are not thread-safe |
| D8 | One additive `CheckoutView.submit_scan()` reuses existing result rendering and focus rules | Maximum fidelity, minimum blast radius |
| D9 | Route mobile scans through an exact-barcode-only desktop path, and reject non-digits before transmission | Digit filtering alone is insufficient: a numeric value can still match a product name via `resolve_scan`'s search fallback. Requires an additive, approval-gated `CheckoutService.resolve_barcode()` |
| D10 | Three-layer duplicate suppression; desktop idempotency is authoritative | One physical scan must never add two units |
| D11 | No database migration; device registry is a separate JSON store with hashes only | Avoids coupling transport to business data |
| D12 | Phone shows success only after the desktop confirms the cart mutation | Transport acknowledgment is not a sale |
| D13 | Mobile is admitted only when Sales is active, the license is full mode, and a shift is open | Prevents "added" when the desktop cannot display or complete the sale; USB/keyboard unchanged |

The detailed ADR log, alternatives, and approval status live in
`docs/planning/risks-and-decisions.md`.

---

## How to review this proposal

1. Read `PROJECT_CONSTITUTION.md` — if any principle is wrong, stop there.
2. Read `docs/architecture/repository-audit.md` — check the facts against the real repo.
3. Read `docs/architecture/desktop-integration.md` — this is the highest-risk area.
4. Read `docs/architecture/networking-protocol.md` and `security-design.md` — challenge the threat
   model and the pairing flow.
5. Read `docs/design/mobile-ux-specification.md` — confirm the retail workflow and the Arabic copy.
6. Read `docs/planning/implementation-roadmap.md` — confirm phase order and the files that will change.
7. Answer the open questions in `docs/planning/risks-and-decisions.md`.

Nothing is implemented until those answers are recorded and the owner approves Phase 1.

---

## Proposed acceptance criteria

The product is acceptable only when all of the following are demonstrated with evidence:

1. Android pairs with RawPOS via QR.
2. Android scans each enabled barcode format.
3. RawPOS receives authenticated scan events only.
4. The existing checkout pipeline processes those events.
5. The USB scanner still works exactly as before.
6. Keyboard-only checkout still works.
7. Human typing is never misclassified as a barcode.
8. Duplicate camera frames never duplicate a cart line.
9. Network failures are handled safely and visibly.
10. No business rule changes.
11. The existing RawPOS test suite remains green.
12. The Android application builds successfully.
13. Manual testing is completed on a real Android device and a real Windows computer.

The traceable version, with test IDs, is in `docs/quality/test-matrix.md`.

---

## Licensing and ownership

This is a separate companion project. It does not relicense RawPOS. No RawPOS source file is modified
in this phase. Any future desktop patch is proposed, reviewed, and applied under the change control in
`PROJECT_CONSTITUTION.md`.
