# Barcode Scanning Specification

**Status:** proposed — pending approval of the barcode-only desktop entry point (D9)
**Applies to:** Android capture, validation, duplicate suppression, and the contract with the desktop
**Related:** `repository-audit.md` §4, `networking-protocol.md`, `../planning/risks-and-decisions.md`

---

## 1. The constraint that shapes everything

RawPOS stores barcodes as **opaque ASCII digit strings**:

- `product_barcode.barcode TEXT NOT NULL UNIQUE`
- `CHECK (barcode <> '' AND barcode NOT GLOB '*[^0-9]*')`
- no length limit, no check-digit logic, no format column, no UPC/EAN equivalence
- leading zeros are significant

And `CheckoutService.resolve_scan()` does this:

```
exact barcode lookup → if none, product name search → first match wins
```

**Consequence:** a mobile scan must never use the name-search fallback. Even a digits-only value can
match a product name such as `"Carton 1000"` for a scan of `"1000"`. Filtering to digits is necessary
but **not sufficient**. The mobile path needs an exact-barcode-only entry point on the desktop (D9).

If that entry point is not approved, the mobile product scanner cannot ship safely. A pre-check
alternative exists (see §4.2) but is inferior.

---

## 2. Format support decision

ML Kit supports many formats. RawPOS supports exactly one payload shape: digits. Enable only what can
produce a valid RawPOS barcode, and disable the rest by default.

| Format | ML Kit constant | Payload | RawPOS today | v1 decision | Reason |
|---|---|---|---|---|---|
| EAN-13 | `FORMAT_EAN_13` | 13 digits | Exact match | **Enabled** | Standard retail; numeric |
| EAN-8 | `FORMAT_EAN_8` | 8 digits | Exact match | **Enabled** | Standard retail; numeric |
| UPC-A | `FORMAT_UPC_A` | 12 digits | Exact match | **Enabled** | Standard retail; numeric |
| UPC-E | `FORMAT_UPC_E` | 8 digits compressed | Exact match only | **Disabled by default** | Representation varies by scanner (compressed vs expanded); enable only after the shop's USB output is verified |
| Code 128 | `FORMAT_CODE_128` | variable ASCII | Numeric payloads only | **Enabled, numeric payloads only** | Common for internal labels; alphanumeric payloads are rejected |
| Code 39 | `FORMAT_CODE_39` | alphanumeric | Digits only | **Disabled by default** | No evidence of use; optional checksum and broad charset add ambiguity |
| ITF | `FORMAT_ITF` | even-length digits | Exact match | **Disabled by default** | ITF-14 carton labels only; enable if the shop uses them |
| QR Code | `FORMAT_QR_CODE` | arbitrary text | Digits only | **Disabled for product scanning** | QR usually carries URLs/structured data; enabled **only** in the pairing scanner mode |

### 2.1 Two camera modes, two format sets

The app has two distinct scanning modes:

- **Pairing mode** — QR only. Used once per device, from the Connection screen.
- **Product mode** — the enabled retail formats above. QR is not in the format set.

This prevents a customer's QR code, a URL, or a shelf label from ever entering the product pipeline,
while still allowing QR pairing. The mode is explicit in the UI and cannot be changed by a scanned
payload.

### 2.2 Format is diagnostic, not authority

The event carries the ML Kit format for diagnostics and support. It does not change desktop behavior:
the desktop always performs an exact digit-string lookup. The phone never "converts" a symbology.

---

## 3. Validation predicate

Applied **before** any network call, and re-applied on the desktop:

```
rawValue != null
rawValue matches ^[0-9]{1,64}$          (ASCII digits only, full-string)
format ∈ enabled set for the current mode
```

Rules:

- **No trimming.** A payload with whitespace is rejected, not cleaned.
- **No Unicode normalization.** Arabic-Indic or full-width digits are rejected, not converted.
- **No integer parsing.** Leading zeros are preserved exactly; never use `toInt()`.
- **No zero-stripping or padding.** `"0123"`, `"123"`, and `"00123"` are different barcodes.
- **No check-digit rejection in v1.** ML Kit already validates the symbology checksum. RawPOS treats
  codes as opaque and may legitimately contain non-GTIN internal codes printed in a retail symbology.
  Adding a second, stricter rule would create mobile-only false negatives. (This resolves a conflict
  with Agent 3's recommendation in favour of compatibility; see `../agents/consolidation-notes.md`.)
- **Maximum 64 digits** is a v1 safety cap, not a RawPOS rule. It needs approval and a shop data audit.
- **Minimum 1 digit.** RawPOS accepts short internal codes; the desktop's 4-key wedge threshold is a
  keyboard heuristic and must not be copied into the data rule. ML Kit may not decode a one-character
  1D symbol; if it does, the app accepts it.

### 3.1 Rejection messages

| Condition | Phone message (English) | Arabic key | Network call? |
|---|---|---|---|
| Empty / null | No barcode detected | `mobile.scan.no_barcode` | no |
| Whitespace or control chars | Unsupported barcode | `mobile.scan.unsupported` | no |
| Non-ASCII digits | Use a numeric product barcode | `mobile.scan.digits_only` | no |
| Alphanumeric payload | Only numeric product barcodes are supported | `mobile.scan.digits_only` | no |
| > 64 digits | Barcode too long | `mobile.scan.too_long` | no |
| Disabled format | Barcode type not enabled | `mobile.scan.format_disabled` | no |
| Looks like a URL | Not a supported product barcode | `mobile.scan.not_product` | no |
| Two codes in frame | Frame one barcode | `mobile.scan.one_at_a_time` | no |
| Unknown exact code | Barcode not found | `mobile.scan.not_found` | yes (desktop decides) |
| Inactive product | Product is inactive | `mobile.scan.inactive` | yes (desktop decides) |

The last two are decided by the desktop, not the phone. The phone must not guess.

---

## 4. Desktop contract for barcode-only resolution

### 4.1 Recommended: an additive `resolve_barcode`

```
CheckoutService.resolve_barcode(text: str) -> ScanResult
```

Semantics:

- strip? **No.** The mobile path already rejects whitespace; the method validates and rejects anything
  that is not `^[0-9]{1,64}$` as `NotFound` (or a typed invalid result).
- exact `find_by_barcode` only; **no** `search()` fallback;
- inactive → `Deactivated`;
- otherwise `_ensure_started()` + `_add_or_merge(...)` exactly as today;
- returns the same `Matched` / `NotFound` / `Deactivated` union.

Implementation shape: extract the common tail of `resolve_scan` into a private helper with an
`allow_search: bool` parameter. `resolve_scan` keeps `allow_search=True` and its observable behavior is
unchanged; `resolve_barcode` uses `False`. Both are covered by tests, including a test proving
`resolve_scan` still searches and `resolve_barcode` never does.

**This is a change to an existing business service and therefore requires explicit approval**
(Constitution Article XV). It is additive, behavior-preserving for existing callers, and has no schema
impact. If approved, it is the only service change in the project.

### 4.2 Rejected alternative: pre-check in the controller

The controller could call `ProductRepository.find_by_barcode()` on the GUI thread and only dispatch if
an exact match exists, then call `resolve_scan()`. Because both happen on the same thread against the
same connection with no intervening write, the fallback would not fire.

Why reject it:

- it puts product lookup in the transport bridge, violating separation;
- it performs two lookups per scan;
- it duplicates the "is it inactive?" decision or risks a race with product edits;
- it is harder to test and to explain than a named service method.

It remains a fallback if the owner refuses the service change — but then the mobile scanner should not
ship, because the alternative weakens the architecture for no business benefit.

---

## 5. Leading zeros and the UPC-A / EAN-13 ambiguity

This is a headline interoperability decision.

Facts:

- ML Kit can report a UPC-A symbol as `FORMAT_UPC_A` with 12 digits, or as `FORMAT_EAN_13` with a
  leading zero, depending on version/device behavior.
- RawPOS stores whatever the USB scanner sent. A shop may have `036000291452` (12) for one product and
  `0036000291452` (13) for another, or only one representation.
- There is no format column, so the desktop cannot know whether a 13-digit zero-prefixed value is a
  UPC-A or a genuine EAN-13.

Decision for v1: **exact-only**. The phone sends the raw digits it decoded; the desktop looks up that
exact string. No padding, stripping, or alias lookup.

Consequences:

- Safe failure: a representation mismatch yields `not_found`, not a wrong product.
- The shop may see "not found" for a UPC-A item stored in the other representation. The fix is a data
  normalization decision (store one canonical form) or a later, audited alias policy — not a silent
  phone-side guess.
- Before pilot, capture the USB scanner's output and the phone's ML Kit output for the same physical
  labels. If they differ, resolve it as shop data or an explicit setting, not as hidden magic.

Alternatives rejected for v1:

- **Dual lookup** (try 12-digit and 13-digit forms) can alias arbitrary internal codes and add a product
  the USB scanner would not have added.
- **Phone-side normalization** hides the mismatch and makes the phone authoritative about symbology.
- **Blind data migration** of 12→13 digits can collide with existing 13-digit barcodes.

---

## 6. Duplicate suppression

Three layers, each with a distinct job. The desktop is authoritative for mutation.

```
Camera frames ──► candidate stabilizer ──► event id ──► desktop idempotency
   (layer 1)          (layer 2)             (layer 2)        (layer 3)
```

### 6.1 Layer 1 — camera/candidate stabilizer (phone)

State machine:

```
PAUSED ──resume──► ARMED
ARMED ──candidate seen──► STABILIZING
STABILIZING ──2 matching frames──► SUBMITTING
STABILIZING ──different/absent frame──► ARMED (reset streak)
SUBMITTING ──event created──► WAITING_FOR_ACK (latch value)
WAITING_FOR_ACK ──result──► RESULT
RESULT ──value absent ≥500 ms──► WAIT_FOR_ABSENCE
WAIT_FOR_ABSENCE ──cooldown elapsed──► ARMED
```

Parameters (proposed, tunable in Phase 6):

| Parameter | Default | Rationale |
|---|---|---|
| Analysis rate | 10 fps (range 8–12) | Balances latency, CPU, battery |
| Stability frames | 2 consecutive | Suppresses transient misdecodes; ML Kit already validates the symbology |
| Same-value cooldown after submit | 300 ms | Prevents immediate re-fire |
| Re-arm absence | ≥500 ms **and** the value absent from the target | Requires the item to leave the target |
| Global minimum interval | 150 ms | Prevents machine-gun events |
| In-flight limit | 1 | No silent queueing; honest waiting state |
| First-submission staleness | 10 s | A frame captured 10 s ago is not a new scan |
| Manual re-arm | optional button after a result | Accessibility/ergonomics fallback |

A legitimate double-scan of the same item requires removing it from the target for the re-arm interval
and presenting it again. That creates a **new** event id, and RawPOS merges it into the existing line
(quantity +1). A code held continuously in view fires once.

Two distinct symbols in the target region in one frame: submit neither; show "Frame one barcode".

### 6.2 Layer 2 — event identity (phone)

- One UUIDv7 `event_id` per accepted physical scan.
- Persisted with the pending event before the first send, so a process restart can retry the same id.
- Retries reuse the same id and an identical payload.
- A new event id is created only for a new physical presentation, never for a retry.
- No offline queue of old scans. A scan that cannot be sent within its 10 s freshness window is
  discarded with "Scan expired — scan again".

### 6.3 Layer 3 — desktop idempotency (authoritative)

- Key: `(device_id, event_id)`.
- The entry is **reserved before** GUI dispatch, so concurrent retries cannot dispatch twice.
- States: `in_flight`, `completed(outcome)`, `conflict`.
- Same key + same payload:
  - in flight → `202 processing`;
  - completed → the original status, plus `duplicate: true`.
- Same key + different payload → `409 event_id_conflict`, never dispatched.
- TTL 24 h, bounded at 50,000 entries. When full, fail closed with `503 dedupe_capacity`; never evict a
  live entry to make room.
- The desktop does **not** dedupe by barcode value or a time window. Two intentional scans of the same
  product are two units, and `_add_or_merge` merges them into one line with quantity 2.

### 6.4 Why both client and server dedupe

The client stops the camera from generating an event per frame. The server stops a retried event from
mutating the cart twice. Neither alone is sufficient: a client bug must not corrupt the cart, and a
server-only design would flood the network with duplicate requests.

---

## 7. Continuous scanning contract

| State | Camera | Network | UI |
|---|---|---|---|
| Scanning | on, analyzing | ready | live preview, `Scanning` |
| Waiting for desktop | on, candidate latched | one request in flight | `Waiting for desktop` |
| Result shown | on, latched | idle | status + feedback, `Scan again` available |
| Paused | off/analysis stopped | idle | `Paused`, no capture |
| Background/locked | off | idle or dropped | no invisible scanning |
| Disconnected | on (preview) | none | `Disconnected`, no queueing |
| Desktop rejected | on | idle | typed rejection, no success feedback |

No scanning while the user cannot see feedback. No queueing of scans while disconnected. No silent
retry that could add an item the cashier did not see.

---

## 8. Camera configuration

| Concern | v1 setting | Notes |
|---|---|---|
| Camera | Rear, `DEFAULT_BACK_CAMERA` | Front camera is not a product scanner |
| Preview + analysis | CameraX `Preview` + `ImageAnalysis` | Lifecycle-bound |
| Analysis resolution | 1280×720 target | Avoid multi-megapixel frames; ML Kit does not need them |
| Format | `YUV_420_888` | ML Kit `InputImage.fromMediaImage` |
| Backpressure | `STRATEGY_KEEP_ONLY_LATEST` | Drop stale frames |
| Frame rate | ~10 fps analysis | Preview can run faster |
| Rotation | Pass `ImageProxy.imageInfo.rotationDegrees` | Prevents rotated-code failures |
| Focus | Continuous autofocus; tap-to-focus | CameraX focus/metering |
| Zoom | Pinch + optional slider; no surprise auto-zoom | User control |
| Torch | Off by default, user toggle | Glare caveat |
| ROI | Center guide; filter by bounding-box center | No physical crop in v1 |
| Multiple codes | Reject the frame if >1 eligible in ROI | Avoid wrong item |
| Low light / blur | Prompt steady/close; offer torch | No guessing at partial digits |
| Glare/curved | Prompt tilt/flatten | No OCR fallback |

Every `ImageProxy` must be closed exactly once, including on analysis error and lifecycle teardown.

---

## 9. Latency budget (proposed, not measured)

| Stage | p50 | p95 | Measured by |
|---|---:|---:|---|
| Exposure → analyzable frame | 60 ms | 120 ms | Camera timestamp → analyzer |
| ML Kit inference | 25 ms | 60 ms | Around `process()` |
| 2-frame stabilization at 10 fps | 100 ms | 200 ms | First → second matching frame |
| Validation + event creation | 2 ms | 5 ms | Candidate → request ready |
| LAN round trip | 40 ms | 150 ms | Phone monotonic clock |
| Desktop receiver → GUI dispatch | 10 ms | 40 ms | Receiver → GUI slot |
| Exact lookup + draft mutation | 20 ms | 100 ms | Service entry → result |
| Render + response | 20 ms | 50 ms | UI update → response sent |
| **End to end** | **≤ 400 ms** | **≤ 800 ms** | Monotonic clocks, correlated by event id |

Stretch target: p95 ≤ 600 ms. Do not measure by subtracting phone and PC wall clocks; they are not
synchronized. Record per-stage durations on each side and correlate by event id.

---

## 10. Feedback

| Outcome | Visual | Audio | Haptic |
|---|---|---|---|
| Detected, sending | bounding box + spinner | none | none |
| Added | green check + "Added" | short high tone | light pulse |
| Merged (quantity +1) | green check + "Quantity increased" | same tone | same pulse |
| Not found | amber + code shown | low double tone | double pulse |
| Inactive | red + "Product inactive" | low two-tone | double pulse |
| Rejected (no checkout / unavailable) | red + reason | alert tone | double pulse |
| Network failure / uncertain | amber + "Check desktop" | alert tone | double pulse |
| Duplicate replay | neutral + "Already confirmed" | none | none |

Rules:

- Success feedback fires only after the desktop returns `added` or `merged`.
- A transport 200 with `processing` is not success.
- Respect system volume, Do Not Disturb, and the app's mute/vibration settings.
- Never use sound as the only success signal (noisy shops, deaf users).
- Never use color as the only status signal.

---

## 11. Test vectors

| # | Input | Expected phone behavior | Expected desktop behavior |
|---|---|---|---|
| 1 | EAN-13 `4006381333931` | accept, one event | exact lookup; add once |
| 2 | EAN-8 `96385074` | accept | exact lookup |
| 3 | UPC-A `036000291452` | accept 12 digits | exact 12-digit lookup |
| 4 | EAN-13 `0036000291452` | accept 13 digits | exact 13-digit lookup; no alias |
| 5 | UPC-E `04252614` | disabled; no request | no mutation |
| 6 | ITF-14 `10012345000017` | disabled; no request | no mutation |
| 7 | Code 128 numeric `0042` | accept; preserve zeros | exact lookup |
| 8 | Code 128 numeric `7` | predicate accepts; ML Kit may not decode | exact lookup if sent |
| 9 | Code 128 `AB123` | reject locally | no request |
| 10 | Code 39 `123456` | format disabled | no request |
| 11 | QR URL | product mode rejects; pairing mode only | no request |
| 12 | QR digits `12345678` | product mode rejects | no request |
| 13 | EAN-13 bad check digit `4006381333930` | accept only if ML Kit returns it; no extra check | exact lookup |
| 14 | Empty | reject | no request |
| 15 | `" 0042 "` | reject; no trim | no request |
| 16 | `"12\n34"` | reject | no request |
| 17 | 65 digits | reject | no request |
| 18 | Arabic-Indic `١٢٣٤` | reject | no request |
| 19 | Same code in 30 frames | one event | one increment |
| 20 | Re-present before re-arm | suppressed | no second increment |
| 21 | Re-present after 500 ms absence | new event | quantity +1 |
| 22 | Two codes in one frame | no event; prompt | no mutation |
| 23 | Retry same event id | retry | cached result; one increment |
| 24 | Unknown numeric `999999` | send once; not found | exact lookup only |
| 25 | Numeric value matching a product name (`1000` vs "Carton 1000") | send once | **must not** match by name; not found |

Vector 25 is the acceptance test for D9.

---

## 12. Risks

| Risk | Mitigation |
|---|---|
| `resolve_barcode` not approved | Do not ship product scanning; use the pre-check fallback only with written acceptance of its weaknesses |
| UPC-A/EAN-13 representation mismatch | Pre-pilot capture of USB vs phone output; exact-only; shop data normalization decision |
| ML Kit behavior changes | Pin the library/model; golden tests on target devices |
| Low-end camera performance | 720p, 10 fps, 2-frame stability; tune in Phase 6 |
| Glare/damage | UX prompts; no OCR guessing |
| Duplicate cart lines | Three-layer dedupe; vector 23; server idempotency |
| Digit-only name collision | Barcode-only service path; vector 25 |
| Short internal codes | Minimum 1 digit; test actual labels |
| High-volume lane | Hardware scanner remains the better tool; phone is auxiliary |

## 13. Open questions

1. Approve the additive `CheckoutService.resolve_barcode()` (exact-only) — required.
2. Confirm the 1–64 digit envelope, or provide the real maximum from shop data.
3. Are UPC-E, ITF-14, or alphanumeric Code 39 actually used by this shop?
4. What does the shop's USB scanner emit for UPC-A/EAN-13/UPC-E? Are both 12- and 13-digit records present?
5. Is a 500 ms absence + 2-frame re-arm acceptable for two identical units, or is a manual "scan again"
   control required?
6. Should the phone show the barcode value after a result? (Recommended: yes, digits only, no product name.)
