# Consolidation Notes — Six Independent Reviews

**Role:** Principal Architect
**Inputs:** the six independent agent reports under `docs/agents/`, plus the Principal's own repository audit
**Output:** one unified architecture, with every conflict resolved on technical grounds

---

## 1. Process

- Six independent briefs were written from a shared verified-fact pack
  (`shared-facts.md`, derived from HEAD `797167a`).
- Each agent ran through the configured delegate fleet, read-only (`plan` agent), with the lane's
  model/variant:

| Agent | Lane | Model / variant | Result |
|---|---|---|---|
| 1 — Architecture | `review` | `opencode-go/gpt-6-luna` / max | complete |
| 2 — Android | `feature` | `opencode-go/gpt-6-luna` / max | complete |
| 3 — Barcode | `feature` | `opencode-go/gpt-6-luna` / max | complete |
| 4 — Networking/Security | `feature` | `opencode-go/gpt-6-luna` / max | complete |
| 5 — UI/UX | `ui` | `opencode-go/claude-haiku-5-5` / max | complete (resumed once; first run truncated before §8) |
| 6 — QA | `tests` | `opencode-go/claude-haiku-5-5` / max | complete |

- All runs reported `touched files: 0`. No production file was modified.
- The fleet config was read, not changed.
- Each report is preserved verbatim; edits were not made to agent text.

**Limitations:**

- Agent 6 could not see the 13 acceptance criteria (they were in the project brief, not the shared
  pack). The Principal supplied the traceability in `test-matrix.md` §15.
- Agent 6 did not run pytest (read-only brief). The Principal ran it: **1,450 passed / 134.97 s**
  (Python 3.14.3, global interpreter). This is a measurement, not a CI result.
- Agent 5's first run stopped before §8; the same session was resumed for the remainder. The preserved
  report is the concatenation of both parts.
- No agent had an Android device, Android SDK, or a real LAN. All Android/network performance claims
  are proposals.

---

## 2. What each report contributed

| Agent | Strongest contribution | Adopted? |
|---|---|---|
| 1 | Checkout-context token to stop a delayed scan landing in the next invoice; provider-not-cache view resolution; import-architecture test; admission gate reasoning | Yes (D11, D17, D23) |
| 2 | Target SDK 36 (avoid Android 17's new local-network permission), manual `ImageAnalysis.Analyzer`, CameraX 1.6.2 baseline, manual DI, no VIBRATE permission, 2-frame stability, process-death uncertainty | Yes (D21), except the 16-event queue (rejected) |
| 3 | Format decision table; 3-frame stability + re-arm parameters; UPC/EAN leading-zero analysis; test vectors; independent discovery of the numeric name-search hole | Yes (D14, barcode spec) |
| 4 | Protocol schemas, error codes, rate limits, server instance id, pairing approval/SAS, threat model, uncertain-delivery semantics | Yes (protocol/security), with simplifications |
| 5 | Real design audit (not copied from stale 014 docs), token/contrast work, screen states, Arabic key table, desktop pairing UX | Yes (design system/UX), with product-data removed |
| 6 | Test levels, parity testing, no-network amendment design, failure injection, evidence rules, honest "not run" baseline | Yes (QA strategy/matrix) |

---

## 3. Conflicts and resolutions

Agreement between agents was not treated as evidence. Each row was checked against the source or
against a first-principles requirement.

### C1 — Reuse `resolve_scan()` vs exact-barcode-only

- Agents 1, 3, 4, and 6 independently concluded that `resolve_scan()` is unsafe for mobile.
- **Principal verification:** `resolve_scan()` calls `find_by_barcode`, then `search()`; `search()` uses
  `LIKE %token%` on Arabic/English names. A digits-only token can match a name.
- **Resolution:** add an exact-barcode-only `resolve_barcode()` (D9). Digit filtering alone is
  insufficient. This is the one approval-gated business-service change. If refused, mobile product
  scanning does not ship.
- **Rejected alternative:** controller pre-check via `ProductRepository` (business lookup in the
  bridge, double query, race).

### C2 — Read-only / no-shift admission

- Agent 1 (and Agent 4's integration section) recommended refusing scans when the checkout is not
  sellable; Agent 6's matrix assumed it. The Principal initially preferred mirroring USB behavior.
- **Resolution:** adopt a **mobile admission policy** (D17): active Sales + full mode + open shift + no
  transition. Rationale is truthfulness, not a new business rule: the existing UI can add an invisible
  draft line while showing "no shift" or read-only, and the phone would falsely say "added". USB and
  keyboard behavior are unchanged.
- **Trade-off:** mobile and USB diverge in these edge states, deliberately and testably.

### C3 — Who generates the device token

- Agent 1: desktop generates. Agent 4: phone generates and sends it during pairing.
- **Resolution:** desktop generates (D5). Both are secure over pinned TLS; desktop-generated matches
  the requested flow ("phone receives credentials"), keeps pairing state simpler, and lets the desktop
  control rotation. The token is returned once and stored as a hash.

### C4 — `format` field

- Agent 4 proposed `format: "digits"` always, treating format as irrelevant.
- The project brief explicitly requires a barcode format in every event.
- **Resolution:** keep a format enum (`ean_13`, `ean_8`, `upc_a`, `upc_e`, `code_128`, `code_39`,
  `itf`, `qr`, `unknown`) as **diagnostic only**. The desktop never uses it for lookup. This satisfies
  the requirement without letting the phone influence resolution.

### C5 — Health endpoint authentication

- Agent 4: unauthenticated minimal health. Principal initially preferred authenticated.
- **Resolution:** unauthenticated, but only `{v, status, receiver, server_instance_id, server_time}`,
  rate-limited. It is needed for reachability and restart detection before the phone has a context, and
  reveals no business data. The `server_instance_id` is essential for uncertain-delivery handling.

### C6 — Dedupe TTL and capacity

- Agent 3: 15 min / 10k. Agent 4: 24 h / 50k.
- **Resolution:** 24 h / 50k, fail closed with `503 dedupe_capacity` (D10). A busy till can exceed 10k
  in a day; 50k keeps retries safe without unbounded memory. Failure to admit is safer than eviction.

### C7 — Context rotation on language remount

- Agent 1: rotate the context on remount. But a language switch recreates the view, not the invoice.
- **Resolution:** derive the context from invoice state (`invoice_id`, `created_at`, `status`), so it
  survives a view remount and changes only when the invoice changes (D11/D23).

### C8 — Check-digit validation

- Agent 3: add local GTIN check-digit validation for EAN/UPC.
- **Resolution:** no extra rejection. ML Kit validates the symbology; RawPOS treats codes as opaque and
  may contain non-GTIN internal codes. A second, stricter rule would create mobile-only false negatives.
  ML Kit's output is accepted if it is digits and in an enabled format.

### C9 — QR generation dependency

- Agent 1: defer QR, use manual host/code. Agent 5: QR is central. The brief requires QR pairing.
- **Resolution:** add `segno` (pure Python, zero transitive dependencies) as the one approved desktop
  dependency (D18). Manual initial pairing is rejected: a short code cannot carry the endpoint, pin, and
  secret safely; "edit address" exists only for an already-paired desktop.

### C10 — DPAPI vs ACL

- Agents 1 and 4 recommend DPAPI; simplicity argues for ACL-only.
- **Resolution:** DPAPI for the TLS private key and registry, with an ACL-only fallback and a visible
  warning. Fail closed on unprotect failure. No new dependency (`ctypes`).

### C11 — `http.server` vs a production server

- Agent 1: stdlib. Security review risk: `http.server` is not a hardened production server.
- **Resolution:** stdlib first with a hardened handler, bounded threads, strict limits, and a Phase 7
  fuzz gate. If fuzzing fails, adopt `waitress` (pure Python, zero transitive deps) as a contingency.
  This is a release gate, not an intention (D19).

### C12 — Baseline test count

- Agent 6 could not confirm 1,450 statically (1,190 `def test_` definitions; parametrization accounts
  for the rest) and correctly refused to claim it.
- **Resolution:** the Principal ran the suite: 1,450 passed / 134.97 s. Recorded with interpreter and
  date. The project `.venv` lacks pytest; the global Python 3.14 was used. This is not a 3.12 CI result.

### C13 — Product data on the phone

- Agent 5's copy initially showed product names and quantities (`Added {name}`, `now {qty}`).
- **Resolution:** removed. The protocol returns no product data. The phone shows the barcode and a
  status (`Added`, `Quantity increased`). This keeps the boundary and the privacy promise.

### C14 — Receiver lifecycle vs pairing dialog

- Agent 5 suggested the receiver runs only while a phone is paired or the dialog is open.
- **Resolution:** the receiver is enabled/disabled explicitly and runs until sign-out/recovery/shutdown;
  the dialog manages pairing only. This avoids a connection dropping when the dialog closes.

### C15 — Portrait lock

- Agent 5 recommends portrait-only; the Principal prefers portrait-first without a hard lock.
- **Resolution:** portrait-first, rotation supported with a camera rebind and duplicate tests. Revisit
  if field testing shows a duplicate risk.

### C16 — Default port

- Agent 4 proposed 8765; the Principal chose 47653.
- **Resolution:** 47653 (private/dynamic range, less likely to collide with common dev servers),
  configurable. The QR and firewall rule always show the actual port; there is no silent random port.

### C17 — Self-signed trust anchor

- The first drafts said "use OkHttp `CertificatePinner`". That is incomplete: pinning does not make a
  self-signed leaf trusted, so the handshake fails before pinning is evaluated.
- **Resolution:** use a pin-only `X509TrustManager` that accepts exactly the leaf whose SPKI hash
  matches the QR pin and checks validity; keep standard hostname verification. `CertificatePinner` is
  optional as a second check. Do not store a one-certificate KeyStore anchor, because same-key reissue
  changes the DER and would be rejected.
- This is standard certificate pinning, not custom cryptography, and it is covered by the TLS matrix.

### C18 — Frame rate and stability window

- Agent 2: 8–10 fps, two consecutive frames, latch until absent ~500 ms. Agent 3: 10–12 fps, three
  frames, 300 ms cooldown, 300 ms re-arm.
- **Resolution:** 10 fps (8–12 tuning range), **two consecutive matching frames**, 300 ms cooldown,
  re-arm after **500 ms absence**. ML Kit already validates the symbology; two identical decodes plus a
  longer absence is fast and safe. Three frames is the conservative fallback if field testing shows
  false reads. This changes the stabilization budget to p50 100 ms / p95 200 ms.

### C19 — In-flight queue

- Agent 2 proposed a bounded 16-event FIFO; Agents 3/4 and the Principal use one in-flight event.
- **Resolution:** one in-flight, no queue (D15). A queue hides failures, adds latency, and can replay
  stale scans. If throughput later demands it, add a *visible* queue with per-event status, not a
  silent one.

### C20 — Target SDK and the Android 17 local-network permission

- Agent 2: target/compile SDK 36; Android 17 requires `ACCESS_LOCAL_NETWORK` for apps targeting 37+.
- **Resolution:** adopt **targetSdk 36** for v1, with CAMERA + INTERNET only. Target 37 is a future
  decision that adds a runtime permission; record it as a risk and an approval item. Verify the SDK
  behavior at Phase 1 because the platform/toolchain moves.

### C21 — CameraX analyzer

- Agent 2: manual `ImageAnalysis.Analyzer`, no `camera-mlkit-vision`. The Principal's draft left it
  open; Agent 3 assumed CameraX+ML Kit.
- **Resolution:** manual analyzer (explicit backpressure, proxy lifetime, throttling); add
  `camera-mlkit-vision` only if a coordinate-mapped overlay becomes a requirement.

### C22 — VIBRATE permission

- The Principal's draft declared VIBRATE; Agent 2 omits it and uses platform/Compose haptics.
- **Resolution:** omit VIBRATE in v1. Use `LocalHapticFeedback`/`HapticFeedbackConstants`; add VIBRATE
  only if custom multi-pulse patterns are proven necessary.

### C23 — Auto-zoom

- Agent 2 suggests ML Kit zoom suggestions with a cap; Agent 3 says optional and measure; the Principal
  prefers user-controlled zoom.
- **Resolution:** user pinch/slider only in v1; no automatic zoom. Evaluate ML Kit suggestions in
  Phase 6 as a tuning experiment, off by default.

### C24 — Compose toolchain pinning

- Agent 2 notes current Compose releases may require compileSdk 37/AGP 9, which conflicts with the
  SDK 36 baseline.
- **Resolution:** pin the newest stable Compose BOM whose resolved artifacts support SDK 36; record the
  mapping in Phase 1. Do not chase the newest BOM at the cost of the permission boundary.

---

## 4. Agent recommendations rejected (with reasons)

| Recommendation | Source | Why rejected |
|---|---|---|
| Bounded 16-event FIFO | 2 | Hides failures, adds latency, can replay stale scans; one in-flight is truthful |
| Automatic ML Kit zoom | 2 | Unexpected field-of-view changes confuse the cashier; user-controlled zoom in v1 |
| Target SDK 37 | 2 (warning) | Adds `ACCESS_LOCAL_NETWORK`; v1 stays on 36 |
| Synthetic `QKeyEvent` injection | considered by 1 | Focus/payment risk, indistinguishable from USB, cannot return a truthful result |
| Direct `resolve_scan()` from the receiver thread | considered by 1 | Unsafe shared mutable draft and SQLCipher connection |
| Value/time-based server dedupe | considered by 3 | Blocks legitimate double scans; event-id dedupe is correct |
| Phone-generated token | 4 | Viable but more pairing state; desktop-generated is simpler and matches the flow |
| `format: "digits"` only | 4 | Violates the explicit format requirement; enum is diagnostic-only instead |
| Local GTIN check-digit rejection | 3 | Mobile-only false negatives against opaque RawPOS data |
| QR disabled entirely | 3 (product scanning) | Correct for product mode; QR is still required for pairing mode |
| Manual initial pairing code | 5 | Cannot carry pin/secret safely; QR-only |
| Product names/quantities on the phone | 5 | Leaks business data; violates the boundary |
| Receiver tied to dialog lifetime | 5 | Drops the connection; explicit enable/disable is better |
| `waitress` immediately | considered by 1/4 | Defer until fuzzing proves `http.server` insufficient |
| Trust-on-first-use | considered by 4 | MITM; pin from QR is required |
| Durable exactly-once across crash | 4 (as an option) | Requires a schema/lifecycle redesign; out of scope |

---

## 5. What the Principal verified independently

| Claim | Method | Result |
|---|---|---|
| HEAD, branch, clean tree | `git` | `797167a`, `main`, 0 changes |
| Full suite | `py -3 -m pytest -q` | 1,450 passed / 134.97 s |
| `.venv` cannot run tests | direct invocation | no pytest |
| `resolve_scan` name fallback | source read | `find_by_barcode` → `search` |
| Barcodes digits-only | migration + service | CHECK GLOB + `_clean_barcode` |
| Checkout has no locks | source read | confirmed |
| Lazy module mount + remount | source read | `_activate`, `_remount_active` |
| No network code | source + tests | confirmed |
| Contrast ratios | WCAG formula over tokens | matches Agent 5; border 1.45/1.42 fails 3:1 |
| Catalog parity | import `_CATALOGS` | 655 keys each, no missing keys |
| No Android tooling | environment check | no JDK/SDK/emulator |

---

## 6. Residual disagreements and open risks

1. **Admission policy (D17)** changes mobile behavior relative to USB in no-shift/read-only states. It
   is recommended for truthfulness, but the owner may prefer exact USB parity. Either choice is
   testable; the wrong choice is to claim "added" when the desktop cannot show the line.
2. **DPAPI** adds Windows-specific code. The fallback is ACL-only; the owner must choose.
3. **`http.server`** may fail fuzzing. The contingency (`waitress`) is a new dependency and must be
   pre-approved in principle.
4. **One active device** may be too restrictive for a shop with two phones at one till. It is the
   simplest truthful model; multi-device needs ordering and UI work.
5. **UPC-A representation** cannot be resolved without real shop data. Exact-only is the safe default;
   a data audit is required before pilot.
6. **No exactly-once across a desktop crash.** Accepted; the UX says "check the desktop".
7. **Android performance** is entirely unmeasured. The first real numbers come in Phase 3.

---

## 7. How the unified architecture was produced

1. Verified the repository facts myself; did not rely on the fact pack alone.
2. Read all six reports; extract every decision, assumption, and conflict.
3. Re-verified the high-severity claims (name search, thread safety, barcode rule, no-network tests).
4. Chose one owner for each concern: architecture (1), Android (2), barcode (3), protocol/security (4),
   design (5), QA (6), with the Principal resolving cross-cutting conflicts.
5. Wrote the consolidated documents so that each decision has a rationale, an alternative, and a
   consequence; removed contradictions found in a final consistency pass.
6. Kept the agent reports verbatim as evidence of the independent review.

**Final rule applied:** a recommendation is accepted only if it survives the repository facts, the
constitution, and a failure-mode analysis. Popularity among agents is not a criterion.
