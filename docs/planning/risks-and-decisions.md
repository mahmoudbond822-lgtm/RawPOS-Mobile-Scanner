# Risks, Decisions, and Open Questions

**Status:** live register for the architecture phase
**Rule:** a decision marked PROPOSED is not binding until the owner approves it. A decision marked
BLOCKED stops the dependent work.

---

## 1. How to read this register

| Status | Meaning |
|---|---|
| **PROPOSED** | Recommended by the Principal Architect; awaiting owner approval |
| **APPROVED** | Owner has explicitly accepted it (record date and name) |
| **BLOCKED** | Cannot proceed without an approval or missing fact |
| **REJECTED** | Considered and deliberately not chosen |
| **DEFERRED** | Valid, but not in v1 |

Every decision carries: rationale, alternatives, consequences, and the phase that depends on it.

**Phase 4 route design:** the approved design for `POST /v1/scans` and `GET /v1/context`, including ten
decisions (Q1–Q10) that resolve the gaps A12 leaves open, is recorded in
`../architecture/desktop-scan-routes.md` (approved 2026-11-21).

---

## 2. Decision register

### D1 — Desktop is the sole business authority

**Status:** PROPOSED (constitutional)
**Decision:** Android captures and transmits a barcode. All lookup, pricing, stock, cart, payment,
invoice, shift, and audit logic stays in RawPOS.
**Rationale:** avoids a second POS, duplicated rules, and divergent totals; matches the product vision.
**Alternatives:** thin client with local price cache (rejected: stale data, business logic duplication);
shared database (rejected: bypasses services, SQLCipher coupling).
**Consequence:** the phone cannot show product names/prices in v1.
**Depends on:** nothing. **Phase:** all.

### D2 — HTTP/1.1 + JSON over TLS, not WebSocket

**Status:** **APPROVED — owner, 2026-11-21 (A19)**
**Decision:** one authenticated `POST /v1/scans` per scan, response after business processing.
**Rationale:** request/response matches scan semantics; simple to test, reason about, and secure;
WebSocket adds state without a v1 requirement.
**Alternatives:** WebSocket (deferred until push/multi-till is required); raw TCP (rejected: custom
framing); long-poll (rejected: latency/state).
**Consequence:** no desktop→phone push in v1.
**Phase:** 2/4.

### D3 — QR pairing with one-time secret, desktop approval, and a six-digit code

**Status:** **APPROVED — owner, 2026-11-20 (A5)**
**Decision:** desktop opens a 120 s, one-use window; QR carries endpoint, desktop id, SPKI pin, and a
256-bit secret; the phone submits the secret; the desktop shows the device name + code; the cashier
approves; the desktop issues a token.
**Rationale:** no CA or internet; resists shoulder-surfing and rogue QR; explicit local consent.
**Alternatives:** immediate pairing without approval (simpler, weaker against a photographed QR);
manual host/code entry (no QR, violates the requested workflow); cloud pairing (rejected).
**Consequence:** one extra tap on the desktop.
**Phase:** 2.

### D4 — Self-signed ECDSA P-256 certificate, SPKI pin from the QR

**Status:** PROPOSED
**Decision:** generate a local certificate with `cryptography`; pin its SPKI SHA-256 in the QR; require
TLS 1.2+; reissue with the same key on IP change so the pin survives; fail closed on mismatch.
**Rationale:** authenticates the desktop without a CA, internet, or user-installed root.
**Alternatives:** plain HTTP + HMAC (rejected: exposes traffic); system CA (impossible for a private
IP); user-installed CA (operational burden); trust-on-first-use (rejected: MITM).
**Consequence:** key loss requires re-pairing; certificate expiry must be monitored.
**Phase:** 2.

### D5 — Desktop-generated 256-bit device token, hash at rest, Keystore on phone

**Status:** **APPROVED — owner, 2026-11-21 (A20)** (retroactive for the desktop half shipped in Phase 2)
**Decision:** desktop generates the token, returns it once over pinned TLS, stores a domain-separated
SHA-256 verifier; the phone stores the token with Android Keystore AES-GCM and backup disabled.
**Rationale:** simple, revocable, scan-only; no plaintext token at rest on the desktop.
**Alternatives:** phone-generated token (equally secure, more pairing state; not chosen); password
(rejected: weaker, human-chosen); client certificate (heavier provisioning).
**Consequence:** re-pairing rotates the token.
**Phase:** 2/2A.

### D6 — Receiver off by default, LAN-only, port 47653

**Status:** **APPROVED — owner, 2026-11-20 (A3 listener; A7 port and firewall)**
**Approval note:** A3 (owner, 2026-11-20) approved the inbound listener, disabled by default, and the
no-network test amendment. A7 (owner, 2026-11-20) approved port 47653 with the Private-profile,
LocalSubnet-only firewall rule, on the condition that a bind failure reports the error and the configured
alternative rather than silently changing the port.
**Decision:** no listener until a signed-in user enables it; bind to a selected private interface;
Private-profile/LocalSubnet firewall rule; no wildcard/public/VPN binding.
**Rationale:** smallest attack surface; preserves the offline guarantee.
**Alternatives:** on at startup (rejected); wildcard bind (rejected); UPnP (rejected).
**Consequence:** setup requires a deliberate action and firewall instructions.
**Phase:** 2.

### D7 — Queued Qt bridge; no business call from the receiver thread

**Status:** **APPROVED — owner, 2026-11-21 (A25)**
**Decision:** the receiver parses/authenticates/dedupes; a controller on the GUI thread resolves the live
`CheckoutView` and calls an additive `submit_scan()`; the receiver waits on a bounded future.
**Rationale:** `CheckoutService` and Qt widgets are not thread-safe and share one SQLCipher connection.
**Alternatives:** direct call (rejected: races); synthetic key events (rejected: focus/payment risk);
UI timer queue (rejected: latency, second mechanism).
**Consequence:** one event-loop turn; a dispatch deadline and cancellation path are required.
**Phase:** 5.

### D8 — Additive `CheckoutView.submit_scan()` with a shared result renderer

**Status:** **APPROVED — owner, 2026-11-21 (A26)**
**Decision:** extract the status/beep/refresh block into `_render_scan_result()`; `scan()` keeps its
behavior; `submit_scan()` reuses the renderer without touching `scan_edit` or focus.
**Rationale:** maximum fidelity to USB behavior, minimum blast radius, preserves focus.
**Alternatives:** make `scan()` return a result and set the field (rejected: field/focus side effects);
duplicate the rendering logic (rejected: drift).
**Consequence:** one widget change; existing wedge tests are the gate.
**Phase:** 5.

### D9 — Exact-barcode-only desktop entry point

**Status:** **APPROVED — owner, 2026-11-20 (A2)**
**Decision:** add `CheckoutService.resolve_barcode(text)` that performs exact barcode lookup only and
shares the add/merge tail with `resolve_scan()`. `resolve_scan()` behavior is unchanged.
**Rationale:** `resolve_scan()` falls back to a name `LIKE` search; even a digits-only value can match a
product name (`"1000"` vs `"Carton 1000"`) and add the wrong product. Digit filtering is necessary but
not sufficient.
**Alternatives:** reuse `resolve_scan()` (rejected: integrity risk); controller pre-check via
`ProductRepository` (rejected: business lookup in the bridge, double query, race); disable mobile
scanning (the safe fallback if D9 is refused).
**Consequence:** the only expected change to a business service; additive and behavior-preserving for
existing callers. Without it, product scanning does not ship.
**Phase:** 5. **Approval:** A2, owner, 2026-11-20.

### D10 — Three-layer duplicate suppression, desktop authoritative

**Status:** **APPROVED — owner, 2026-11-21 (A12)**
**Decision:** camera stabilizer + one event id per physical scan + desktop idempotency keyed by
`(device_id, event_id)`, reserve-before-dispatch, 24 h / 50k, fail closed when full.
**Rationale:** one physical scan must never add two units; retries must be safe; deliberate double scans
must still work.
**Alternatives:** value/time dedupe only (rejected: blocks legitimate doubles); client-only (rejected:
a client bug corrupts the cart); server-only (rejected: network flood).
**Consequence:** in-memory dedupe is not durable across a desktop crash (D20).
**Phase:** 2/4.

### D11 — Checkout-context token

**Status:** **APPROVED — owner, 2026-11-21 (A21)**
**Decision:** an opaque token derived from the current invoice identity (`invoice_id`, `created_at`,
`status`) travels with each scan; a mismatch rejects with `rejected_stale_context` and no mutation.
**Rationale:** prevents a delayed scan from landing in the next invoice after finalize/hold/resume.
**Alternatives:** event age only (rejected: clock skew, still cross-invoice); no context (rejected:
wrong-sale risk); rotate on view remount (rejected: language switch would lose a valid context).
**Consequence:** one scan may be rejected after an invoice change; the cashier rescans.
**Phase:** 4/5.

### D12 — No database schema change; registry outside `rawpos.db`

**Status:** **APPROVED — owner, 2026-11-20 (A6)**
**Approval note:** DPAPI protection approved with the one-way ACL fallback rule: a fallback chosen at
first use is recorded and never silently migrated later; a later unprotect failure keeps the receiver off
and never falls back to plaintext.
**Decision:** device registry and pairing state live in `%LOCALAPPDATA%\RawPOS\mobile\`, with token
hashes and timestamps; TLS key protected by DPAPI (or ACL fallback). No migration.
**Rationale:** keeps transport state out of the business DB; avoids schema/backup/restore coupling.
**Alternatives:** new tables (rejected: migration, backup coupling); `config.json` (rejected: explicitly
non-secret); DB-embedded registry (deferred if durability is required).
**Consequence:** a corrupt registry fails closed and requires re-pairing; it is not covered by the
RawPOS backup, so document that.
**Phase:** 2.

### D13 — Truthful status ladder

**Status:** **APPROVED — owner, 2026-11-21 (A22)**
**Decision:** `added` / `merged` only after the desktop confirms the cart mutation; transport receipt,
authentication, validation, dispatch, and resolution are distinct internal stages and never shown as
success.
**Rationale:** a network ack is not a sale; the cashier must trust the phone.
**Alternatives:** optimistic UI (rejected); generic "sent" (rejected: ambiguous).
**Consequence:** the phone needs a "Waiting for desktop" and "Delivery uncertain" state.
**Phase:** 4/3.

### D14 — Barcode format set

**Status:** **APPROVED PROVISIONALLY — owner, 2026-11-21 (A11).** Approved as a **default set**, not as a
statement about the shop: no real shop data exists yet (`../quality/barcode-audit-2026-11-21.md`). The
pre-pilot verification against real labels is mandatory and may change the enabled set through settings
without an architecture change.

**Decision:** enable EAN-13, EAN-8, UPC-A, and numeric Code 128; disable UPC-E, Code 39, ITF-14, and QR for
product scanning; QR is enabled only in pairing mode.
**Decision:** enable EAN-13, EAN-8, UPC-A, and numeric Code 128; disable UPC-E, Code 39, ITF, and QR for
product scanning; QR is enabled only in pairing mode.
**Rationale:** RawPOS stores ASCII digits only; alphanumeric/QR payloads cannot match and may trigger
name search. Disabled formats can be enabled per shop after verification.
**Alternatives:** enable all formats and filter later (rejected: risk, noise); QR product scanning
(rejected: URLs/text).
**Consequence:** UPC-E/ITF shops need an explicit setting.
**Phase:** 3. **Input needed:** actual shop symbologies.

### D15 — No offline scan queue

**Status:** **APPROVED — owner, 2026-11-21 (A12, same decision as D20)**
**Decision:** the phone never stores scans for later automatic delivery; one in-flight event may retry
within 15 s; older scans expire and must be rescanned.
**Rationale:** a queued scan can land in a different invoice or after the customer left; silent replay is
worse than a rescan.
**Alternatives:** store-and-forward (deferred; needs durable context/ordering); unlimited retry
(rejected).
**Consequence:** a network outage means manual rescanning.
**Phase:** 3/4.

### D16 — One active device per desktop

**Status:** **APPROVED — owner, 2026-11-20 (A5)**
**Decision:** one paired phone is active; pairing another requires "Replace current device" and revokes
the old token.
**Rationale:** removes ambiguity about which phone is feeding the cart; matches the single-cashier vision.
**Alternatives:** multiple simultaneous scanners (deferred: ordering/UX complexity).
**Consequence:** a second phone cannot be a hot spare without replacing.
**Phase:** 2.

### D17 — Mobile admission policy (stricter than USB)

**Status:** **APPROVED — owner, 2026-11-20 (A9)**
**Approved policy:** a mobile scan is admitted only when all of the following are true at dispatch time:

- the Sales screen is active;
- Full Mode is enabled;
- the signed-in cashier has an open shift;
- no modal dialog is open;
- no backup/restore operation or application transition is in progress;
- the checkout context matches.

Otherwise the scan is refused with `rejected_no_checkout`, `rejected_read_only`,
`rejected_no_shift`, `rejected_busy`, or `rejected_unavailable`, and nothing is dispatched.

**Rationale:** truthfulness. The existing UI allows `resolve_scan()` to add a draft line with no shift or
in read-only mode, but does not display or complete it. The phone would say "added" while the cashier
cannot see or finish the sale. The admission rule does not change checkout business logic; it declines
to inject input when the screen is not sellable. USB and keyboard behavior remain exactly as they are.
**Alternatives:** mirror USB exactly (rejected: misleading success, invisible cart lines); relax the
rule per state (more complexity, unclear truthfulness).
**Consequence:** mobile and USB diverge in these edge states by design; documented and testable.
**Phase:** 5. **Approval:** A9, owner, 2026-11-20.

### D18 — QR generation dependency

**Status:** **APPROVED — owner, 2026-11-20 (A4)**
**Decision:** add `segno` (pure Python, zero transitive dependencies, BSD) to render the pairing QR.
**Rationale:** the QR workflow is required; writing a QR encoder is worse; `segno` has no runtime
footprint beyond itself.
**Alternatives:** `qrcode` (needs Pillow for PNG; SVG possible); manual host/code entry (violates the
requested QR workflow); a cloud QR image (rejected: internet).
**Consequence:** one new pinned desktop dependency; Nuitka must bundle it (pure Python, low risk).
**Phase:** 2.

### D19 — HTTP server hardening path

**Status:** **APPROVED — owner, 2026-11-20 (A8)** (release gate retained: fuzz must pass, or `waitress` is
adopted)
**Decision:** start with `http.server.ThreadingHTTPServer` and a hardened custom handler; fuzz it in
Phase 7. If fuzzing fails, adopt `waitress` (pure Python, zero transitive dependencies) as the one
approved server dependency.
**Rationale:** avoids a dependency before it is proven necessary, while refusing to ship an unhardened
parser.
**Alternatives:** `waitress` now (safer, new dependency); custom HTTP parser (more code/risk);
Qt `QTcpServer` (manual HTTP, worse).
**Consequence:** a release gate, not a vague intention.
**Phase:** 2/7.

### D20 — Uncertain delivery across a desktop restart

**Status:** **APPROVED — owner, 2026-11-21 (A12)**
**Decision:** `server_instance_id` changes on restart. The phone does **not** auto-replay an
unacknowledged event across a restart; it shows "Delivery uncertain — check the desktop".
**Rationale:** the cart is in memory; a replay could duplicate or land in a new sale. A missed scan is
safer than a wrong one.
**Alternatives:** auto-replay (rejected: duplicate risk); durable inbox coupled to cart persistence
(out of scope; would need schema/lifecycle redesign).
**Consequence:** the cashier may need to rescan after a crash.
**Phase:** 4.

### D21 — Android platform baseline

**Status:** PROPOSED (reviewed by Agent 2)
**Approval note:** A13 (owner, 2026-11-20) approved the platform baseline: Kotlin/Compose, minSdk 26,
and targetSdk 36, subject to the documented compatibility constraints. CameraX, ML Kit, OkHttp, and the
remaining D21 implementation details are **not** approved by A13 and remain proposed.
**Decision:** Kotlin 2.2.x, Jetpack Compose + Material 3, single Activity, CameraX 1.6.x, a manual
`ImageAnalysis.Analyzer`, bundled ML Kit 17.3.x, coroutines/StateFlow, OkHttp with a pin-only trust
manager, Keystore-backed storage, manual DI, minSdk 26, **compile/targetSdk 36**. No VIBRATE,
notification, or `ACCESS_LOCAL_NETWORK` permission. One in-flight scan, no queue.
**Rationale:** offline-first (bundled model), modern lifecycle-safe camera, one HTTP client with robust
pinning, no Play Services; target 36 avoids Android 17's new local-network runtime permission.
**Alternatives:** XML views (viable, more boilerplate); Camera2 (more code); `MlKitAnalyzer` (adds a
dependency; only useful for coordinate overlays); unbundled ML Kit (needs download); Hilt (adds build
complexity); target 37 (adds `ACCESS_LOCAL_NETWORK`).
**Consequence:** APK size grows by the bundled model (~2.4 MB); a future target 37 is a separate
approved change.
**Phase:** 1/3.

### D22 — Sign-out stops the receiver

**Status:** PROPOSED
**Decision:** stop accepting and stop the server on sign-out; the next signed-in user must enable it.
**Rationale:** no authorized cashier means no scanning; avoids scans at the login screen.
**Alternatives:** keep running and reject (more state, unclear ownership).
**Consequence:** re-enable after each sign-in; acceptable for a till.
**Phase:** 2/5.

### D23 — Language/digit remount preserves the connection and context

**Status:** **APPROVED — owner, 2026-11-21 (A27)**
**Decision:** the receiver keeps running; the bridge re-resolves the new `CheckoutView`; the context token
is derived from invoice state, so it survives the remount.
**Rationale:** a language switch must not drop pairing or duplicate a scan.
**Alternatives:** stop/restart the receiver on remount (disruptive, unnecessary).
**Consequence:** the view provider must never cache a widget.
**Phase:** 5.

---

## 3. Risk register

| ID | Risk | L / I | Mitigation | Owner | Phase |
|---|---|---|---|---|---|
| R1 | Wrong product via name-search fallback | H / H | D9 exact-only; test with a numeric product name; do not ship without it | Desktop | 5 |
| R2 | Duplicate cart lines from frames/retries | H / H | Three-layer dedupe; reserve-before-dispatch; fault injection | Net + Barcode + QA | 2/4 |
| R3 | Keyboard-wedge/focus regression | M / H | Do not touch `scan()`/`eventFilter`; shared renderer; wedge tests as gate | Desktop UI | 5 |
| R4 | Business call from a non-GUI thread | M / H | Queued bridge; thread assertion; import-architecture test; off-thread test | Desktop | 5 |
| R5 | Stale view after shell remount | M / H | Live-view provider + weak ref; language-switch test | Desktop UI | 5 |
| R6 | Late scan lands in the next invoice | M / H | Context token; reject with no mutation; no auto-apply | Net + Desktop | 4/5 |
| R7 | Restore/backup races a scan | M / H | Critical-op bracket; stop/disarm before recovery; transition rejection | Desktop | 5 |
| R8 | No-network guarantee eroded | M / H | Keep outbound denial; amend tests explicitly; receiver off by default; static gate | QA + Net | 2/6 |
| R9 | TLS pin operational failure | M / H | Same-key reissue; clear error; re-pair path; no trust-anyway | Net + Android | 2 |
| R10 | QR shoulder-surf / pairing race | M / M | TTL, one use, approval, device name + code, visible pending device | Net + UX | 2 |
| R11 | Lost/stolen phone | M / H | Keystore, no backup, revoke, scan-only scope | Net + Android | 2 |
| R12 | DoS / parser abuse | M / M | Bounds, rate limits, fuzz gate, supervised thread | Net + QA | 2/7 |
| R13 | UPC-A/EAN-13 representation mismatch | H / M | Exact-only; **pre-pilot label capture is a blocking pilot gate** (audit tooling ready, no real data yet); shop normalization decision still open | Barcode + Owner | 0/3 |
| R14 | Barcode format false negatives | M / M | Configurable formats; per-shop verification; raw value display | Barcode | 3 |
| R15 | Android fragmentation/camera variance | H / M | 720p/10 fps; device matrix; fallback instructions | Android + QA | 3/6 |
| R16 | Battery/thermal drain | M / M | Camera off when backgrounded; 10 fps; measure; no wake locks | Android | 3/6 |
| R17 | `http.server` hardening | M / H | Fuzz or `waitress` release gate | Net | 7 |
| R18 | DPAPI/registry corruption | L / H | Atomic writes; fail closed; re-pair; no plaintext fallback | Desktop | 2/7 |
| R19 | Localization drift | M / M | Key-parity tests; native review; `[needs review]` gate | UX + QA | 1/6 |
| R20 | Scope creep into a second POS | M / H | Constitution; no data endpoints; route inventory test | Architect | all |
| R21 | No Android tooling/CI | H / H | Procure device/toolchain; local scripts first; evidence rules | Owner + QA | 0/1 |
| R22 | Unverified performance | H / M | Benchmarks in Phase 3/4/6; no claims without artifacts | QA | 3/6 |
| R23 | New QR dependency risk | L / L | `segno` pinned, pure Python, reviewed; manual fallback | Desktop | 2 |
| R24 | Barcode logging/privacy | M / M | Never log barcodes; redaction test; diagnostics view | Net + QA | 2/6 |
| R25 | Multi-device ambiguity | M / M | One active device; replace flow; per-device audit | Desktop | 2 |
| R26 | Shop data has non-standard barcodes | M / M | Audit a copy (with permission); configure formats/length; no silent guess | Barcode + Owner | 0 |
| R27 | Windows Firewall blocks the first run | H / L | Explicit instructions; connectivity test; show exact endpoint | Desktop | 2/8 |
| R28 | Phone clock skew breaks TLS | L / M | Android auto-time; clear error; cert validity tolerant by 1 day | Android | 2 |
| R29 | Android 17 target 37 forces `ACCESS_LOCAL_NETWORK` | M / M | Stay on target 36 in v1; treat 37 as an approved change with a permission flow | Android | 1/8 |

---

## 4. Open questions for the owner

Each question has a recommended answer. Silence is not approval.

### Product and business

| # | Question | Recommended answer |
|---|---|---|
| Q1 | Should mobile scans be admitted when no shift is open or the license is read-only? | **No** — refuse with a clear status (D17). This is a mobile admission rule, not a change to checkout logic. |
| Q2 | Should the phone display a product name after a successful scan? | **No.** It displays the barcode and a status. A name is business data and expands the boundary. |
| Q3 | Is one active phone per desktop enough? | **Yes** for v1. A second phone replaces the first after confirmation. |
| Q4 | Is a desktop approval tap during pairing acceptable? | **Yes.** It is the main defence against a photographed QR. |
| Q5 | Should the phone keep a local scan history? | **No** by default. It is purchase data on a portable device. |
| Q6 | What is the expected scan volume per hour? | Needed for rate-limit/dedupe sizing; assume ≤ 1,000/h per till until measured. |

### Barcode data

**Audit attempt 2026-11-21:** no real shop data exists yet. The demo installation was audited instead
and cannot answer these questions (see `../quality/barcode-audit-2026-11-21.md`). Q7–Q11 below remain
open, and A10/A11 remain unsigned.

| # | Question | Recommended answer |
|---|---|---|
| Q7 | Which formats does the shop actually use: UPC-E, ITF-14, Code 39, alphanumeric Code 128? | Start with EAN-13/8, UPC-A, numeric Code 128; enable others only after verification. |
| Q8 | What does the USB scanner emit for a UPC-A: 12 digits, or 13 with a leading zero? | Capture 20 real labels before Phase 3; use exact-only until known. |
| Q9 | Are there both 12- and 13-digit records for the same product? | Audit a **copy** of the DB (with permission) for duplicate GTINs; do not modify live data. |
| Q10 | What is the longest real barcode? | Approve the 64-digit cap or raise it with evidence. |
| Q11 | Are one- or two-digit internal codes in use? | Keep the minimum at 1; test ML Kit's ability to decode them. |

### Security and operations

| # | Question | Recommended answer |
|---|---|---|
| Q12 | Is an inbound LAN listener acceptable, with the no-network tests amended? | **Yes**, off by default, Private/LocalSubnet only. |
| Q13 | Is DPAPI-protected key storage acceptable (Windows-specific), or ACL-only? | **DPAPI**, with ACL fallback and a fail-closed path. |
| Q14 | Is one new QR dependency (`segno`) acceptable? | **Yes**; it is pure Python with no transitive dependencies. |
| Q15 | Is `waitress` acceptable if `http.server` fails fuzzing? | **Yes**, as a release-gate contingency. |
| Q16 | Who may pair/revoke: owner only, or any signed-in cashier? | **Owner only** for pairing and revoke; any signed-in cashier may enable the receiver and connect an already-paired phone. **Decided — A14, 2026-11-20.** |
| Q17 | Is process-lifetime dedupe acceptable (no exactly-once across a desktop crash)? | **Yes**, with "delivery uncertain" UX. **Decided — A12, 2026-11-21.** |
| Q18 | What is the shop LAN topology (guest isolation, multiple APs, static DHCP)? | Needed before Phase 6; client isolation must be documented as unsupported. |

### Android and delivery

| # | Question | Recommended answer |
|---|---|---|
| Q19 | Which phone models and Android versions are available? | At least one budget and one mid-range device at API 26+; procure before Phase 3. |
| Q20 | Is there an Android build machine/CI? | A Windows/Linux machine with JDK + Android SDK; local Gradle first, CI later. |
| Q21 | Is Windows 10 still in use, or only Windows 11? | Target Windows 11; test Windows 10 only if a shop has it. |
| Q22 | Who reviews Arabic copy? | A native Arabic speaker; mark uncertain strings `[needs review]` until signed off. |
| Q23 | What is the target POS screen resolution/DPI? | Confirm 1366×768 at 100%/150% before designing the desktop pairing dialog. |
| Q24 | Is targetSdk 36 acceptable for v1, or must the app target 37+? | **36.** Targeting 37 adds `ACCESS_LOCAL_NETWORK` and a runtime permission; revisit only if required. |

---

## 5. Conflicts between agent recommendations — resolved

The Principal Architect did not accept a recommendation merely because agents agreed. The full
reasoning is in `../agents/consolidation-notes.md`.

| Conflict | Agents | Resolution |
|---|---|---|
| Reuse `resolve_scan()` vs exact-only | 1, 3, 4, 6 agree on exact-only; verified independently | **Exact-only** (D9). Digit filtering alone is insufficient; a numeric value can match a name. |
| Read-only/no-shift admission | Agent 1 says refuse; existing UI allows a draft | **Approved mobile admission policy (A9/D17):** Sales active, Full Mode, open shift, no modal, no backup/restore/application transition. Refuse with a typed status; no dispatch. |
| Token generated by desktop vs phone | Agent 1 desktop; Agent 4 phone | **Desktop-generated** (D5): simpler, matches "phone receives credentials"; both are secure over pinned TLS. |
| `format` field | Agent 4 says always `"digits"`; the brief requires format | **Format enum retained** as diagnostic only; the desktop never uses it for lookup. |
| Health endpoint auth | Agent 4 unauth; Principal initially auth | **Unauthenticated minimal health** with `server_instance_id`; no business data; rate-limited. |
| Dedupe TTL | Agent 3 15 min; Agent 4 24 h/50k | **24 h / 50k, fail closed** (D10). |
| Context rotation on remount | Agent 1 rotate; invoice state persists | **Derive from invoice state**; remount does not invalidate a live context (D11/D23). |
| Check-digit validation | Agent 3 add local validation; RawPOS opaque | **No extra check-digit rejection**; ML Kit validates the symbology; avoid mobile-only false negatives. |
| QR dependency | Agent 1 defer/manual; brief requires QR | **`segno` dependency** (D18), approval-gated. |
| DPAPI vs ACL | Agents 1/4 DPAPI; simplicity argues ACL | **DPAPI recommended**, ACL fallback (D12). |
| `http.server` vs production server | Agent 1 stdlib; hardening risk | **Stdlib first, fuzz gate, `waitress` contingency** (D19). |
| Baseline test count | Agent 6 could not reproduce 1,450 | **Measured by Principal:** 1,450 passed / 134.97 s (Python 3.14). Recorded as a measurement, not a CI result. |

---

## 6. Approval checklist

The project does not move to implementation until every line is signed.

The outstanding Phase 2 items (A5, A6, A7, A8, A14, plus the two prerequisites found during preparation)
are briefed for signature in `approval-packet-phase-2.md`. The status values in this table remain
authoritative; the packet only restates them.

| # | Item | Decision | Status |
|---|---|---|---|
| A1 | Project scope, constitution, non-goals | — | ☐ |
| A2 | Additive `CheckoutService.resolve_barcode()` | D9 | **✅ APPROVED — 2026-11-20** |
| A3 | Inbound LAN listener + no-network test amendment | D6/R8 | **✅ APPROVED — 2026-11-20** (port/firewall remains A7) |
| A4 | QR dependency `segno` | D18 | **✅ APPROVED — 2026-11-20** |
| A5 | Pairing approval step + one active device | D3/D16 | **✅ APPROVED — 2026-11-20** (approval tap, 6-digit code, 120 s single-use window, one active device) |
| A6 | DPAPI key/registry storage | D12 | **✅ APPROVED — 2026-11-20** (DPAPI + hash-only registry; one-way ACL fallback) |
| A7 | Port 47653 + Private/LocalSubnet firewall | D6 | **✅ APPROVED — 2026-11-20** (no silent port change on bind failure) |
| A8 | HTTP server hardening path | D19 | **✅ APPROVED — 2026-11-20** (stdlib first; `waitress` pre-approved as the fuzz-gate remedy) |
| A9 | Mobile admission policy: active Sales + full mode + open shift + no modal + no transition | D17 | **✅ APPROVED — 2026-11-20** |
| A10 | Exact-only UPC/EAN behavior | D14/R13 | **✅ APPROVED — 2026-11-21** (exact-only; no normalisation until a pilot audit) |
| A11 | Enabled/disabled barcode formats | D14 | **✅ APPROVED PROVISIONALLY — 2026-11-21** (D14 defaults; pre-pilot verification against real labels is mandatory) |
| A12 | Process-lifetime dedupe / uncertain delivery | D10/D15/D20 | **✅ APPROVED — 2026-11-21** (in-memory dedupe, no auto-replay, no offline queue) |
| A13 | Android platform baseline: Kotlin/Compose, minSdk 26, targetSdk 36, subject to documented compatibility constraints | D21 | **✅ APPROVED — 2026-11-20** |
| A14 | Owner-only pairing/revocation | Q16 | **✅ APPROVED — 2026-11-20** (owner-only for both pairing and revoke) |
| A15 | Phase 1 authorization (Android foundation only) | — | **✅ RECORDED — 2026-11-20** (see note) |
| A16 | RawPOS write authorization on a dedicated branch, including the narrow no-network test amendment | `AGENTS.md` §7 / A3 | **✅ APPROVED — 2026-11-20** (dedicated branch; branch push authorized 2026-11-21; **merge to `main` authorized and performed 2026-11-21**, merge commit `7efa178`) |
| A17 | Dependency locking before Phase 2, or an explicit recorded deferral | `../phase-1/scope-compliance.md` §5.5 | **✅ APPROVED — 2026-11-20** (Gradle locking + exact `segno` pin) |
| A18 | Phase 3 authorization: Android camera and barcode scanning, no network | — | **✅ APPROVED — 2026-11-21** |
| A19 | HTTP/1.1 + JSON over TLS, request/response | D2 | **✅ APPROVED — 2026-11-21** |
| A20 | Desktop-generated token, hash at rest, Keystore on phone | D5 | **✅ APPROVED — 2026-11-21** (retroactive for the shipped desktop half) |
| A21 | Checkout-context token | D11 | **✅ APPROVED — 2026-11-21** |
| A22 | Truthful status ladder | D13 | **✅ APPROVED — 2026-11-21** |
| A23 | `INTERNET` permission + pinned OkHttp | D21 residue | **✅ APPROVED — 2026-11-21** |
| A24 | Phase 4 authorization | — | **✅ APPROVED — 2026-11-21** |
| A25 | Queued GUI-thread bridge with a bounded deadline | D7 | **✅ APPROVED — 2026-11-21** |
| A26 | Additive `CheckoutView.submit_scan()` + shared renderer | D8 | **✅ APPROVED — 2026-11-21** |
| A27 | Remount keeps connection and context | D23 | **✅ APPROVED — 2026-11-21** |
| A28 | Phase 5 authorization | — | **✅ APPROVED — 2026-11-21** |

**A15 reconciliation (2026-11-20).** A15 had remained unchecked while Phase 1 was authorized and delivered.
Its status is **recorded from the existing Phase 1 authorization** (“Android Foundation Only”) and the
delivered evidence in `../phase-1/`, not from a new signature. Phase 2 carries its own separate
authorization line in `approval-packet-phase-2.md` §9.

**Owner approval recorded 2026-11-20:** A2, A3, A4, A5, A6, A7, A8, A9, A13, A14, A16, and A17 by
explicit signature; A15 by reconciliation (see above). Remaining open: **A1** (scope and constitution —
delivered in Phase 0 but never checked, see below), **A10** and **A11** (Phase 3, pending shop barcode
data), and **A12** (Phase 4). A3 does not imply approval of A10/A11; A13 does not imply approval of the
remaining D21 implementation details.

**A1 reconciliation pending.** `implementation-roadmap.md` records Phase 0 as complete and approved, and
Phase 1 was authorized on that basis, but A1 remains unchecked. It is left open rather than self-approved:
the owner should confirm the original approval date so the register and the roadmap agree.

**Phase 2 itself is a separate line** (`approval-packet-phase-2.md` §9). Every Phase 2 prerequisite is
approved, and the phase was **authorized 2026-11-20** and merged 2026-11-21.

**Remaining open items:** **A1** only (reconciliation — needs the owner's Phase 0 approval date). Every
other approval is signed: Phase 2 (A5–A8, A14, A16, A17), Phase 3 (A18), Phase 4 (A12, A19–A24), and
Phase 5 (A10, A25–A28).

---

## 7. Assumptions register

These are **not** verified facts. If one is wrong, the affected decision must be revisited.

| # | Assumption | Impact if wrong |
|---|---|---|
| AS1 | The shop uses EAN-13/EAN-8/UPC-A and numeric internal codes | Format set/config changes |
| AS2 | The phone and desktop can share a private LAN without client isolation | The product cannot work on that network |
| AS3 | A mid-range Android 8+ phone has a usable rear camera | Device matrix/procurement changes |
| AS4 | The shop can open one inbound firewall port on the Private profile | Installation model changes |
| AS5 | One desktop serves one till and one active phone | Multi-device design needed |
| AS6 | The cart is intentionally in memory and lost on crash | Durable-inbox design needed |
| AS7 | The shop accepts a desktop approval tap during pairing | Pairing flow changes |
| AS8 | Barcodes in the DB are the same representation the USB scanner emits | UPC/EAN mismatch work |
| AS9 | ML Kit's bundled model works offline on the chosen devices | Vendor/model choice changes |
| AS10 | The 50 ms/4-key wedge heuristic is adequate for the shop's scanner | Existing behavior (not this project) needs revisiting |
| AS11 | Arabic and English are the only required locales | Localization scope changes |
| AS12 | No tax/VAT is in scope (matches RawPOS today) | Business logic change, out of scope |
