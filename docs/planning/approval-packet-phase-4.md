# Phase 4 Approval Packet — scan protocol and acknowledgment

**Status:** prepared 2026-11-21 for owner signature. Nothing here is approved until §5 is signed.
**Purpose:** Phase 4 implements four decisions that are still PROPOSED and adds two capabilities nobody
has approved. This packet briefs all six so the phase can start on signed rules instead of assumptions.
**Authority:** `risks-and-decisions.md` remains the approval register; this packet restates, it does not
change status.
**Related:** `../architecture/networking-protocol.md`, `../architecture/desktop-integration.md`,
`implementation-roadmap.md` Phase 4.

---

## 1. A19 — the wire format (D2)

**Decision:** one authenticated `POST /v1/scans` per scan; the response arrives after business
processing; plus `GET /v1/context`. HTTP/1.1 and JSON over TLS.

**Why now:** Phase 4 is the first phase where the phone and the desktop must agree byte for byte. The
contract has to be approved before both sides implement it, or they will drift.

**What you accept:** no desktop→phone push in v1. A cashier sees a result only in response to a scan.
That is fine for a scanner; it is not a chat protocol.

**Rejected:** WebSocket (adds state without a v1 need), raw TCP (custom framing), long-poll (latency and
state).

**If refused:** there is no transport, and Phase 4 cannot start.

---

## 2. A20 — device token (D5)

**Decision:** the desktop generates a 256-bit token, returns it once over pinned TLS, stores only a
domain-separated SHA-256 verifier, and the phone stores it with Android Keystore AES-GCM under backup
disabled.

**Why now:** **the desktop half already exists.** Phase 2 implemented and tested exactly this. Approving
it now makes the register honest about shipped behaviour; the Android half lands in Phase 4.

**What you accept:** re-pairing rotates the token, and a lost Keystore entry means re-pairing. A token is
revocable and scan-only.

**Rejected:** phone-generated token (more pairing state, same security), password (weaker), client
certificate (heavier provisioning).

**If refused:** the already-shipped Phase 2 registry would be operating under an unapproved decision, and
the phone would have no credential scheme to implement.

---

## 3. A21 — checkout context (D11)

**Decision:** an opaque token derived from the current invoice (`invoice_id`, `created_at`, `status`)
travels with every scan. A mismatch returns `rejected_stale_context` with no mutation.

**Why now:** this is the only defence against a delayed scan landing in the **next** invoice after a
finalize, hold, or resume. Phase 4 defines the token; Phase 5 enforces it at dispatch.

**What you accept:** one scan may be rejected after an invoice change, and the cashier rescans. That is
deliberate: a lost scan is recoverable, a line on the wrong customer's invoice is not.

**Rejected:** event age only (clock skew, still crosses invoices), no context (wrong-sale risk), rotate on
view remount (a language switch would invalidate a live context).

**If refused:** a scan captured before finalize can land in the next invoice. That is a money-correctness
defect, not a UX one.

---

## 4. A22 — truthful status ladder (D13)

**Decision:** `added` and `merged` are shown **only** after the desktop confirms the cart mutation.
Transport receipt, authentication, validation, dispatch, and resolution are separate internal stages and
are never presented as success.

**Why now:** Phase 4 renders the statuses. There is no later phase that can retrofit truthfulness.

**What you accept:** the phone shows "Waiting for desktop" and "Delivery uncertain" states, and a slow
desktop looks slow rather than done. Optimistic UI would feel faster and would lie.

**Rejected:** optimistic success (a network ack is not a sale), a generic "sent" (ambiguous).

**If refused:** the phone will claim success it cannot know, which defeats the product's premise.

---

## 5. A23 — the two enablers

Both were **explicitly excluded** from A13, so neither is approved by anything signed so far.

| Enabler | Why it is needed | Cost |
|---|---|---|
| `INTERNET` permission | an HTTP client cannot exist without it | the phone can now make outbound connections — scoped by code to the paired desktop only |
| OkHttp (pinned) | TLS with a pin-only trust manager, timeouts, connection reuse | one new dependency, plus its transitive tree |

**What you accept:** the app gains a network capability and a dependency. The offline guarantee is
preserved in behaviour (no cloud, no telemetry, LAN only) but it is no longer provable by the absence of a
permission — it becomes a property that must be tested.

**Rejected:** `HttpURLConnection` (no clean pin-only trust manager, awkward timeouts), Ktor (larger
tree), a hand-written TLS client (custom crypto-adjacent code, forbidden).

**If refused:** Phase 4 cannot send anything. The only alternative architecture is the deferred durable
transport, which is larger and does not remove the need for a client.

---

## 6. A24 — Phase 4 authorization

Scope, once the five items above are signed:

- **Android:** `ScanClient` (pin-only TLS, timeouts, one in-flight, retry with the same event id),
  `ContextClient`, event identity and persistence, status rendering, "delivery uncertain" path.
- **Desktop:** `POST /v1/scans`, `GET /v1/context`, the in-memory dedupe table approved as A12, context
  derivation.
- **Shared:** contract fixtures and a SHA-256 manifest checked by both builds.

**Not in scope:** `CheckoutService.resolve_barcode()`, any `CheckoutView` change, admission rules, and
any dispatch into the cart. Those are Phase 5 and remain unauthorized. Phase 4 delivers a tested
transport that stops at a fake dispatcher.

---

## 7. Verification plan

| Layer | What it proves |
|---|---|
| Contract | every request and response fixture parses on both sides; the manifest matches |
| Desktop | reserve-before-dispatch, concurrent same id, conflict, TTL, capacity, context mismatch, instance id, rate limit |
| Android | retry reuses the event id, no new id, context refresh, uncertain state, no optimistic success |
| End to end | loopback: phone fake client → receiver → fake dispatcher; add / not found / reject / duplicate / uncertain |

---

## 8. Signature block

| # | Item | Decision | Owner | Date |
|---|---|---|---|---|
| A19 | HTTP/1.1 + JSON over TLS, request/response | D2 | | |
| A20 | Desktop-generated token, hash at rest, Keystore | D5 | | |
| A21 | Checkout-context token | D11 | | |
| A22 | Truthful status ladder | D13 | | |
| A23 | `INTERNET` permission + OkHttp | D21 residue | | |
| A24 | Phase 4 authorization | — | | |

**Not decided here:** A1 (Phase 0 reconciliation) and A10 (exact-only UPC/EAN, Phase 5).
