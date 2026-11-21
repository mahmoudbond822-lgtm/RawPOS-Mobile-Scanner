# Phase 5 — Scope Compliance Report

**Authorization:** A28 (Phase 5) with A10 (exact-only), A25 (D7 bridge), A26 (D8 submit_scan), A27 (D23
remount), plus the already-approved A2 (`resolve_barcode`) and A9 (admission policy).
**Result:** compliant on scope. No money, pricing, discount, stock, shift, invoice, audit, or schema change.
The keyboard path is behaviourally unchanged.

---

## 1. Approved scope vs delivered

| Approved deliverable | Delivered | Evidence |
|---|---|---|
| `CheckoutService.resolve_barcode()` (exact-only) | Added, sharing the add/merge tail | `test_checkout_resolve_barcode.py` (8 tests) |
| `CheckoutView.submit_scan()` | Added; never reads or clears `scan_edit`, never moves focus | `test_checkout_submit_scan.py` |
| `_render_scan_result()` extraction | Verbatim extraction from `scan()` | Existing checkout GUI suite, unmodified |
| `MobileScanController` | Queued GUI bridge, 2 s deadline, cancellation, admission | `test_mobile_bridge.py` (12 tests) |
| Admission policy (A9) | Sales, full mode, open shift, no modal, no transition | Parametrised refusal tests |
| Status chip | `mobile_label` with three states, updated by the shell | `test_the_mobile_chip_reports_the_receiver_state` |
| Wiring into the Phase 4 receiver | `MobileLinkController(dispatcher=…)`; shell builds the bridge | `test_mobile_scan_wiring.py` |

---

## 2. Exclusions — verified

| Exclusion | Verification | Result |
|---|---|---|
| No change to `resolve_scan` behaviour | Existing tests pass unmodified; exact-barcode and name-fallback tests | Pass |
| No synthetic key events | No `QKeyEvent`/`QTest.key` anywhere in the new code | Pass |
| No business call off the GUI thread | Test dispatches from a worker and asserts the call ran on the GUI thread | Pass |
| No money/stock/shift/schema change | Diff touches only `checkout_service.resolve_barcode`, `checkout_view` rendering, the bridge, and wiring | Pass |
| Keyboard wedge unchanged | The existing checkout GUI suite passes without edits | Pass |

---

## 3. Gates — measured

| Gate | Result |
|---|---|
| `ruff check .` | All checks passed |
| `mypy src` | no issues in **161** files |
| `scripts/check_no_float.py` | PASS |
| `pytest -q` | **1688 passed in 116.46s** |

Phase 4 ended at 1661; Phase 5 added 27 tests (8 service, 6 view, 12 bridge, 1 end-to-end).

---

## 4. What the tests actually prove

1. **Exact-only is real.** With a product named `"Carton 1000"`, `resolve_scan("1000")` still matches by
   name while `resolve_barcode("1000")` returns not found. The mobile path cannot add the wrong product.
2. **The thread boundary holds.** A scan driven over TLS from a worker thread reached a view method that
   ran on `QApplication.instance().thread()`.
3. **Admission refuses before acting.** Five conditions each return a typed rejection with zero calls
   into the view.
4. **A slow till says uncertain.** A blocking GUI beyond the deadline yields `unknown`, not a retry.
5. **The keyboard path is untouched.** `scan()` still clears its field, and the suite that guards it
   needed no modification.

---

## 5. Deviations and judgment calls

1. **The bridge lives in `ui/sales/mobile_bridge.py`, not `mobile/`.** The transport package is forbidden
   to import Qt by an existing boundary test; that test stays green, and the constraint is what dictated
   the location.
2. **`submit_scan` returns `None` on a domain refusal.** The screen has already rendered the reason; the
   bridge maps it to `rejected_domain` with `reason_code="DomainRefused"` rather than inventing a
   business class name it cannot see.
3. **The chip is text, not a styled badge.** Simplest thing that satisfies "the cashier can see the state";
   styling belongs to the design pass, not this phase.
4. **`AutoConnection` instead of an explicit queued connection.** PyQt6's typing stubs reject the
   explicit form, and auto-resolution is queued for a cross-thread emit, which is the only case that
   matters here. The test proves the behaviour rather than the declaration.

---

## 6. Acceptance criteria (roadmap Phase 5)

| Criterion | Status |
|---|---|
| A mobile scan adds exactly one line and renders the existing status | **met at the service and view level**; not yet observed on hardware |
| USB and keyboard-only checkout unchanged | **met** — suite unmodified |
| No business call from a non-GUI thread | **met** — asserted |
| A stale context never mutates the new invoice | **met** — Phase 4 service rules, unchanged |
| Read-only / no-shift scans refused with clear statuses | **met** — parametrised |
| Full RawPOS suite green | **met** — 1688 passed |

---

## 7. What is not proven

- **A real phone adding a real line on a real till.** The chain is proven at Python level and the
  hardware proved pairing in Phase 4; the combined run has not happened yet.
- **Windows Firewall on a shop LAN** (A7 approved, never exercised on shop hardware).
- **Arabic review** of the three new chip strings and the scanner strings (Q22).
- **Performance** — latency under a slow till, and the 2 s deadline in practice (Phase 6).
- **`device_already_paired`** still maps to `409 pairing_used`.

---

## 8. Open items

1. **Hardware end-to-end**: install the new APK, pair, scan, and confirm the line appears.
2. **Arabic review** (Q22).
3. **Pre-pilot label capture** — still the gate for any deployment (R13).
4. **Phase 6** (integration and regression testing) is not authorized.

---

## 9. Inventory

- **RawPOS:** `services/checkout_service.py` (+`resolve_barcode`, `_match_product`);
  `ui/sales/checkout_view.py` (+`submit_scan`, `_render_scan_result`, chip);
  `ui/sales/mobile_bridge.py` (new); `ui/app_shell.py` (bridge wiring, live view, context, chip);
  `mobile/controller.py` (dispatcher pass-through); localization (3 keys × 2).
- **Tests:** 4 new files, 27 tests.
- **Companion:** this report.

---

## 10. Closure statement

Phase 5 delivered the last hop: a mobile scan can reach the checkout view, render exactly like a keyboard
scan, and be refused cleanly when the till cannot accept it. The business services gained one additive
method; nothing about money, stock, shifts, or invoices changed.

The chain is proven in tests and has not yet been run on the shop floor.

**Phase 5 is complete. Phase 6 has not started.**
