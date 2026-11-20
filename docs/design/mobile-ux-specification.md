# Mobile UX Specification

**Status:** proposed
**Applies to:** Android app (Connection, Scanner, Settings, Diagnostics) and the RawPOS desktop pairing dialog
**Related:** `mobile-design-system.md`, `../architecture/networking-protocol.md`,
`../architecture/barcode-scanning.md`

---

## 1. Experience principles

1. **Point. See the till confirm. Move on.** Capture is automatic; the phone claims only what the
   desktop confirmed.
2. **Zero taps to scan.** Mid-sale actions take at most two taps. Pairing takes one tap on the phone
   plus one approval on the desktop.
3. **The till is the truth.** The phone never shows a product name, price, cart, or total. It shows the
   code it read and a status the desktop returned.
4. **Built for a retail floor.** 48 dp targets, 64 dp scanner controls, 4.5:1 text, gloves, glare,
   one hand, long shifts.
5. **Arabic-first.** Start/end layouts, LTR digit runs, Western numerals, native Arabic copy.
6. **No silent scanning.** If the user cannot see feedback, the camera is not analyzing.
7. **No guessing.** Non-digit payloads never leave the phone; an unknown code is "not found", never a
   name match.
8. **Recoverable without a manual.** Every failure names the cause and the next action.

---

## 2. Information architecture

```
App launch
  ├── No pairing ──────────────► Connection (QR scan)
  ├── Paired, reachable ───────► Scanner
  ├── Paired, unreachable ─────► Scanner, paused + banner
  └── Revoked ─────────────────► Connection, "access removed"

Scanner
  ├── Overflow ────────────────► Settings
  ├── Connection chip ─────────► Connection details
  └── Result card ─────────────► Scan again / Retry

Settings
  ├── Scanner (formats, sound, vibration, continuous, torch)
  ├── Till (name, endpoint, status, edit address, forget, diagnostics)
  ├── Appearance (language, theme, numerals)
  └── Support (copy diagnostics, privacy, about, version)
```

No bottom navigation. The scanner is the app; Settings is one level away. This is a tool, not a
dashboard.

---

## 3. Screen A — Connection

### 3.1 Purpose

Go from unpaired to connected in one scan, with an honest explanation for every failure.

### 3.2 Layout

```
┌──────────────────────────────┐
│ ←  Pair with your till       │
│                              │
│   ┌────────────────────┐     │
│   │  camera viewfinder │     │
│   │      (QR only)     │     │
│   └────────────────────┘     │
│   Point at the code on the   │
│   till screen.               │
│                              │
│   [ Open camera settings ]   │  only if denied
└──────────────────────────────┘
```

- QR mode only; product formats are off in this screen.
- The viewfinder is 4:3 or square, centered, with a quiet zone.
- No manual pairing code in v1. The QR carries the endpoint, desktop id, pin, and one-time secret;
  a short typed code cannot carry that trust material safely. A previously paired desktop can have its
  host/port edited in Settings (pin unchanged).
- The 6-digit code is a **verification** code shown after the QR is read, not a pairing code.

### 3.3 States

| State | Visual | Primary action | Secondary |
|---|---|---|---|
| First run / unpaired | Viewfinder + hint | Point at QR | Open settings (if camera denied) |
| Camera permission needed | Explanation + icon | Allow camera | Open settings |
| Camera permanently denied | Explanation | Open settings | — |
| Reading QR | Viewfinder, subtle progress | — | Cancel |
| Confirming on till | Large 6-digit code, "Check this code on the till" | — (waiting) | Cancel |
| Connected | Green check, till name | Go to scanner | — |
| Pairing denied | Warning, "The till declined this phone" | Try again | — |
| Pairing expired | Warning | Scan a new code | — |
| Pin mismatch | Danger, "Desktop identity changed" | Do not continue | Diagnostics |
| Unreachable | Warning, "Can't reach your till" | Retry | Diagnostics |
| Revoked | Danger, "Access was removed on the till" | Pair again | — |
| Not paired / wrong network | Warning with Wi-Fi hint | Retry | Diagnostics |

### 3.4 Interaction

- The camera starts only after the screen is visible and permission is granted.
- Scanning a QR stops the camera, posts the pairing request, and shows the 6-digit code while the
  request waits for desktop approval (up to 60 s).
- A denial or expiry clears the code and offers a new scan.
- Back leaves the screen; it does not cancel an in-flight pairing silently. Show a confirm if a pairing
  is pending.

---

## 4. Screen B — Scanner

### 4.1 Purpose

Read a barcode at speed and show proof of delivery without obscuring the code.

### 4.2 Layout

```
┌──────────────────────────────┐
│ Till 1  ● Connected      ⋮   │  chip + overflow
├──────────────────────────────┤
│        ┌──────────────┐      │
│        │  ── scan ──  │      │  target frame; no overlay
│        └──────────────┘      │  text over the code
├──────────────────────────────┤
│ ✓ Added                      │  result card
│ 6221000659298                │  LTR digits
│                        12:04 │
├──────────────────────────────┤
│   [Torch]   [Pause]   [1×]   │  64 dp controls
└──────────────────────────────┘
```

- The preview fills the screen behind the status bar.
- The target frame is an outline only; nothing is drawn inside it.
- The result card is persistent, not a transient snackbar. It shows the last code and status until the
  next scan.
- The overflow menu contains Settings, Diagnostics, and Disconnect.
- The chip opens Connection details.

### 4.3 States

| State | Card | Frame | Feedback | Action |
|---|---|---|---|---|
| Idle | "Point at a barcode" | Dashed | — | — |
| Reading | Transient "Reading…" (optional) | Dashed | — | — |
| Sending | "Sending…" | Solid | None | — |
| Added | Green check, "Added", code | Solid green flash 150 ms | Confirm haptic + high tone | Scan next |
| Merged | Green check, "Quantity increased", code | Same | Same | Scan next |
| Not found | Amber alert, "Unknown barcode", code | Solid amber | Double pulse + low tone | Scan next |
| Inactive | Amber/red, "Product inactive" | Solid | Reject pattern | Fix on till |
| Rejected — no shift | Amber banner, "No open shift" | Paused | Reject pattern | Open shift on till |
| Rejected — read-only | Amber banner, "Till is read-only" | Paused | Reject pattern | Activate on till |
| Rejected — no checkout | Amber banner, "RawPOS is not on Sales" | Paused | Reject pattern | Open Sales |
| Rejected — till busy | Amber banner, "Finish the dialog on the till" | Paused | Reject pattern | Finish the dialog |
| Rejected — stale context | Amber, "Sale changed — scan again" | Solid | Double pulse | Rescan |
| Not confirmed | Amber refresh icon, "Check the till" | Solid | One pulse | Retry (same event) |
| Not sent | Red wifi-off, "Not sent" | Solid | One pulse | Retry (new event) |
| Not a product code | Amber, "Not a product barcode" | Solid | Reject pattern | — |
| Paused | Scrim, "Paused" | Hidden | — | Resume |
| Camera error | Banner | Hidden | — | Retry |
| Low light (10 s no read) | Hint banner | Dashed | — | Torch |

"Retry (same event)" is only offered for an uncertain delivery; it reuses the event id. "Retry (new
event)" is only for a scan that never left the phone.

### 4.4 Continuous scanning

- Default: on. After a result, the scanner re-arms when the code leaves the target for the re-arm
  interval (500 ms absence).
- A "Scan again" button is always available for accessibility and for a deliberate second unit.
- If continuous is off, the scanner pauses after each result and requires "Scan again".
- Only one request is in flight. While sending, a new code is not queued; the card says "Sending…".

### 4.5 Controls

- **Torch:** 64 dp, toggles; off by default; icon changes state.
- **Pause/Resume:** 64 dp, centered; stops analysis, sound, and haptics; keeps the connection.
- **Zoom:** 64 dp, cycles 1×/2×/3× up to the device max; pinch also works.
- **Overflow:** Settings, Diagnostics, Disconnect.
- **Chip:** connection state; tap for details.

---

## 5. Screen C — Settings

### 5.1 Layout

One list, 56 dp rows, grouped:

```
Scanner
  Barcode types                 EAN-13, EAN-8…   ›
  Sound                         [on]  Follows silent mode
  Vibration                     [on]
  Continuous scanning           [on]
  Torch on start                [off]
Till
  Till 1 · Connected            ›
  Edit address                  ›
  Diagnostics                   ›
  Forget this till              (danger)
Appearance
  Language                      Arabic · English
  Theme                         System · Light · Dark
  Numerals                      Western digits (0–9)
Support
  Copy diagnostics (redacted)
  Privacy
  About · Version 1.0.0
```

### 5.2 Behavior

- Settings apply immediately; there is no Save button.
- Changing language/theme does not stop the camera or drop the connection.
- "Barcode types" opens a checklist with a warning for non-default formats:
  "Alphanumeric formats are not supported by RawPOS; enabling them will reject scans."
- "Forget this till" requires a confirmation; the confirm control is not the default. It clears only
  the phone's credentials; the desktop record remains until revoked there.
- "Edit address" is for a known desktop whose IP changed. The pin stays; a pin mismatch fails closed.
- Diagnostics: connection state, endpoint, pin suffix, app version, last 10 event ids/statuses/latency.
  No barcodes by default; "Copy diagnostics" redacts codes.
- Privacy: "Scanned codes go only to your till on this Wi-Fi. Nothing goes to the internet."
- About: version, build, licenses (Noto Sans Arabic OFL, Lucide ISC, ML Kit, CameraX), no cloud.

---

## 6. Feedback

### 6.1 Visual

- Status is always **icon + word + color**, never color alone.
- Success flash: 150 ms + 150 ms hold, then static.
- Warning/error: 150 ms color transition, no shake.
- The result card is a polite live region; connection loss/revocation is assertive.
- Codes are LTR, monospace, grouped if long; never animated.

### 6.2 Audio

| Outcome | Sound |
|---|---|
| Added / merged | Short high tone (~80 ms) |
| Not found | Low double tone |
| Inactive / rejected | Distinct low two-tone |
| Not confirmed / not sent | Alert tone |
| Duplicate replay | None |

Sound follows the ringer/silent mode. The Settings row says "Follows silent mode". A silent phone still
shows visuals and haptics.

### 6.3 Haptic

| Outcome | Pattern |
|---|---|
| Added / merged | `CONFIRM` (API 30+), short pulse below |
| Not found / rejected | `REJECT`, or two 30 ms pulses 60 ms apart |
| Uncertain | One 30 ms pulse |

Vibration is a separate toggle and is hidden on devices without a haptic actuator. v1 uses
platform/Compose haptic feedback, which does not require the `VIBRATE` permission. Custom multi-pulse
patterns would require adding `VIBRATE` and are out of scope unless field testing proves them necessary.

### 6.4 Reduced motion

If `ANIMATOR_DURATION_SCALE == 0`: no sweep, no flash, no card motion; 100 ms cross-fades; text/color
unchanged. Never animate codes, counts, or error text.

---

## 7. Desktop pairing dialog

### 7.1 Entry points

- A permanent status-bar button next to the shift button, visible from every module:
  `status.scanner.off` / `connected` / `unavailable`.
- A Settings entry that opens the same dialog.
- Neither steals focus from `scan_edit` when opened from Sales.

### 7.2 Layout (480 px)

```
Phone scanners
┌──────────────┐  Check that the phone shows this code:
│   QR 240 px  │  4821
│              │  Expires in 1:58   [New code]
└──────────────┘  [Approve] [Deny]

Device          Status      Last scan
Ahmed's phone   Connected   12:04       [Revoke…]

[Add phone]                              [Close]
```

- QR: 240 logical px, 4-module quiet zone, black on white in both themes (new tokens
  `qr_foreground`, `qr_background`).
- The QR must not be copyable or savable; it contains a one-time secret.
- "Approve"/"Deny" apply only while a pairing attempt is pending.
- Device list uses the shared `DataTable`; revoke uses the danger role and a reason dialog
  (lost/stolen, replaced, staff left).
- Empty state: "No phones paired" + "Add phone".

### 7.3 Behavior

- "Add phone" opens a 120 s window and displays the QR + countdown.
- A phone scan triggers the device name + 6-digit code; the cashier compares and approves.
- Closing the dialog cancels the window.
- Revoke is immediate; the phone's next request gets `403`.
- Pairing/revoke are **owner-only**; cashiers see status and the list.
- Pair/revoke write audit events (`audit.event.device.paired` / `revoked`).
- The receiver lifecycle is independent of the dialog: it runs while enabled, survives language/digit
  remounts, and stops on sign-out/recovery/shutdown.
- Use a non-modal `show()` so a scan typed into the checkout is not captured by the dialog; no text
  fields; Esc closes; focus returns to `scan_edit`.
- Light/dark and RTL follow the existing theme and `apply_direction`.

### 7.4 Desktop wording

Reuse `common.confirm`, `common.cancel`, `error.NotOwner`. New keys are listed in §8.

---

## 8. Localization and RTL

### 8.1 Mirroring

- Use start/end, never left/right.
- Mirror: back/forward chevrons, list disclosure, text alignment, side controls.
- Do **not** mirror: camera preview, target frame, scan line, barcodes, pairing codes, timestamps,
  numbers, brand marks.
- In the scanner bottom bar, Pause stays centered; Torch and Zoom swap sides in RTL.
- In the result card, the status icon is at the start; the action is at the end.

### 8.2 Digits and bidi

- Codes and timestamps are forced LTR at the element level.
- Inside Arabic sentences, wrap inserted Latin/digit values in LRI/PDI (U+2066/U+2069).
- Numerals: Western by default. The optional Arabic-Indic setting changes counts/times, never
  identifiers or codes.
- A scanned Arabic-Indic payload is refused, not converted.

### 8.3 Copy rules

- Sentence case. State the result, then the next step.
- No jargon: no "invalid", "socket", "token", "port", "TLS".
- Use "till" / نقطة البيع, matching the desktop title.
- Reuse desktop wording where the meaning is identical (marked †).
- Errors are actionable: what happened + what to do.

### 8.4 Mobile key table

`values/` and `values-ar/` must have identical keys. Arabic marked `[needs review]` requires a native
reviewer before release. Android resource names use underscores; the catalogue uses dots.

| Key | English | Arabic |
|---|---|---|
| mobile.pair.title | Pair with your till | اربط الهاتف بنقطة البيع |
| mobile.pair.hint | Point the camera at the code on the till. | وجّه الكاميرا إلى الرمز على شاشة نقطة البيع. |
| mobile.pair.camera_needed | Camera access is needed to read the code. | نحتاج إلى إذن الكاميرا لقراءة الرمز. |
| mobile.pair.allow_camera | Allow camera | السماح بالكاميرا |
| mobile.pair.open_settings | Open settings | فتح الإعدادات |
| mobile.pair.expired | This code has expired. Ask the till for a new one. | انتهت صلاحية الرمز. اطلب من نقطة البيع رمزًا جديدًا. |
| mobile.pair.confirm_code | Check this code on the till: {code} | تحقق من هذا الرمز على نقطة البيع: {code} |
| mobile.pair.waiting | Waiting for approval on the till… | في انتظار الموافقة على نقطة البيع… |
| mobile.pair.denied | The till declined this phone. | رفضت نقطة البيع هذا الهاتف. |
| mobile.pair.pin_mismatch | Desktop identity changed. Do not continue. | تغيّرت هوية نقطة البيع. لا تُكمل. |
| mobile.connection.connected | Connected to {till} | متصل بـ{till} |
| mobile.connection.reconnecting | Reconnecting… | جارٍ إعادة الاتصال… |
| mobile.connection.unreachable | Can't reach your till. Check the Wi-Fi. | تعذّر الوصول إلى نقطة البيع. تحقق من شبكة Wi-Fi. |
| mobile.connection.revoked | Access was removed on the till. | تم سحب الصلاحية من نقطة البيع. |
| mobile.chip.connected | Connected | متصل |
| mobile.chip.reconnecting | Reconnecting | جارٍ الاتصال |
| mobile.chip.offline | Offline | غير متصل |
| mobile.chip.revoked | Revoked | الصلاحية مسحوبة |
| mobile.action.retry | Retry | إعادة المحاولة |
| mobile.action.pair_again | Pair again | إعادة الربط |
| mobile.common.cancel † | Cancel | إلغاء |
| mobile.error.generic | Something went wrong. Try again. | حدث خطأ ما. حاول مرة أخرى. |
| mobile.scan.hint | Point at a barcode | وجّه الكاميرا إلى الباركود |
| mobile.scan.sending | Sending… | جارٍ الإرسال… |
| mobile.scan.added | Added | تمت الإضافة |
| mobile.scan.merged | Quantity increased | زادت الكمية |
| mobile.scan.not_found | Unknown barcode | باركود غير معروف |
| mobile.scan.inactive | Product is inactive | الصنف غير مفعّل `[needs review]` |
| mobile.scan.rejected | Not added: {reason} | لم تتم الإضافة: {reason} |
| mobile.scan.not_confirmed | Not confirmed. Check the till before retrying. | لم يتم التأكيد. راجع نقطة البيع قبل المحاولة. |
| mobile.scan.not_sent | Not sent. The till is unreachable. | لم يُرسل. نقطة البيع غير متاحة. |
| mobile.scan.not_product_code | Not a product barcode. | ليس باركود منتج. |
| mobile.scan.add_one_more | Scan again | امسح مرة أخرى |
| mobile.scan.paused | Paused | متوقف مؤقتًا |
| mobile.scan.pause | Pause scanning | إيقاف المسح مؤقتًا |
| mobile.scan.resume | Resume scanning | استئناف المسح |
| mobile.scan.torch | Torch | المصباح |
| mobile.scan.zoom | Zoom {level} | التكبير {level} |
| mobile.scan.no_shift | No open shift on the till. Open one to sell. | لا توجد وردية مفتوحة في نقطة البيع. افتحها للبيع. |
| mobile.scan.readonly | The till is read-only. Sales are blocked. | نقطة البيع في وضع القراءة فقط. البيع متوقف. |
| mobile.scan.no_checkout | Open the Sales screen on the till. | افتح شاشة البيع على نقطة البيع. |
| mobile.scan.busy | Finish the dialog on the till, then retry. | أكمل النافذة على نقطة البيع ثم أعد المحاولة. |
| mobile.scan.stale | The sale changed. Scan again. | تغيّرت عملية البيع. امسح مرة أخرى. |
| mobile.scan.camera_error | The camera stopped. Try again. | توقفت الكاميرا. أعد المحاولة. |
| mobile.scan.low_light | Too dark to read? Turn on the torch. | صعب القراءة؟ شغّل المصباح. |
| mobile.till_error.ProductInactive | Product is deactivated and cannot be sold. | هذا الصنف غير مفعّل ولا يمكن صرفه. `[needs review]` |
| mobile.till_error.NoOpenShift | No shift is open. | لا توجد وردية مفتوحة. |
| mobile.till_error.LicenseReadOnly | The till is read-only. | نقطة البيع في وضع القراءة فقط. |
| mobile.till_error.generic | The till could not add this item. Check the till. | تعذّرت إضافة هذا الصنف على نقطة البيع. راجع نقطة البيع. `[needs review]` |
| mobile.settings.title † | Settings | الإعدادات |
| mobile.settings.formats | Barcode types | أنواع الباركود |
| mobile.settings.sound | Sound | الصوت |
| mobile.settings.sound_hint | Follows silent mode | يتبع وضع الصامت |
| mobile.settings.vibration | Vibration | الاهتزاز |
| mobile.settings.continuous | Continuous scanning | المسح المستمر |
| mobile.settings.torch_start | Torch on start | المصباح عند البدء |
| mobile.settings.till | Connected till | نقطة البيع المتصلة |
| mobile.settings.edit_address | Edit address | تعديل العنوان |
| mobile.settings.forget | Forget this till | نسيان نقطة البيع |
| mobile.settings.forget_confirm | Forget {till}? You will need to pair again. | نسيان {till}؟ ستحتاج إلى إعادة الربط. |
| mobile.settings.language † | Language | اللغة |
| mobile.settings.theme † | Theme | السمة |
| mobile.settings.numerals † | Number display | عرض الأرقام |
| mobile.settings.privacy | Scanned codes go only to your till on this Wi-Fi. Nothing goes to the internet. | تُرسل الرموز الممسوحة إلى نقطة البيع على شبكة Wi-Fi هذه فقط، ولا يُرسل شيء إلى الإنترنت. `[needs review]` |
| mobile.settings.diagnostics | Copy diagnostics (codes redacted) | نسخ معلومات التشخيص (الرموز مُخفاة) |
| mobile.settings.about | About | عن التطبيق |
| mobile.theme.system | System | النظام |
| mobile.privacy.title | Privacy | الخصوصية |
| mobile.privacy.body | No account, no cloud, no product data. Codes go only to your till. | لا حساب، لا سحابة، لا بيانات منتجات. تُرسل الرموز إلى نقطة البيع فقط. `[needs review]` |
| mobile.about.version | Version {version} | الإصدار {version} |

### 8.5 Desktop key table (Arabic + English, both catalogs)

| Key | English | Arabic |
|---|---|---|
| status.scanner.off | Phone scanner: off | ماسح الهاتف: معطّل |
| status.scanner.connected | Phone scanner: connected | ماسح الهاتف: متصل |
| status.scanner.unavailable | Phone scanner: unavailable | ماسح الهاتف: غير متاح |
| settings.mobile.title | Phone scanners | ماسحات الهاتف `[needs review]` |
| settings.mobile.add | Add phone | إضافة هاتف |
| settings.mobile.code_hint | Check that the phone shows this code: {code} | تحقق من أن الهاتف يعرض هذا الرمز: {code} |
| settings.mobile.expires | Expires in {time} | تنتهي خلال {time} |
| settings.mobile.approve | Approve | موافقة |
| settings.mobile.deny | Deny | رفض |
| settings.mobile.revoke | Revoke… | سحب الصلاحية… |
| settings.mobile.revoke_reason | Reason | السبب |
| settings.mobile.reason.lost | Lost or stolen | مفقود أو مسروق |
| settings.mobile.reason.replaced | Phone replaced | تم استبدال الهاتف |
| settings.mobile.reason.left | Staff member left | غادر الموظف `[needs review]` |
| settings.mobile.no_network | No Wi-Fi or Ethernet address found. Connect to a network to pair phones. | لم يُعثر على عنوان شبكة. اتصل بشبكة Wi-Fi أو بالإيثرنت لربط الهواتف. `[needs review]` |
| settings.mobile.port_blocked | Windows Firewall blocks the scanner port on private networks. | جدار حماية Windows يمنع منفذ الماسح على الشبكات الخاصة. `[needs review]` |
| settings.mobile.read_only | Read-only mode: pairing is unavailable. | وضع القراءة فقط: الربط غير متاح. |
| settings.mobile.col_device | Device | الجهاز |
| settings.mobile.col_last | Last scan | آخر مسح |
| sales.scan.from_phone | {message} (phone) | {message} (من الهاتف) |
| audit.event.device.paired | Phone scanner paired | تم ربط ماسح هاتف |
| audit.event.device.revoked | Phone scanner removed | تمت إزالة ماسح هاتف |

"Manual code" is deliberately **not** a key: v1 pairing is QR-only.

---

## 9. Accessibility

- Every control has a label; switches announce on/off through the platform.
- Result card: polite live region; connection loss/revocation: assertive.
- Codes: read digit by digit via a spaced content description, always LTR.
- Focus order: top bar → chip → result card → controls → overflow. Pause stays central in both
  directions.
- 48 dp minimum, 4.5:1 text, 3:1 non-text.
- Never color alone: icon + word + haptic; the frame changes shape.
- 200% font: wrap codes and labels to two lines; never truncate; test both languages.
- Reduced motion: honor `ANIMATOR_DURATION_SCALE`.
- Camera-free paths: the till's USB scanner; typed barcode entry (open question); torch/zoom.
- Gloves: test cotton and nitrile; 64 dp controls; no guarantee for capacitive gloves.

---

## 10. Design QA without a designer

- Automated: contrast recomputed from tokens and thresholded; `values-ar` key parity; Android Lint
  `MissingTranslation` blocking; Compose semantics tests for ≥48 dp and labels; a CI check for hex
  literals outside the token file.
- Screenshots: every state, both themes, `ar`/`en`, 100%/200% font, stored with the build.
- Human: native Arabic review of every `[needs review]` string and every RTL screen; Accessibility
  Scanner; TalkBack walkthrough; a non-developer cashier runs M-01…M-13.
- Field: gloves, torch on/off, 30-minute heat/battery session.

---

## 11. Open questions

1. Brand assets: the repo has no logo, icon, or font. Supply a mark, or confirm the token palette and
   Noto Sans Arabic are the base.
2. Pairing/revoke: owner-only (recommended) or any signed-in user?
3. Portrait-first with rotation, or locked portrait? (Recommended: portrait-first, rotation supported.)
4. Typed barcode entry as a camera-free path: allow (recommended) or USB only?
5. Sound follows the ringer (recommended) or independent?
6. After an outage, resume manually (recommended) or automatically?
7. Phone numeral setting in v1? (Recommended: yes, Western default.)
8. Revoke reason required? (Recommended: yes, three fixed options.)
9. Privacy wording legal review before release? (Recommended: yes.)
10. Add "System" to the desktop theme options? (Mobile default is System; desktop is explicit.)
