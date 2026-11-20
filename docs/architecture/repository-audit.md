# Repository Audit — RawPOS (as consumed by RawPOS Mobile Scanner)

**Audit date:** 20 November 2026
**Audited revision:** `797167acbcb14d96b476891a573a7e31638acf12` (`main`)
**Audited tree:** `..\RawPOS` (sibling repository of this project)
**Working tree at audit:** clean (`git status --porcelain` returned 0 entries)
**Method:** read-only source inspection, `git` read commands, one full test run with the global Python
3.14 interpreter. No files in the RawPOS repository were created, modified, or deleted; no dependency
was installed; nothing was committed, pushed, or fetched.

> Scope note: this audit covers only what the companion mobile scanner can touch. It is not a general
> code review of RawPOS.

---

## 1. Repository state — measured

| Item | Value | Evidence |
|---|---|---|
| Branch | `main` | `git branch` |
| HEAD | `797167acbcb14d96b476891a573a7e31638acf12` | `git log -1` |
| HEAD subject | `docs: update handover register v3 with Feature 014 completion and manual QA approval` | `git log -1` |
| Working tree | clean, 0 entries | `git status --porcelain` |
| Test files | 215 `test_*.py` | filesystem count |
| Full suite (measured) | **1,450 passed in 134.97 s** | `py -3 -m pytest -q` |
| Interpreter used | Python 3.14.3 (global) | `py -3 --version` |
| Declared Python | `>=3.12` | `pyproject.toml` |
| Runtime dependencies | `PyQt6==6.10.2`, `sqlcipher3==0.6.3`, `cryptography==49.0.0` | `pyproject.toml` |
| Dev dependencies | `pytest==9.1.0`, `pytest-qt==4.5.0`, `ruff==0.17.0`, `mypy==2.4.0` | `pyproject.toml` |
| Repo `.venv` | **cannot run tests** — `No module named pytest` | `.venv\Scripts\python.exe -m pytest` |
| Packaging | Nuitka one-file Windows build, console disabled | `scripts/build_windows.ps1` |
| DB encryption | SQLCipher; schema head is migration `0009_audit_log` | `src/rawpos/migrations/versions/` |

**Important qualification:** the measured 1,450-pass run used the global interpreter, not the project
environment. It proves the current source is green on Python 3.14. It does not prove the declared
Python 3.12 environment is green, and it is not a CI result. Any future claim must state both facts.

---

## 2. Application shape

RawPOS is a single-process, single-`QApplication` PyQt6 desktop application with a strict composition root:

```
rawpos.app.main()
  ├── ConfigStore                       (JSON, no secrets)
  ├── Licensing + DB protection
  ├── StartupService                    (migrations, integrity, recovery decision)
  ├── AppContext                        (every service, built once)
  └── ApplicationShell                  (mounts exactly one screen)
        ├── Recovery / Activation / First-run / Login
        └── MainWindow
              ├── QListWidget  (module navigation)
              └── QStackedWidget (lazily created module pages)
                    └── Sales → CheckoutView(CheckoutService)
```

Evidence: `src/rawpos/app.py:127` (`build_context`), `:181` (`_fill_context`), `:284` (`main`);
`src/rawpos/ui/app_shell.py:60` (`show`), `:242` (`_sales_factory`);
`src/rawpos/ui/main_window.py:157` (`_page_for`), `:213` (`_activate`).

Consequences that matter for the companion app:

1. Services are constructed on the GUI thread and are never designed for cross-thread use.
2. Module pages are created on first activation and cached; `CheckoutView` is not recreated on every
   navigation, but it **is** recreated when the shell remounts the whole window (language or digit-style
   change) — `ApplicationShell._remount_active` at `app_shell.py:402`.
3. Only one top-level screen exists at a time. When the app is on Login, Recovery, Activation, or
   First-run, there is no `CheckoutView` and no usable checkout service.
4. `ApplicationShell.enter_recovery_mode()` (`app_shell.py:75`) deliberately nulls every database-backed
   service, including `checkout_service`.

---

## 3. The barcode pipeline as it exists today

### 3.1 Hardware path (keyboard wedge)

`CheckoutView` installs an application-wide event filter while it is visible
(`checkout_view.py:344`). The filter watches printable key presses:

- `_SCAN_GAP_NS = 50_000_000` (50 ms maximum gap between keys)
- `_MIN_SCAN_KEYS = 4` (shorter runs can never be a scan)
- a run is promoted to a scan only on Enter/Return and only if the last key was within 50 ms
- the run is taken back out of whatever widget received it, written into `scan_edit`, selected,
  and passed to `scan()` (`_wedge_route`, `checkout_view.py:333`)
- focus is returned to `scan_edit`

This is a heuristic, documented as such in the handover ("a guess until tested on the shop's real
scanner"). It is protected by four GUI tests in `tests/gui/test_checkout_ui.py` (lines 416–520).

### 3.2 Resolve path

`CheckoutView.scan()` (`checkout_view.py:441`) does:

```
text = scan_edit.text()
result = checkout_service.resolve_scan(text)
clear scan_edit
Matched      → status "sales.scan.added", then optional low-stock warning, refresh
NotFound     → status "sales.scan.not_found", QApplication.beep(), refresh
Deactivated  → status "error.ProductInactive", refresh
```

`CheckoutService.resolve_scan()` (`checkout_service.py:523`) does:

```
token = str(text).strip()
empty → NotFound("")
product = products.find_by_barcode(token)          # exact match
if none: product = products.search(token)[0]       # NAME / barcode search fallback
if none → NotFound(token)
if not product.active → Deactivated(name)
ensure draft started; _add_or_merge(product_id, 1.000)
→ Matched(product_id, name, line_no, merged)
```

`_add_or_merge` (`checkout_service.py:441`) merges by `product_id`: scanning the same product twice
increments the existing line by one unit. It never creates a second line for the same product.

### 3.3 Why this cannot be called from a network thread

- `CheckoutService` has no lock, no queue, and no thread-affinity guard.
- It mutates `_draft_lines` in place and calls repository methods bound to the single SQLCipher
  connection (`ProductRepository.find_by_barcode`, `barcodes_for`, `prices_for`,
  `InventoryService.on_hand`).
- `CheckoutView` reads the same service concurrently during paint/refresh and owns Qt widgets, which
  are GUI-thread-only.
- The service is also used by finalize, hold/resume, discounts, and shift reconciliation; a race can
  corrupt a live cart or a transaction.

**Audit conclusion:** a mobile receiver thread must never call `resolve_scan()`, any repository, or
any Qt widget. Delivery must cross into the GUI thread through a queued Qt signal or an equivalent
main-thread dispatch, and the GUI-thread handler must be the only caller into checkout.

---

## 4. Barcode and product constraints

- `product_barcode` is `TEXT NOT NULL UNIQUE` with
  `CHECK (barcode <> '' AND barcode NOT GLOB '*[^0-9]*')`
  (`0004_products_inventory.py:41-48`). Barcodes are **ASCII digits only, non-empty, globally unique**.
- `ProductService._clean_barcode` enforces the same rule and raises `InvalidBarcode`
  (`product_service.py:59-64`).
- No maximum length, minimum length, check-digit validation, or format table exists. A one-digit
  internal code and a 20-digit code are both legal.
- `ProductRepository.find_by_barcode` is an exact string comparison (`product_repository.py:190`).
  Leading zeros are significant: `"0123"` and `"123"` are different barcodes.
- `ProductRepository.search` uses `LIKE %query%` on Arabic and English names plus an exact barcode
  match (`product_repository.py:207`). `resolve_scan` falls back to it.
- Consequence: **any non-digit payload forwarded to `resolve_scan` cannot match by barcode and may
  match by name.** A QR code containing a product name, a URL containing a product name, or an
  alphanumeric Code 128 can therefore add a product. The mobile path must reject non-digit payloads
  before they reach the desktop pipeline.
- UPC-A nuance: ML Kit reports UPC-A as EAN-13 with a leading zero. Since RawPOS stores exactly what
  was entered (often from a USB scanner), a 12-digit stored UPC-A and a 13-digit mobile payload are
  different keys. This is an interoperability decision, not a bug in either side; see
  `docs/architecture/barcode-scanning.md`.

---

## 5. Business behavior that must be preserved

All of the following live in `CheckoutService` and are already covered by tests. The mobile scanner must
reach them only through the existing UI path, so their behavior is inherited rather than reimplemented.

| Behavior | Implementation | Evidence |
|---|---|---|
| One line per product; repeat scan increments | `_add_or_merge` | `checkout_service.py:441` |
| Inactive product not added | `resolve_scan` → `Deactivated` | `checkout_service.py:523` |
| No open shift blocks selling | `has_open_shift`, `_require_open_shift` | `checkout_service.py:469` |
| Negative stock allowed with warning | `_view` projected balance | `checkout_service.py:_view` |
| Low-stock warning | `low_stock_threshold_minor` | `checkout_service.py:_view` |
| Line/invoice discounts + owner approval | `set_line_discount`, `set_invoice_discount` | `checkout_service.py` |
| Hold / resume / cancel | `hold`, `resume`, `cancel` | `checkout_service.py` |
| Cash finalize, change, receipt after commit | `finalize` | `checkout_service.py:finalize` |
| Audit in the same transaction | `audit_event` | `checkout_service.py`, `infra/logging.py` |
| Read-only mode blocks mutations at finalize/discount/hold | gate calls | `checkout_service.py` |

The scanner must not duplicate any of this on Android and must not add a parallel "scan" service on the
desktop. It is a new **input source** into the same screen.

---

## 6. Threading, shutdown, and recovery

- GUI thread only for services and widgets. The only background helper is
  `src/rawpos/infra/workers.py` (`threading.Thread` + callbacks), used for long backup/restore work.
- `DatabaseProtectionService` exposes `begin_critical_operation()` / `end_critical_operation()`;
  `CheckoutService.finalize` brackets itself with them (`checkout_service.py:finalize`). Backup and
  restore can close or replace the database file.
- Shutdown sequence in `app.main()`: detach audit sink → checkpoint WAL and close DB → release
  single-instance lock → re-raise first shutdown error (`app.py:main`, `finally`).
- Recovery entry nulls `checkout_service`, `authentication`, and every other DB service
  (`app_shell.py:75`).
- A mobile receiver therefore needs an explicit lifecycle: start after the shell mounts, pause/reject
  while the DB is unavailable or a restore is in progress, and stop before the DB closes.

---

## 7. Localization

- Two in-code catalogs, Arabic (default) and English: **655 keys each, exact parity**
  (measured by importing `rawpos.localization._CATALOGS`).
- `tr(key, **kwargs)` falls back to Arabic then to the key; `error_text(exc)` maps
  `error.<ClassName>` and never exposes raw exception text when a catalog entry exists
  (`localization/__init__.py:1364`, `:1371`).
- Views must use `tr()` for every string and the shared number/money formatters for every number.
- Existing checkout keys include `sales.scan.placeholder`, `sales.scan.added`,
  `sales.scan.not_found`, `sales.scan.search_failed`, `sales.status`, and the payment/discount keys.
- The mobile app has its own string resources; the desktop's new keys (pairing dialog, status,
  device management) must be added to both catalogs in the same change.

---

## 8. Design system

RawPOS already has a real design system, not a proposal:

- `src/rawpos/ui/theme/tokens.py`: semantic colors for light/dark, type, spacing, radius, border,
  elevation, control sizes, `touch_target_min = 44`.
- `build_qss()` converts tokens to the Qt stylesheet (`ui/theme/qss.py`).
- `ThemeManager` persists light/dark in config and applies the palette (`ui/theme/theme_manager.py`).
- A widget kit exists: themed buttons, card, data table, dialog, empty/loading/error/feedback states,
  section header, text/number fields, RTL helpers (`ui/theme/widgets/`).
- Icons are Lucide SVGs (`ui/icons/`).
- Views are expected to use semantic roles, never hard-coded hex.

The Android app must derive a **mobile** language from this system; desktop point sizes, density,
hover states, and the sidebar model must not be copied. See `docs/design/mobile-design-system.md`.

---

## 9. Logging and audit

- `audit_event()` redacts any field whose name contains a sensitive token (`key`, `secret`, `password`,
  `recovery`, `credential`, `token`, `salt`, `serial`, `guid`, `fingerprint`, `machine`, `volume`,
  `cpu`) and writes structured key=value lines (`infra/logging.py:29-100`).
- An attached sink receives the same redacted mapping. A sink failure propagates so the caller can roll
  back (`logging.py:82`).
- `audit_app_event()` prefixes `app.`.
- Device tokens, pairing secrets, and certificate private keys are therefore never loggable by name;
  the mobile receiver must not invent field names that evade redaction (e.g. `scan_token` is still
  caught because the check is token-based).

---

## 10. Networking

- **RawPOS currently performs no network I/O.** Two tests enforce this:
  - `tests/failure/test_no_network.py` patches `socket.socket`, `socket.create_connection`, and
    `socket.getaddrinfo` to raise, then exercises licensing, DB, backup, restore.
  - `tests/integration/test_no_network.py` does the same for startup, authentication, the app shell,
    language/digit-style switches, and backup.
- Adding an inbound LAN listener will require a **deliberate amendment** to this guarantee. The correct
  amendment is: no outbound internet, no cloud, receiver disabled by default, receiver only reachable
  on the local network, and tests that assert the listener is off unless explicitly enabled. Silently
  deleting or weakening the existing tests is not acceptable.
- The stdlib already provides everything needed for a minimal HTTP/TLS server (`http.server`, `ssl`,
  `socket`, `threading`, `secrets`, `hmac`, `hashlib`, `uuid`, `ipaddress`), and `cryptography` is
  already a runtime dependency for X.509 generation. No new desktop dependency is required.

---

## 11. Tests and gates

- Markers: `unit`, `integration`, `failure`, `migrations`, `gui`; pytest-qt is available in dev extras.
- Gates documented by the project: `ruff check .`, `mypy src`, `py scripts/check_no_float.py`,
  `py -3 -m pytest -q`.
- The checkout screen alone has 25+ GUI tests, including the wedge/focus/typing cases. These are the
  first regression net for any desktop integration change.
- There is no visual regression framework, no screenshot baseline, and no Android CI.
- `tests/gui/*` requires a Qt platform; the measured run succeeded in this environment, so GUI tests
  are runnable here.

---

## 12. Integration constraints (audit conclusions)

1. **No direct service calls from the receiver thread.** Use a main-thread bridge.
2. **No synthetic keyboard events.** Injecting `QKeyEvent`s would be indistinguishable from a physical
   scanner to the wedge filter, would fight the focus logic, and would make "mobile vs USB vs human"
   undiagnosable. Rejected.
3. **Add one narrow, additive entry point on `CheckoutView`** (for example
   `submit_scan(text: str) -> ScanResult | None`) that reuses the existing result rendering but does not
   touch `scan_edit` or steal focus. The current `scan()` should be refactored to share the rendering
   helper without changing its observable behavior.
4. **The bridge must resolve the live `CheckoutView`, not cache it**, because language switching
   destroys and recreates the whole main window. A provider callback or weak reference is required.
5. **Reject scans when there is no active Sales screen.** Do not auto-navigate, do not queue
   indefinitely, and do not deliver into another module. The phone must show a clear "not accepted"
   state.
6. **Never send non-digit payloads into `resolve_scan`.** The name-search fallback makes that unsafe.
7. **Preserve the wedge filter and `scan_edit` path untouched.** Mobile is an additional source, not a
   replacement.
8. **Do not modify the database schema.** Device registry and pairing state can live in a dedicated
   JSON file and/or config, with token hashes only.
9. **Respect read-only, no-shift, inactive-product, and recovery states** by letting the existing
   service decide; the receiver only transports and reports the outcome.
10. **Amend the no-network tests explicitly**, with a new assertion that the listener is off by default
    and binds only when enabled.

---

## 13. Verified facts vs assumptions

| Claim | Status | Basis |
|---|---|---|
| Checkout service owns the draft in memory and mutates it on scan | Verified | `checkout_service.py` |
| No thread synchronization exists around checkout | Verified | source inspection |
| Barcodes are ASCII digits only, unique, no length rule | Verified | migration `0004`, `product_service.py` |
| `resolve_scan` falls back to name search | Verified | `checkout_service.py:523` |
| CheckoutView installs an app-wide wedge filter while visible | Verified | `checkout_view.py:344` |
| Pages are lazily mounted and cached; shell remounts on language change | Verified | `main_window.py:213`, `app_shell.py:402` |
| Full suite currently passes | Verified (measured, Python 3.14) | 1,450 passed / 134.97 s |
| Project `.venv` cannot run the suite | Verified | missing pytest |
| No network code exists today | Verified | source + no-network tests |
| `cryptography` is already bundled | Verified | `pyproject.toml`, build script |
| A queued Qt signal is the right bridge | Assumption / design decision | to be confirmed by Agent 1 |
| A single active checkout is sufficient for v1 | Assumption | to be confirmed by Agents 1/4 |
| Pairing state can live outside SQLCipher | Assumption | to be confirmed by Agents 1/4 |
| 50 ms / 4-key wedge heuristic is adequate for the shop's scanner | Unverified | handover says "a guess" |
| Scanner performance on real hardware | Unverified | no device available |

---

## 14. Residual risks introduced by this audit

- The measured green suite used Python 3.14, not the declared 3.12; a 3.12 CI run is still required.
- The audit did not exercise a real USB scanner, a real LAN, or a real Android device.
- `tests/integration/test_no_network.py` will fail the moment a listener is opened in the same process
  unless the test is amended deliberately; that is a feature, not an obstacle.
- The handover notes that barcode-scanner behavior is still a heuristic on real hardware; mobile
  integration must not be used as evidence that the wedge heuristic is correct.

---

## 15. Post-audit decisions that refine these conclusions

The audit above records the repository as it is. The consolidated architecture then makes three
decisions that go beyond the audit:

1. **Exact-barcode-only service path (D9).** Digit-only validation is not sufficient because
   `resolve_scan()` can match a numeric product name. The mobile path uses an additive
   `CheckoutService.resolve_barcode()`; reusing `resolve_scan()` is rejected. This is approval-gated.
2. **Mobile admission policy (D17).** The phone is admitted only when Sales is active, the license is
   full mode, a shift is open, and no transition is running. This prevents a false "added" while the
   desktop cannot display or complete the sale. USB and keyboard behavior are unchanged.
3. **Checkout-context token (D11).** A delayed scan is rejected if the invoice changed, rather than
   landing in the next sale.

These do not change the verified facts in §§1–12; they constrain how the new input source may use them.
