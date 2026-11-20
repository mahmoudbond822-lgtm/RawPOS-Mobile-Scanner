# Barcode Scanning Specification — Agent 3

**Recommended v1:** enable EAN-13, EAN-8, UPC-A and numeric-only Code 128; accept only exact ASCII-digit payloads; use exact barcode lookup only. Never pass non-digit data to `resolve_scan()`. Because `resolve_scan()` also searches product names, the desktop must expose a barcode-only path for mobile scans—even digits-only input can otherwise fall through to name search.

The defaults below are **proposals**, not measurements. RawPOS facts are marked **verified**; ML Kit behavior is distinguished from our policy.

## 1. Format decision table

ML Kit constants are from its Android barcode API. RawPOS treats barcodes as opaque strings: only non-empty ASCII digits are accepted, values are globally unique, and there is no length or check-digit rule. A non-digit sent through the existing resolver could take its name-search fallback. [ML Kit formats](https://developers.google.com/ml-kit/vision/barcode-scanning/android) · [Raw value/API reference](https://developers.google.com/android/reference/com/google/mlkit/vision/barcode/common/Barcode)

| Format | ML Kit constant | Payload and typical length | Check digit | RawPOS compatibility today | Recommended v1 state and reason |
|---|---|---|---|---|---|
| EAN-13 | `Barcode.FORMAT_EAN_13` | ASCII digits; 13 including check digit | Yes; GTIN-13 | Exact match if all 13 digits, including leading zero, are stored | **Enabled-with-restriction**: exactly 13 digits and valid GTIN-13 check digit |
| EAN-8 | `Barcode.FORMAT_EAN_8` | ASCII digits; 8 including check digit | Yes; GTIN-8 | Exact match if stored identically | **Enabled-with-restriction**: exactly 8 digits and valid GTIN-8 check digit |
| UPC-A | `Barcode.FORMAT_UPC_A` | ASCII digits; normally 12 including check digit | Yes; GTIN-12 | Exact match if stored identically; no UPC/EAN equivalence lookup | **Enabled-with-restriction**: exactly 12 digits and valid check digit. Do not silently pad or strip |
| UPC-E | `Barcode.FORMAT_UPC_E` | ASCII digits; normally 8, including check digit | Yes; validate via standard UPC-E expansion | Exact match only; some scanners may expand the compressed value | **Disabled-by-default** until USB scanner storage behavior is verified; never expand on the phone |
| Code 128 | `Barcode.FORMAT_CODE_128` | Variable ASCII/byte payload; numeric or alphanumeric | Symbol checksum is not a payload check digit | Numeric payloads can match; mixed payloads cannot, and may trigger name search | **Enabled-with-restriction**: ASCII digits only. Useful for numeric internal labels; reject FNC/control/non-digit data |
| Code 39 | `Barcode.FORMAT_CODE_39` | Uppercase letters, digits and symbols; variable | Optional Mod-43; ML Kit does not support its checksum digit | Exact match only for digit-only payloads | **Disabled-by-default**: no evidence it is needed; optional checksum and broad character set add ambiguity |
| ITF | `Barcode.FORMAT_ITF` | Numeric, even-length; ITF-14 is 14 digits | ITF-14 carries a GTIN-14 check digit; generic ITF does not | Exact match for digits | **Disabled-by-default**: ML Kit documents ITF checksum limitations and instability below six characters; enable ITF-14 only if required and locally validated |
| QR Code | `Barcode.FORMAT_QR_CODE` | UTF-8/structured data; variable | No check digit; QR error correction is different | Numeric QR may match exactly; URL/text QR cannot | **Disabled-by-default**: QR commonly contains URLs or structured payloads; digit-only filtering alone is not a reason to expose it |

**Enabled set:** `FORMAT_EAN_13`, `FORMAT_EAN_8`, `FORMAT_UPC_A`, `FORMAT_CODE_128`. Configure ML Kit to those formats only; do not enable all formats and rely solely on later filtering.

## 2. v1 validation predicate

Before **any network call**, require all of the following:

```text
format ∈ {EAN_13, EAN_8, UPC_A, CODE_128}
rawValue is not null
rawValue matches exactly: ^[0-9]{1,64}$
no trimming, Unicode normalization, digit conversion, or zero stripping
format-specific length/check-digit rules pass
the same (format, rawValue) is stable for 3 consecutive analyzed frames
exactly one symbol is eligible in the target region
no matching event is pending or latched
```

Format-specific rules:

- `EAN_13`: 13 digits and valid GTIN-13 check digit.
- `EAN_8`: 8 digits and valid GTIN-8 check digit.
- `UPC_A`: 12 digits and valid GTIN-12 check digit.
- `CODE_128`: 1–64 ASCII digits; no application-level check digit.
- For EAN/UPC, calculate the check digit using the GS1 mod-10 rule. Validate UPC-E only if it is enabled, using a tested standard expansion routine; do not improvise an expansion.

Use `String.matches("[0-9]{1,64}")` or an equivalent full-string match—not `\d`, which can vary with Unicode settings. Use `rawValue`, not `displayValue`.

**Minimum length is 1**, because RawPOS accepts one-digit barcodes and short internal codes exist. The desktop wedge’s four-character threshold is a keyboard-timing/UI heuristic, not a barcode data rule; copying it into the mobile scanner would reject legitimate codes. ML Kit may not decode one-character 1D symbols, but the app predicate should not reject one if the detector returns it.

**Maximum length is 64** as a proposed mobile/protocol safety cap; typical retail and internal product codes are shorter. RawPOS itself has no maximum, so this is an explicit v1 compatibility limit and needs approval plus a data audit. Reject 65 or more digits; raise the cap if real product data requires it.

## 3. Leading zeros and UPC/EAN normalization — headline decision

Android ML Kit exposes separate UPC-A and EAN-13 formats, and documents `getRawValue()` as the encoded value. UPC-A and a zero-prefixed EAN-13 are, however, equivalent barcode patterns. A platform or wrapper may represent UPC-A as a 13-digit EAN-13 value with a leading `0`. Conversely, a reported ML Kit issue shows a zero-prefixed EAN-13 being classified as UPC-A with that zero omitted. This is a version/platform risk, **not a reliable normalization contract**. [ML Kit API](https://developers.google.com/android/reference/com/google/mlkit/vision/barcode/common/Barcode) · [Reported leading-zero issue](https://github.com/googlesamples/mlkit/issues/797)

Never parse a barcode as an integer. Keep the exact `rawValue`, including leading zeros. Test the selected Android ML Kit version on target devices.

| Approach | Benefit | Business-integrity consequence |
|---|---|---|
| **Exact-only — recommended v1** | Preserves RawPOS’s current opaque-string semantics; no alias guessing | Safe failure: 12-vs-13 representation mismatch yields NotFound, not a possibly wrong product |
| Dual lookup fallback | After exact miss, query the 12-digit UPC-A / 13-digit zero-prefixed EAN equivalent | Can incorrectly alias arbitrary internal digit codes, which RawPOS does not distinguish from GTINs. If considered later, desktop must reject conflicting aliases and audit the data first |
| Shop-side normalization | Normalize known GTINs and all input paths to one canonical representation | Most consistent long-term, but requires auditing/migrating products, updating USB and mobile paths, and resolving collisions. Do not blindly rewrite arbitrary 12-digit barcodes |

**V1 consequence:** if the USB scanner stored a 12-digit UPC-A and the phone sends its 13-digit equivalent, exact lookup misses. If the phone drops an EAN-13 leading zero and the desktop stores 13 digits, it may also miss. Display “Barcode not found”; do not guess or fall back to a product-name search. This is an approval decision, not a minor implementation detail.

## 4. Duplicate detection and event delivery

ML Kit’s Android `Barcode` API does not provide a calibrated per-code confidence score. Use **temporal stability plus geometry**, not an invented confidence threshold.

**Proposed defaults**

- Analyze at **10 frames/second**.
- Require **3 consecutive analyzed frames** with the same format and exact value, bounding-box center inside the scan region. This spans about 200 ms between first and third observations at 10 fps.
- Same-value cooldown: **300 ms** after submission.
- Re-arm only after the value is absent for **3 consecutive frames and at least 300 ms**.
- Global minimum interval: **150 ms** between submissions. Keep at most **one request in flight**; while awaiting a desktop result, show “Waiting for desktop” and do not silently scan/queue more items.
- A new accepted scan gets one UUIDv4 `event_id` and an increasing session sequence. A retry reuses the **same** ID and payload.
- Client does not queue offline scans. A capture older than **10 seconds** is stale for first submission; rescan instead. An uncertain in-flight request may only be retried with its original ID.
- Desktop stores the result by event ID for **15 minutes** (and up to 10,000 entries), returning the cached result on retry. Reusing an ID with different payload is a conflict.

**State machine:** `ARMED → STABILIZING → SUBMITTING → WAITING_FOR_ACK → RESULT/LATCHED → WAIT_FOR_ABSENCE → ARMED`. A missing/different observation resets the candidate streak. Three matching observations submit once and latch that value. A business result is shown; the value stays latched until the absence rule and cooldown pass. Pause/background/offline transitions to `PAUSED`, clearing any unsubmitted candidate.

A legitimate rapid double-scan of the same item requires removing it from the target region for the re-arm interval, then presenting it again. Each presentation creates a new event ID; RawPOS’s merge behavior then increments the existing line quantity by one. A continuously visible code fires once, not once per frame. A visible “Scan again” control may explicitly re-arm after a confirmed result. For two or more distinct symbols in the target region in one frame, submit **neither** and ask the user to frame one barcode.

**Authority:** client dedupe suppresses repeated camera frames; desktop event-ID idempotency is authoritative for preventing duplicate mutations after retries. The desktop must not dedupe by barcode value/time window: two intentional scans of the same product are two units. Both layers are required.

## 5. Continuous-scanning UX contract

- **Scanning:** only while the user is on the visible scanner screen, the app is foregrounded, and the connection is ready. Show a live preview, reticle, and `Scanning` status.
- **Paused:** stop analysis immediately; show `Paused`. No requests are sent.
- **Background/locked:** stop camera and analyzer; clear candidate state. Resume requires an explicit user action. Never scan invisibly.
- **Connection dropped:** stop new submissions; show `Disconnected`. Do not queue scans. If a request was in flight, preserve its ID and resolve it by retry/status—not by creating a new event.
- **Desktop rejects:** show the typed result (unknown, inactive, validation, or unavailable); no success feedback and no cart mutation. Re-arm only after the code leaves the region.
- **Unknown barcode:** explicit “Barcode not found”; no name search, no silent retry loop.

## 6. Camera performance

| Topic | V1 mechanism | Expected/measurable effect |
|---|---|---|
| Resolution/format | Start at **1280×720** (about 0.9 MP), YUV_420_888; avoid native multi-megapixel streams. CameraX may choose a nearby supported size | Google recommends avoiding native-resolution frames; EAN-13 needs about 190 pixels of barcode width for ideal two-pixel modules. Measure decode success and p95 inference by device |
| Backpressure | CameraX `STRATEGY_KEEP_ONLY_LATEST`; close every `ImageProxy` after ML Kit completes | Prevents stale-frame queues and preview stalls; drops superseded frames |
| Frame rate | Cap analysis at **10 fps**, even if preview is 30 fps | Bounds CPU/battery and makes 3-frame stability predictable; measure time-to-decode |
| ROI/crop | Draw a wide center guide; initially filter by bounding-box center rather than physically cropping | Rejects peripheral codes without cutting quiet zones. Physical cropping may reduce work but can clip long 1D labels; test before adopting |
| Torch | Off by default; user-controlled toggle | Helps dim scenes; glossy labels can glare. Measure decode rate with torch on/off per device |
| Autofocus | Use rear camera and continuous autofocus where supported; keep exposure/white balance automatic | Improves sharpness for moving/close labels; camera support varies |
| Tap-to-focus | Map taps on preview to CameraX focus/metering | Gives a recovery path for soft focus; measure time to stable decode after tap |
| Zoom | Offer user pinch/slider using CameraX zoom ratio; avoid automatic repeated zoom by default | User can enlarge small codes; digital zoom can add noise and narrow field of view |
| Auto-zoom | Optional ML Kit zoom suggestions only with a supported pinned version, a callback, and device max-ratio bounds | May help distant codes; measure added latency and false zooms; do not fight user zoom |
| Low light / motion blur | Suggest moving closer/holding steady and offer torch; do not compensate by lowering resolution blindly | Light and steadiness improve edge contrast; measure failure rate in dim/moving conditions |
| Curved/damaged labels | Prompt to flatten, rotate slightly, and recapture; no OCR fallback | ML Kit may fail; never invent a digit string from a partial decode |
| Glare | Prompt a small off-axis tilt; offer torch off | Reduces specular reflection; test glossy packaging |
| Multiple codes | One eligible symbol in ROI only; otherwise show “Frame one barcode” | Avoids adding a shelf/adjacent item; no network call |
| Mirrored/reflective surfaces | Rear camera; prompt angle/lighting change; no image-flip guess | Reflections may reverse or obscure bars; measure on actual labels |

CameraX supports YUV/RGBA output, zoom, torch, and focus/metering, but capabilities vary by device. Use lifecycle-bound use cases and release each proxy. [CameraX analysis](https://developer.android.com/media/camera/camerax/analyze) · [CameraX configuration](https://developer.android.com/media/camera/camerax/configuration)

## 7. Proposed latency budget

All targets below are **proposals, not measurements**.

| Stage | p50 / p95 target | Measurement point |
|---|---:|---|
| Exposure and wait for analyzable frame | 60 / 120 ms | Camera timestamp to analyzer arrival |
| ML Kit inference, one frame | 25 / 60 ms | Monotonic start/end around `process()` |
| Three-frame stabilization | 200 / 300 ms | First matching detection to third |
| Validation/event creation | 2 / 5 ms | Candidate accepted to request ready |
| LAN request/response | 40 / 150 ms | Phone-side round trip |
| Desktop dispatch to GUI thread | 10 / 40 ms | Receiver arrival to GUI handler |
| Barcode-only lookup and draft mutation | 20 / 100 ms | Service entry to business result |
| Desktop render/result and response | 20 / 50 ms | UI state update to response ready |

**End-to-end goal:** photon-to-desktop business acknowledgment ≤ **450 ms p50 / 900 ms p95**. Measure Android and desktop intervals with local monotonic clocks; do not subtract unsynchronized phone/PC wall clocks. Correlate stages by event ID and never log raw barcode contents. Success feedback waits for the desktop’s business result and rendered checkout update, not transport receipt.

## 8. Feedback design

- Draw the detected bounding box and a center reticle. Show the exact last code and a status chip: `Scanning`, `Sending`, `Added`, `Not found`, `Inactive`, `Disconnected`, or `Paused`.
- **Added:** one short high tone and light haptic pulse, only after desktop confirms a match and checkout render.
- **Unknown:** distinct lower double tone and double haptic; no mutation.
- **Inactive/rejected:** different low two-tone pattern and red status; no mutation.
- **Network failure:** distinct alert and persistent `No desktop response` state; do not imply success.
- Provide app mute/volume controls, respect system volume/DND, and allow haptics off. Suppressed repeated frames get no success tone.
- Do not open URLs or show structured QR content. QR is disabled in v1.

## 9. Validation and rejection rules

Client rejects locally; desktop revalidates every request. User-facing messages are proposals.

| Condition | Message / behavior |
|---|---|
| Null/empty | “No barcode detected”; no request |
| Whitespace or control characters | “Unsupported barcode”; no trimming or request |
| Non-ASCII digits (e.g. Arabic-Indic/full-width) | “Use a supported numeric product barcode”; no request |
| Mixed/alphanumeric payload | “Only numeric product barcodes are supported”; no request |
| More than 64 digits | “Barcode too long (maximum 64 digits)”; no request |
| Invalid EAN/UPC check digit or format-length mismatch | “Barcode could not be verified—align and scan again”; no request |
| Disabled format | “Barcode type not enabled”; no request |
| URL-looking payload | “This is not a supported product barcode”; never open or forward it |
| Duplicate while request pending | Keep `Waiting for desktop`; no second event |
| Stale first submission/session | “Scan expired—scan again”; no mutation |
| Unknown exact barcode | “Barcode not found”; desktop must not search names |
| Inactive product | “Product is inactive”; no quantity change |

**Never forward a non-digit payload to `resolve_scan()`.** More strongly, do not use the current `resolve_scan()` for mobile scans at all until there is a barcode-only desktop entry point: even numeric unknown values can currently reach its name-search fallback.

## 10. Test vectors

Seeded desktop behavior assumes the exact string is stored, unless noted. Invalid or disabled cases must cause no desktop request and no draft change.

| Case | Format / raw value | Expected app behavior | Expected desktop behavior |
|---|---|---|---|
| Valid EAN-13 | EAN-13 `4006381333931` | Accept after stabilization | Exact lookup; add once if seeded |
| Valid EAN-8 | EAN-8 `96385074` | Accept | Exact lookup only |
| UPC-A, 12 digits | UPC-A `036000291452` | Accept with check digit | Exact 12-digit lookup |
| UPC-A represented as 13-digit EAN | EAN-13 `0036000291452` | Accept; preserve 13 digits | Exact 13-digit lookup; no alias to 12 |
| Zero-prefixed EAN reported as UPC-A | UPC-A `036000291452` | Accept exact 12-digit detector output | Match only 12-digit storage; 13-digit-only record is NotFound |
| UPC-E | UPC-E `04252614` | Disabled by default; no request | No mutation |
| ITF-14 | ITF `10012345000017` | Disabled by default; no request | No mutation |
| Short internal code | Code 128 `0042` | Accept; no four-character wedge rule | Exact lookup; preserve leading zeros |
| One-digit code | Code 128 `7` | Predicate accepts if detector returns it; ML Kit may not decode one-character 1D codes | If sent, exact lookup only |
| Numeric Code 128 | Code 128 `1234567890` | Accept | Exact lookup only |
| Alphanumeric Code 128 | Code 128 `AB123` | Reject locally | No request/name search |
| Numeric Code 39 | Code 39 `123456` | Disabled format; reject | No request |
| QR URL | QR `https://example.test/item` | Disabled/rejected; do not open | No request |
| QR digits | QR `12345678` | Disabled format despite digits | No request |
| Bad EAN check digit | EAN-13 `4006381333930` | Reject locally | No request |
| Empty/null | Any / empty | Reject | No request |
| Whitespace | Code 128 ` 0042 ` | Reject without trimming | No request |
| Control character | Code 128 `12\n34` | Reject | No request |
| 65 digits | Code 128, 65-digit string | Reject max length | No request |
| Arabic-Indic digits | Code 128 `١٢٣٤` | Reject non-ASCII digits | No request |
| Repeated same frames | Code 128 `0042`, 3+ identical frames | One event ID only | One increment |
| Same code held in view | Code 128 `0042`, 30 frames | One event; remain latched | No repeated increments |
| Rapid repeat, no re-arm | Same code re-presented before absence/cooldown | Suppress; show waiting/re-arm guidance | One increment |
| Repeat after re-arm | Same code absent ≥3 frames and 300 ms, then stable again | New event ID | Second increment (RawPOS merges quantity) |
| Two different codes in one frame | `0042` and `1234` in ROI | No event; “Frame one barcode” | No mutation |
| Retry same event ID | Same accepted request delivered twice | Retry same ID | Cached result; exactly one mutation |
| Unknown numeric code | Code 128 `999999` | Send once; show NotFound result | Exact lookup only; no name fallback/mutation |

## 11. Risks and alternatives

- **USB/mobile mismatch:** scanner configuration may emit UPC-A as 12 digits, a zero-prefixed 13-digit value, UPC-E expanded to UPC-A, or a symbology prefix. Capture real USB and mobile outputs for the same labels before pilot approval.
- **ML Kit drift:** pin the Android library/model and maintain golden barcode tests. Prefer a bundled model for deterministic offline first use; if using an unbundled model, block scanning until it is ready. ML Kit documents different bundled/unbundled delivery paths and first-use model availability.
- **Camera variance:** autofocus distance, ISP processing, focus speed and low-light behavior vary by phone. Certify a representative device matrix and retune Phase 6 thresholds.
- **High-volume lane:** a trigger-based USB/Bluetooth scanner remains the better tool for high throughput, ergonomics and predictable decode. Treat the phone scanner as an auxiliary or low-volume path, not a faster replacement.
- **Alternatives:** exact-only is safest now; audited server-side dual lookup or shop-side canonicalization could reduce UPC mismatches later. Neither should be silently introduced in the phone.

## 12. Verified vs assumed, dependencies, and open questions

**Verified from the shared fact pack**

- RawPOS barcodes are non-empty ASCII digits, globally unique, with no database/service length limit or check-digit logic.
- `resolve_scan()` strips input, does exact barcode lookup, then falls back to name/category search; a match mutates the in-memory draft.
- Existing USB wedge handling uses a 50 ms key-gap and four-key heuristic; it is not a product-code rule.
- Checkout services are GUI-thread-bound and not safe to call from a background receiver. RawPOS currently makes no network calls; an inbound listener is an explicit change.
- There is no Android implementation or Android test harness yet.

**Verified from external Android documentation**

- ML Kit documents the listed barcode formats, `rawValue`, consecutive-frame stabilization guidance, and format restrictions; it says ITF/Code 39 checksum digits are unsupported.
- CameraX documents `STRATEGY_KEEP_ONLY_LATEST`, YUV analysis, lifecycle binding, focus/metering and zoom. UPC/EAN canonicalization is not a safe assumption from those contracts.

**Assumptions/proposals**

- CameraX + ML Kit, four-format allowlist, 1–64 ASCII digit predicate, local GTIN check validation, exact-only lookup, and all dedupe/latency parameters above are recommendations—not measured behavior.
- UPC-A/EAN representation varies enough that target-device raw outputs must be tested.

**Dependencies**

- **Agent 2 (Android):** pin the ML Kit/model version and verify format/raw-value behavior, especially UPC-A, zero-prefixed EAN-13, and UPC-E on target devices.
- **Agent 4 (networking/security):** define session/event payload, sequence/stale handling, idempotency, and typed desktop acknowledgments; dispatch into the GUI thread and provide a barcode-only resolver.
- **Agent 6 (QA):** automate the vectors above, replay/reconnect tests, multi-frame suppression, and latency measurement on representative phones/desktops.

**Open approval questions**

1. Approve exact-only UPC/EAN lookup for v1, accepting safe NotFound mismatches, or authorize an audited alias policy?
2. Approve the proposed 1-digit minimum and 64-digit cap?
3. Are Code 128 numeric internal labels actually used? Is UPC-E or ITF-14 required?
4. What do current USB scanners store for UPC-A/EAN-13/UPC-E, and are equivalent 12-/13-digit records present?
5. Approve local check-digit validation for EAN/UPC formats, despite RawPOS not enforcing it?
6. Is the 300 ms re-arm workflow acceptable for two identical units, or is a manual re-arm control required?