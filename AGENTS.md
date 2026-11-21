# AGENTS.md — Instructions for AI agents working in this repository

This file is for any coding agent (OpenCode, Claude Code, Codex, or similar) that picks up work in
**RawPOS Mobile Scanner**. Read it before reading code.

## 0. Current phase — read this first

**Phase 1 (Android foundation) is implemented and verified.** The Android project lives under
`android/`.

**Phase 2 (desktop receiver and secure pairing) is complete and merged** into RawPOS `main` at
`7efa178` (2026-11-21). Evidence: `docs/phase-2/`.

**Phase 3 (Android camera and barcode scanning) is complete** as of 2026-11-21. Evidence and closure:
`docs/phase-3/`. The app scans real labels on the POCO X3, and three instrumented tests pass on it.

**Phase 4 (scan protocol and acknowledgment) is not authorized.** A12 (process-lifetime dedupe and
uncertain delivery) is still unsigned.

**Not authorized:** any network call or pairing client, `POST /v1/scans`, `GET /v1/context`,
`CheckoutService.resolve_barcode()`, any `CheckoutView` change, and any change to the sibling RawPOS
repository.

**Do not:**
- modify the sibling RawPOS repository (`..\RawPOS`);
- add Phase 4+ functionality to the project;
- commit, push, tag, fetch, or create/delete branches without explicit authorization;
- claim a test, build, or manual QA passed unless you actually ran it and can quote the evidence;
- add a dependency that is not required by the authorized phase.

Phase 1 evidence is in `docs/phase-1/`; build instructions are in `android/README.md`.

## 1. Read order

1. `PROJECT_CONSTITUTION.md` — binding principles.
2. `README.md` — scope, status, document map.
3. `android/README.md` — toolchain, build, install.
4. `docs/architecture/repository-audit.md` — verified facts about RawPOS.
5. `docs/architecture/system-architecture.md` — the approved architecture.
6. The phase you are working on in `docs/planning/implementation-roadmap.md`.
7. `docs/planning/risks-and-decisions.md` — open decisions and their status.
8. `docs/phase-1/scope-compliance.md` — what Phase 1 did and did not include.

If a document and the code disagree, the code wins for *facts*; the constitution wins for *rules*.
Report the drift instead of silently following either.

## 2. Non-negotiable boundaries

- Android scans and transmits. Desktop owns all business logic.
- Never call `CheckoutService`, a repository, or a Qt widget from a network thread.
- Never inject synthetic `QKeyEvent`s to emulate a scanner.
- Never use `resolve_scan()` for mobile input — it falls back to a product-name search, and even a
  digits-only value can match a name. Use the exact-barcode-only `resolve_barcode()`. The additive
  service change requires approval before it exists.
- Never weaken or delete the existing no-network tests; amend them deliberately and explain why.
- Never change money, pricing, discounts, stock, shifts, invoices, or audit semantics to make a
  transport feature work.
- No database migration in v1.
- No custom cryptography.
- No cloud, telemetry, or internet dependency.

## 3. Verified baseline (as of 2026-11-21)

- RawPOS HEAD: `7efa178` (Phase 2 merge), branch `main`, clean tree.
- Suite after the Phase 2 merge: **1,604 passed in 105.75 s** using global `py -3` (Python 3.14.3).
- Pre-Phase-2 baseline: `797167ac…`, **1,450 passed in 134.97 s**. The 154 additional tests are the
  Phase 2 receiver, pairing, TLS, controller, dialog, and wiring suites.
- The RawPOS project `.venv` does **not** contain pytest; do not assume it can run tests.
- RawPOS barcodes are ASCII digits only, non-empty, unique, with no length or check-digit rule.
- `CheckoutService.resolve_scan()` performs name-search fallback when the barcode is unknown.
- `CheckoutView` owns the keyboard-wedge filter and is recreated when the shell remounts on language
  switch.

Re-verify these before relying on them; record any change in the audit document.

## 4. Where things go

| Kind of work | Location |
|---|---|
| Architecture decisions | `docs/architecture/` |
| Mobile UX and design tokens | `docs/design/` |
| Test strategy and matrix | `docs/quality/` |
| Phases, risks, open decisions | `docs/planning/` |
| Agent review reports (this phase) | `docs/agents/` |
| Phase 1 evidence | `docs/phase-1/` |
| Android source | `android/app/src/` |
| Desktop integration patch (future) | `desktop/` (patch series or documented file list) |

Do not duplicate existing RawPOS business logic anywhere.

## 5. Quality gates

Desktop (run from the RawPOS repository):

```
ruff check .
mypy src
py scripts/check_no_float.py
py -3 -m pytest -q
```

Android (from `android/`):

```
.\gradlew.bat --no-daemon assembleDebug
.\gradlew.bat --no-daemon testDebugUnitTest
.\gradlew.bat --no-daemon lintDebug
.\gradlew.bat --no-daemon connectedDebugAndroidTest   # device/emulator
```

The Phase 1 toolchain paths and environment variables are in `android/README.md` and
`docs/phase-1/environment-report.md`. The connected test uninstalls the app when it finishes.

Protocol (future):

```
py -3 -m pytest tests/protocol -q        # desktop side
./gradlew :protocol:test                  # Android side, if a shared module exists
```

Never report a gate as passing without the command output. If a gate cannot run in the current
environment, say exactly why.

## 6. Documentation rules

- Every architectural claim cites a file, a measurement, or an explicit decision id.
- Distinguish **verified**, **assumed**, and **proposed**.
- When a decision changes, update the ADR/risk entry and the affected documents in the same change.
- Arabic and English must stay in sync; a key added to one catalog must be added to the other.
- Keep documents internally consistent: the protocol in `networking-protocol.md` and the security
  rules in `security-design.md` must describe the same behavior.

## 7. Working with the RawPOS repository

- Treat it as **read-only** unless a phase explicitly authorizes a patch.
- Preserve all user modifications and untracked files.
- Never delete branches, force-push, reset, or clean.
- Run tests read-only; they may write cache directories but must not alter source.
- If a required change touches a business service, stop and escalate with: the exact change, why it is
  necessary, the alternatives, the blast radius, and a rollback plan.

## 8. Definition of Done for any task

A task is done only when:

1. the change matches an approved phase and scope;
2. the relevant automated gates were actually run and reported with evidence;
3. new behavior has a test that fails without the change;
4. documentation and both localization catalogs are updated;
5. the existing USB-scanner, keyboard, and checkout tests still pass;
6. no business rule, money calculation, or schema changed unintentionally;
7. open risks or deviations are recorded, not hidden.

## 9. When to stop

Stop and ask the owner when a task requires any Article XV approval item from the constitution, when
the repository state contradicts the audit, when a "small fix" would touch business logic, or when the
only way to proceed is to guess about shop hardware or business policy.
