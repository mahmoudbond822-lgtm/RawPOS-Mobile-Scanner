# Phase 2 — Scope Compliance Report

**Authorization:** Phase 2 (desktop receiver and secure pairing), owner-approved 2026-11-20 —
A5, A6, A7, A8, A14, A16, A17, and the phase authorization line in
`../planning/approval-packet-phase-2.md` §9.
**Branch:** `feature/mobile-receiver` in RawPOS, from `797167acbcb14d96b476891a573a7e31638acf12`.
**Result:** compliant on scope. No checkout integration, no scan route, no schema change, no business
rule change. Merged to `main` on 2026-11-21 — see §10.

---

## 1. Approved scope vs delivered

| Approved deliverable (`implementation-roadmap.md` Phase 2) | Delivered | Evidence |
|---|---|---|
| `src/rawpos/mobile/protocol.py` — constants, enums, strict validation, error mapping | `protocol.py`, 321 lines | `tests/unit/test_mobile_protocol.py` |
| `src/rawpos/mobile/pairing.py` — state machine, TTL, one use, SAS | `pairing.py`, 295 lines | `tests/unit/test_mobile_pairing.py` |
| `src/rawpos/mobile/devices.py` — registry, atomic write, hashes, revoke | `devices.py`, 328 lines | `tests/unit/test_mobile_devices.py` |
| `src/rawpos/mobile/tls.py` — ECDSA P-256, SANs, SPKI pin, DPAPI/ACL storage | `tls.py`, 497 lines | `tests/unit/test_mobile_tls.py` |
| `src/rawpos/mobile/server.py` — TLS listener, routes, limits, rate limiter, health, pair | `server.py`, 440 lines | `tests/integration/test_mobile_server.py` |
| `src/rawpos/mobile/controller.py` — controller (roadmap: "skeleton") | Delivered as a real lifecycle owner, still with no checkout | `tests/integration/test_mobile_controller.py` |
| `src/rawpos/ui/mobile_scanner_dialog.py` — QR, countdown, approve/deny, device list, revoke | `mobile_scanner_dialog.py`, 275 lines | `tests/gui/test_mobile_scanner_dialog.py` |
| `app.py` / `app_shell.py` wiring — explicit enable, stop on sign-out/recovery/shutdown | Menu action, lazy controller, three stop paths | `tests/gui/test_mobile_shell_wiring.py`, shutdown assertion |
| `pyproject.toml`: `segno` pinned | `segno==1.6.6` | `pyproject.toml` |
| Desktop keys in both catalogs | 30 `mobile.*` keys in `ar` and `en` | `localization/__init__.py` |

Two additions were needed to satisfy approved decisions rather than to expand scope:

- `RegistryCodec` in `devices.py` + `DpapiCodec`/`PlainCodec` in `tls.py`, so the registry is protected
  at rest exactly as A6 requires.
- `build_pairing_uri` in `protocol.py`, the QR payload from §4.2.

---

## 2. Exclusions — verified

| Exclusion | Verification | Result |
|---|---|---|
| No scan route (`POST /v1/scans`, `GET /v1/context`) | `grep '/v1/scans' server.py` → **0** matches; unknown paths return 404 | Pass |
| No checkout integration | No `rawpos.services.checkout`, repository, or widget import in `src/rawpos/mobile/`; the 5 textual hits are docstrings stating the absence | Pass |
| No `resolve_barcode()` change | `git diff main..HEAD -- src/rawpos/services` → empty | Pass |
| No synthetic key events | `grep 'QKeyEvent|QTest.key'` over the mobile package and dialog → **0** | Pass |
| No database migration | `git diff --name-only main..HEAD -- src/rawpos/migrations` → empty | Pass |
| No business rule, money, stock, or shift change | Diff touches only `mobile/`, one dialog, shell/app wiring, catalogs, `pyproject.toml` | Pass |
| No camera or barcode code | No camera/MlKit/scan-symbol references in the new code | Pass |
| RawPOS `main` untouched | `git rev-parse origin/main` → `797167a` | Pass |
| No release artifact | No APK/AAB/installer produced | Pass |

---

## 3. Gates — measured on this machine

| Gate | Command | Result |
|---|---|---|
| Lint | `py -3 -m ruff check .` | `All checks passed!` |
| Types | `py -3 -m mypy src` | `Success: no issues found in 157 source files` |
| Money-path guard | `py -3 scripts/check_no_float.py` | `no-float gate: PASS` |
| Full suite | `py -3 -m pytest -q` | **1604 passed in 104.47s** |
| Mobile tests only | `--collect-only` over the 8 new/updated test files | **150 tests** |

Baseline before Phase 2 code, measured at the start of increment 1: **1450 passed in 85.89s**.
1604 − 1450 = **154 new tests**; no existing test was deleted or weakened.

---

## 4. Deviations and judgment calls

1. **T072 (packaging guard) amended.** The FR-011 guard forbids `private_bytes` and
   `generate_private_key` anywhere in `src/rawpos/`, which the approved TLS design requires. Owner
   approved a narrow exemption on 2026-11-21: pinned to `mobile/tls.py` and to those two labels, with
   two tests holding the pin and a mutation probe confirming the guard still catches `derive_ddk` in
   another module. Not a weakening; the licence-signing labels remain enforced everywhere.
2. **`segno` installed globally and pinned.** A4 approved the dependency; installing it changed the
   interpreter that runs the suite. Recorded because the environment, not just the repository, changed.
3. **`DeviceAlreadyPaired` maps to `409 pairing_used`.** Semantically imprecise. Unreachable through the
   dialog, which owns the Replace choice. Deferred to Phase 4 contract fixtures rather than inventing a
   protocol code here, which would be an Article XV change.
4. **`controller.py` is more than the "skeleton" the roadmap named.** It owns enable/disable and the
   owner check. Justified: A14 needs one enforcement point, and the dialog must not be the boundary.
5. **`desktop_id` lives in `mobile/desktop_id`, not `config.json`.** It is not a secret, and this keeps
   `ConfigStore` unchanged and the mobile directory self-contained.
6. **Desktop display name is the machine hostname.** The protocol allows any name; this avoids a new
   settings field.
7. **Two existing tests were edited.** `test_main_window.py` gained two tests; `test_app_shutdown.py`'s
   shell stub gained `shutdown_mobile` plus an assertion that it is called. Neither change relaxes an
   assertion.
8. **Pairing rate limiting is per window** (3 attempts/minute), while per-IP limiting is the generic
   30 requests/minute. A caller who can reopen a window resets the pairing budget; opening a window
   requires the owner, so this is acceptable for Phase 2 and must be revisited in Phase 7.

---

## 5. Definition of Done — roadmap Phase 2

| Criterion | Status |
|---|---|
| Pairing works end-to-end with a test client | **Met** — a Python HTTPS client pairs over loopback TLS and receives a token |
| Security tests pass | **Met** — expiry, one-use, attempt conflict, constant-time comparison, pin stability, fail-closed corruption |
| No checkout code touched | **Met** — verified by diff and grep |
| Docs and both catalogs updated | **Met** — `mobile.*` in `ar` and `en`; evidence in this directory |
| Evidence recorded | **Met** — `build-evidence.md` and this report |

---

## 6. What is *not* proven

Stated plainly so the phase is not credited with more than it earned:

- **No real phone has connected.** There is no Android client yet; the phone half is Phase 3/4.
- **Phone-side pin verification and the SAS derivation are unverified.** The desktop half is pinned by
  the vector `363186`; the Android implementation must reproduce it.
- **Windows Firewall behaviour on a shop PC is untested.** A7 assumed the shop can open one inbound
  port on the Private profile (AS4), and no client-isolation network exists to test against (AS2).
- **The frozen (Nuitka) build has not been produced with `segno`.** Packaging is Phase 8.
- **Dialog appearance is only partly covered.** Approve/deny/revoke and state transitions are tested;
  light/dark rendering, countdown decrement over time, and focus return are not.
- **Performance is unmeasured** (Phase 6).

---

## 7. Open items carried forward

1. Android SAS vector and pin verification (Phase 3/4).
2. `device_already_paired` typed code (Phase 4 contract fixtures).
3. Arabic review of the 30 new keys by a native speaker (Q22).
4. ~~Merge of `feature/mobile-receiver` into RawPOS `main`~~ — authorized, performed 2026-11-21
   (merge commit `7efa178`). See §10.
5. `waitress` fuzz-gate decision (Phase 7).
6. Temporary key file during `load_cert_chain` — inherent to Python's TLS API.
7. Per-window pairing rate limit (Phase 7).

---

## 8. Final change inventory

- **24 files changed, 4,753 insertions, 2 deletions** against `797167ac`.
- **New:** 7 modules under `src/rawpos/mobile/`, 1 dialog, 8 test files.
- **Modified:** `main_window.py`, `app_shell.py`, `app.py`, `localization/__init__.py`,
  `pyproject.toml`, `tests/gui/test_main_window.py`, `tests/integration/test_app_shutdown.py`,
  `tests/unit/test_packaging_secrets.py`.
- **Commits:** `228ba57` protocol+registry, `6073550` pairing, `860e192` TLS, `5fe3a1a` listener,
  `1033e49` controller+dialog, `9bfd180` wiring, `5e787d3` occupied-port failure case.
- **RawPOS `main`:** unchanged at `797167a`.
- **Companion evidence:** `../phase-2/build-evidence.md`.

---

## 9. Closure statement

Phase 2 delivered every item the packet authorized, stayed inside its exclusions, and passed every
gate with quoted output. It did **not** integrate with checkout, change a schema, or alter business
behaviour — verified, not asserted.

The desktop half of the product is now real and tested. The phone half does not exist yet, so the
acceptance criterion "Android pairs with RawPOS via QR" remains **unmet** until Phase 3/4.

**Phase 2 is complete as a desktop phase. Phase 3 has not started, and A10/A11 remain unsigned.**

---

## 10. Post-report note — the merge

**Recorded 2026-11-21, after this report was written.** The owner authorized the merge that A16 had
reserved, and it was performed with `--no-ff` to keep the branch visible in history:

```
7efa178 Merge branch 'feature/mobile-receiver' into main
797167a previous main
```

| Check | Result |
|---|---|
| Merge kind | `--no-ff`, matching the repository's existing merge-commit convention |
| Parent state | `main` was `797167a`, 0 commits behind, clean tree — no rebase, no force |
| Gates re-run on `main` after the merge | ruff clean, mypy 157 files, no-float PASS, **1604 passed in 105.75s** |
| Push | `797167a..7efa178 main -> main`, private remote |

Nothing in this report's findings changes: the merge moved the reviewed commits onto `main` without
altering them, and the gate run after the merge reproduced the pre-merge result exactly.
