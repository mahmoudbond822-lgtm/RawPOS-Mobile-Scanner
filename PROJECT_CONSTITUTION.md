# PROJECT CONSTITUTION — RawPOS Mobile Scanner

**Version:** 1.0 (architecture phase)
**Status:** binding for all work on this repository once approved
**Companion application to:** RawPOS Desktop (PyQt6 / SQLCipher)

This constitution is deliberately short and enforceable. When a proposed change conflicts with it, the
change is wrong unless the constitution is amended by explicit owner decision.

---

## Article I — One authority

RawPOS Desktop is the **sole authority** for every business operation: product lookup, pricing,
discounts, stock, cart, checkout, payments, invoices, returns, shifts, audit, and licensing.

The Android application is a **sensor and a transport**. It captures a barcode and reports what the
desktop decided. It does not know products, prices, stock, customers, sales, or totals, and it must not
learn them.

**Test:** if the Android app were uninstalled, the shop could still run every business operation with a
USB scanner and a keyboard. If not, the boundary has been violated.

## Article II — No second POS

The following are forbidden on Android, in any form, including "temporary" or "offline" versions:

- product catalogue or product search;
- price, tax, discount, or total computation;
- cart, order, invoice, payment, receipt, or shift models;
- inventory reads or writes;
- customer data;
- any database schema of its own that mirrors POS data.

Android may store: device identity, connection credentials, user preferences, and a bounded diagnostic
log that contains no business data.

## Article III — Preserve the existing input paths

The USB barcode scanner (keyboard wedge) and keyboard-only checkout are production behavior. They must
continue to work **without any change in behavior**, including the 50 ms / 4-key heuristic, focus
restoration, and the "human typing is not a scan" rule.

Mobile scanning is an **additional input source** into the same checkout screen. It must not replace,
wrap, reimplement, or intercept the wedge path. Synthetic keyboard events are forbidden as an
integration mechanism.

## Article IV — Threads are a correctness boundary, not a style choice

- Qt widgets and every service built by the composition root belong to the GUI thread.
- The network receiver may run off the GUI thread, but it may only parse, authenticate, deduplicate, and
  hand off. It must never call `CheckoutService`, a repository, or a widget.
- Crossing into the GUI thread happens through one explicit, testable dispatch point.
- A design that cannot state, in one sentence, which thread owns the cart draft is rejected.

## Article V — Truthful feedback

A network acknowledgment is not a sale.

The system distinguishes, and the phone displays distinctly:

1. event received by the desktop;
2. event authenticated and validated;
3. event deduplicated;
4. event dispatched to the active checkout;
5. barcode resolved;
6. product added or merged into the cart;
7. product not found;
8. product inactive;
9. event rejected (no active checkout, read-only, rate limited, malformed).

The phone may show success only after the desktop confirms the cart mutation. No optimistic "added"
state, ever.

If the desktop cannot display or complete the sale — Sales is not active, the license is read-only, no
shift is open, or a restore is running — the mobile input is refused with a clear reason. The phone
must not report success for a line the cashier cannot see. This is an admission rule for the new input
source, not a change to checkout business logic; USB and keyboard behavior remain untouched.

## Article VI — One physical scan, one cart event

Duplicate suppression exists at three independent layers:

1. **Camera layer** — frame stabilization, same-value cooldown, re-arm rule.
2. **Client layer** — one immutable event id per physical scan, reused on retry.
3. **Desktop layer** — authoritative idempotency store keyed by `(device_id, event_id)`.

The desktop layer is authoritative. A retry must return the original outcome, not add a second unit.
A deliberate double-scan (two identical items) must remain possible; suppression is time/frame based,
not value based forever.

## Article VII — No new desktop framework, no unnecessary dependencies

- Desktop stays PyQt6. No web UI, no Electron, no second runtime, no embedded browser.
- Prefer the Python standard library and dependencies already present (`cryptography` is present).
- A new dependency must state: what it replaces, why stdlib is insufficient, its maintenance risk, its
  packaging cost, and its security surface. "It is convenient" is not a justification.
- Android dependencies follow the same rule, with an additional bias toward AndroidX/Jetpack and
  Google-maintained libraries that work offline.

## Article VIII — No custom cryptography

Use vetted primitives and protocols:

- TLS via Python `ssl` and Android's TLS stack;
- X.509 generation via `cryptography`;
- randomness via `secrets` / `SecureRandom`;
- token comparison in constant time;
- password/token hashing with SHA-256 only where the input is already high-entropy (device tokens),
  never for user passwords (there are none in this app).

No hand-rolled encryption, no custom MAC construction, no "simple obfuscation", no home-made key
exchange.

## Article IX — Local network only, off by default

- No cloud service, no telemetry, no analytics, no remote update channel.
- Normal scanning works with the internet disconnected.
- The receiver is **disabled by default** and enabled deliberately by the shop.
- It binds only to a local interface, never to a public address, and never proxies anything.
- Pairing is explicit, time-limited, one-use, and revocable.
- Every scan request is authenticated. There is no unauthenticated barcode submission.

## Article X — No database schema change

v1 introduces no migration and no new table in `rawpos.db`. Device registration, pairing sessions, and
token hashes live outside the business database in a dedicated, non-secret-except-hashes store.

If a future requirement genuinely needs a table, it stops here and returns for approval with a
migration plan, rollback plan, and backup/restore impact assessment.

## Article XI — Money and business logic stay untouched

No floating point in money. No arithmetic outside `rawpos.domain.pricing`. No change to discount,
stock, shift, invoice, or audit semantics. The mobile feature adds no business rule; if a business rule
needs to change to make mobile work, that is a separate, approved feature.

## Article XII — Localization parity

Every new desktop string exists in both Arabic and English in the same change, with identical key sets.
Every new Android string exists in both `values/` and `values-ar/`. Barcodes render as LTR digit runs
inside RTL layouts. Untranslated placeholders are defects, not debt.

## Article XIII — Evidence over claims

- "Tests pass" is only valid with the command, interpreter, environment, and result.
- "Manual QA passed" is only valid with device model, OS version, date, and the person who ran it.
- Assumptions are labeled as assumptions.
- A regression baseline must be measured, not remembered.
- A green suite on one interpreter does not prove another interpreter.

## Article XIV — Small, reversible, independently testable steps

Android and desktop are developed and tested independently. The protocol is a versioned contract with
shared fixtures. Every phase ends in a state that can be demonstrated and rolled back. No phase depends
on unfinished work in another phase to be testable.

## Article XV — Change control

The following require explicit owner approval **before** work starts:

1. any change to an existing business service or its semantics;
2. any database migration;
3. any new runtime dependency;
4. any change to the keyboard-wedge heuristic;
5. enabling a barcode format that can produce non-digit payloads;
6. any network binding beyond the local machine/LAN;
7. any deviation from the approved protocol or threat model;
8. any scope expansion (multi-store, cloud, product lookup on the phone, offline cart, etc.).

Silence is not approval.

---

## Amendment record

| Version | Date | Change | Approved by |
|---|---|---|---|
| 1.0 | 2026-11-20 | Initial constitution, architecture phase | pending |
