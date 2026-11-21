# Phase 5 Approval Packet — RawPOS integration

**Status:** prepared 2026-11-21 for owner signature. Nothing here is approved until §6 is signed.
**Purpose:** Phase 5 is the only phase that touches the till. It implements three decisions still marked
PROPOSED, needs A10, and needs its own authorization.
**Related:** `../architecture/desktop-integration.md`, `implementation-roadmap.md` Phase 5,
`../phase-4/scope-compliance.md`.

---

## 1. A25 — the GUI-thread bridge (D7)

**Decision:** the receiver thread parses, authenticates, and dedupes; a controller **on the GUI thread**
resolves the live `CheckoutView` and calls an additive `submit_scan()`; the receiver waits on a bounded
future with a deadline and a cancellation path.

**Why it must be decided here:** `CheckoutService` and every Qt widget share one SQLCipher connection and
are not thread-safe. A call from the receiver thread would be a race with the cashier's own clicks.

**What you accept:** a slow GUI produces `unknown` (the phone says *uncertain*), never a silent second
dispatch. The deadline is 2 s; past it the event is answered and the cashier checks the desktop.

**Rejected:** calling the service directly from the receiver thread (races), synthetic `QKeyEvent`s
(forbidden by the constitution and dangerous around focus/payment), a UI timer queue (a second
mechanism, worse latency).

**If refused:** the transport can carry scans but nothing can legally consume them, so Phase 5 does not
exist.

---

## 2. A26 — additive `submit_scan()` (D8)

**Decision:** extract the status / beep / refresh block out of `CheckoutView.scan()` into
`_render_scan_result()`. `scan()` keeps its exact behaviour; `submit_scan()` reuses the renderer without
touching `scan_edit` or focus.

**What you accept:** one widget change inside the highest-risk file in the app. The existing
keyboard-wedge tests are the gate: they must pass **unmodified**.

**Rejected:** making `scan()` return a result and setting the field (field and focus side effects would
change USB behaviour), duplicating the rendering (two code paths that will drift).

**If refused:** mobile scans would render differently from USB scans, which is exactly the divergence this
project exists to avoid.

---

## 3. A27 — language/digit remount (D23)

**Decision:** a language or digit-style switch keeps the receiver running; the bridge re-resolves the new
`CheckoutView` on every dispatch and **never caches a widget**; the checkout context derives from invoice
state, so it survives the remount.

**What you accept:** the view provider is a live lookup, so a remount cannot leave a stale widget
reference. A scan that arrives during the remount is answered normally once the new view is mounted.

**Rejected:** stop/restart the receiver on remount (drops pairing for a cosmetic change).

**If refused:** switching language mid-shift would break scanning until the phone is re-paired.

---

## 4. A10 — exact-only UPC/EAN

**Decision:** `CheckoutService.resolve_barcode(text)` performs **exact** barcode lookup only. No trimming,
no 12↔13-digit normalisation, no check-digit logic, no name fallback. `resolve_scan()` is unchanged.

**What you accept:** if the shop stores a UPC-A as 13 digits and the scanner emits 12 (or the reverse),
the phone reports **not found** until the pre-pilot audit says otherwise. That is a visible failure, not a
wrong product — which is the point.

**Why it can be approved without shop data:** exact-only is the conservative choice. The audit decides
whether normalisation is ever needed; it does not gate shipping the conservative behaviour.

**Rejected:** `resolve_scan()` (name-search fallback can add the wrong product — the R1 risk),
normalising blindly (can collide with real 13-digit codes), disabling mobile scanning (the safe fallback
if this is refused).

**If refused:** product scanning does not ship. A2 already approved the method; A10 approves its rule.

---

## 5. A28 — Phase 5 authorization

Scope, once the four items above are signed:

- `CheckoutService.resolve_barcode()` (additive, exact-only) + tests.
- `CheckoutView.submit_scan()` and the `_render_scan_result()` extraction.
- `MobileScanController`: live-view provider, 2 s deadline, cancellation, outcome mapping, context.
- Admission: Sales active, full mode, open shift, no modal, no transition, context match (A9).
- Status chip on the checkout screen.
- Wiring the controller into the receiver that Phase 4 built.

**Not in scope:** any change to money, pricing, discounts, stock, shifts, invoice finalization, audit
semantics, or schema. No synthetic key events. USB and keyboard behaviour unchanged.

---

## 6. Signature block

| # | Item | Decision | Owner | Date |
|---|---|---|---|---|
| A25 | Queued GUI-thread bridge, bounded deadline | D7 | Approved as specified | 2026-11-21 |
| A26 | Additive `submit_scan()` + shared renderer | D8 | Approved as specified | 2026-11-21 |
| A27 | Remount keeps connection and context | D23 | Approved as specified | 2026-11-21 |
| A10 | Exact-only UPC/EAN, no normalisation | D14/R13 | Exact-only approved | 2026-11-21 |
| A28 | Phase 5 authorization | — | **Authorized** | 2026-11-21 |

**Verification plan**

| Layer | What it proves |
|---|---|
| Unit | `resolve_barcode` exact-only; a numeric product name (`"Carton 1000"`) does **not** match `"1000"`; `resolve_scan` unchanged |
| GUI | added / merged / not found / inactive render identically to USB; focus in tender and quantity; modal open; language remount |
| Integration | real service + DB: a mobile scan adds exactly one line; hold/resume/finalize change the context; a stale scan is refused with no mutation |
| Regression | the wedge tests pass **unmodified**; the full suite stays green |
| Static | no synthetic `QKeyEvent`; no business import in the transport; route inventory unchanged |

**Not decided here:** nothing outstanding. After this packet the register has no open approval item.
