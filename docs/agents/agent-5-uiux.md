I'll start by reading the shared brief facts and the core theme files, then the widgets and screens.The shared fact pack gives me the baseline. Next I'll read the widget kit, which is where the desktop component language is defined.The widget kit is clear. Next are the checkout screens, the main window, settings, and the localization module.The desktop screens are read. Next I need the localization catalog for copy patterns, the numeric digit-style module, the icon set, and the 014 design rationale.The 014 docs are useful but partly stale: they say no theme system exists, and the code now has one. I'll verify that claim directly. Next I need the rest of the localization module, the digit-style code, the icon loader, and a check for hard-coded colors and accessibility attributes.The desktop codebase has far more accessible names than the 014 audit claims, so I'll record the current state rather than the old one. Next I'll verify the theme wiring, the remaining hard-coded colors, the icon coloring path, and the existing theme tests.The theme wiring is confirmed: tokens, QSS, a shared icon loader, and tests that switch themes. The earlier inline `#b00020`/`#c02020` colors no longer appear, which I'll confirm with a targeted search. Next I'll check the remaining hard-coded colors, the owner-approval dialog that a desktop pairing screen would resemble, the read-only banner, and the shell remount path.Confirmed: the desktop has no hard-coded colors left outside the token module, apart from one fallback in `icons/lucide/__init__.py:64`. The read-only banner is a plain widget with no severity role. Next I'll check how many shared widgets production screens actually use, the remount path, and whether any contrast test exists.The remount path is confirmed (`app_shell.py:402-417`). The grep also shows the shared button, card, banner, table, and empty-state widgets are defined but not used by production views. I'll check whether production views set the `role` properties that the stylesheet depends on, since that determines whether a primary action looks primary.The stylesheet's `role` selectors (`primary`, `danger`, `success`) appear to be unused by production views, which I need to confirm before I claim the checkout's primary button has no emphasis. I'll also check for bundled fonts and brand assets, which affects the Arabic font decision.Two more facts matter for the brief: the repo has no bundled fonts or brand assets, and the constitution lives in a hidden `.specify` folder. I'll read the constitution's devices and localization rules, check which status roles the first-run wizard uses, and look at the one reference image in the repo.The constitution gives the governing rules. Principle VI sets Western digits as the default and requires RTL mirroring, and the devices section keeps keyboard-wedge scanning independent of any network call. The reference image is a photo of a legacy dense invoice screen, so it reflects what cashiers already know. I'll run three last checks: which tokens are actually consumed, the Lucide license, and the sibling agent briefs so my dependencies match theirs.The Lucide license is ISC, and several tokens are defined but never read by the stylesheet or any widget. The sibling agent briefs are in the same folder, so I'll read their missions to align my dependencies on Agents 2 and 6 with the actual split of work.I now have the sibling scopes: Agent 2 owns Android engineering, Agent 3 owns barcode validation, dedupe, and feedback numbers, Agent 4 owns the status enum, pairing window, and TLS, and Agent 6 owns QA evidence. Next I'll compute the WCAG contrast ratios for the actual desktop token pairs and the mobile candidates, instead of estimating them by eye.# RawPOS Scanner: UI/UX Specification and Mobile Design System

Read-only review. Desktop evidence is from HEAD `797167a`. Contrast ratios were computed in this review with the WCAG 2.x formula. `[needs review]` marks Arabic I am not certain of.

## 1. Design audit

RawPOS has a real token system. The 014 documents say it does not, and they are stale. `tokens.py`, `qss.py`, `theme_manager.py` and eleven widgets exist. The only literal colours outside `tokens.py` are two fallbacks used when the theme manager fails (`icons/lucide/__init__.py:64`, `main_window.py:116`).

| Area | Evidence | Verdict |
|---|---|---|
| Colour | 25 semantic roles, light and dark (`tokens.py:17â€“43`, `168â€“222`) | Keep the names |
| Theme | `ThemeManager.apply` sets palette and QSS, persists, emits a signal (`theme_manager.py:63â€“90`). Only Light and Dark exist (`tokens.py:12â€“14`) | Add System |
| Type | Segoe UI / Tahoma / Noto Sans Arabic (`tokens.py:48`); body 11 pt, heading 13, title 16. No font files in repo | Do not copy point sizes |
| Space, shape | 4 px ladder 4â€“64; radius 4/6/10 (`tokens.py:66â€“84`). `radius_lg`, `radius_pill`, `size_display`, `family_mono` are read by nothing | Keep the ladder; drop unused tokens |
| Touch | 44 px minimum (`tokens.py:118`; `qss.py:122, 180`) | Too small for gloves |
| Icons | 27 Lucide SVGs, ISC licence; only chevrons and arrows mirror (`lucide/__init__.py:15â€“49, 129â€“131`) | Keep |
| RTL | Per-widget `apply_direction` (`rtl.py:10â€“14`); codes stay left-aligned (`rtl.py:17â€“45`); identifiers stay ASCII (`numbers.py:5â€“7`) | Keep |
| Adoption | No production view constructs `PrimaryButton`, `DataTable`, `FeedbackBanner`, `EmptyState`, `Card` or `SectionHeader` (grep). Confirm payment has no role (`payment_panel.py:60`), so it looks like Hold | Kit unadopted; no primary emphasis |
| Feedback | Checkout status is a plain label (`checkout_view.py:177`), so success and error look alike. Unknown barcode beeps (`checkout_view.py:454`) | Add severity styling |

Verified accessibility failures:
- Input borders `#d0d7de` on white measure 1.45:1; dark `#30363d` on `#161b22` measures 1.42:1. WCAG 1.4.11 asks for 3:1 (`qss.py:177`).
- Low stock is shown by colour alone (`line_grid.py:145â€“151`).
- `ReadOnlyBanner` has no severity role (`readonly_banner.py:40â€“55`).
- No contrast test exists.

Keep the semantic roles, the single QSS generator, direction-aware alignment, and the total-versus-remaining split that stops a short tender reading as paid (`payment_panel.py:115â€“125`). Do not copy the 11 pt body, hover states (`qss.py:125â€“128`), the 900أ—600 default (`main_window.py:83`; the app opens maximised, `app_shell.py:409â€“413`), the left module list, the nine-column grid (`line_grid.py:35â€“45`), or a keyboard-only input model. Cashiers' expectations come from a dense legacy invoice photo (`specs/006â€¦/reference/invoice.jpg`) with a high-contrast net-total box, so keep one large total on the phone.

Hygiene: `specs/014â€¦/data-table-design.md` lines 196â€“461 contain pasted tool-call text and a shell heredoc that would write two more files. I did not run it. It should be removed. The repo has no logo, icon or font file, so there are no brand assets.

## 2. Mobile brand thesis

**Point, see the till confirm, move on.** The phone does one job, needs one gesture, and claims only what the till has confirmed. It shares the desktop's identity: role names, blue primary, neutral surfaces, Western digits, mirroring. Its ergonomics are its own: larger type, bigger targets, a dark camera stage, and status that never depends on colour.

1. **Proof, not optimism.** "Added" appears only after the till confirms.
2. **Zero taps to scan.** Capture is automatic. Mid-sale actions take at most two taps.
3. **Built for gloves, glare and one hand.** 48 dp targets, 64 dp scanner controls in the thumb zone, text at 4.5:1 or better.
4. **Noisy floors and silent phones.** Every outcome has a visual state and, if enabled, a haptic.
5. **Never guess a code.** Non-digit payloads are refused on the phone and never sent, so no phone scan can reach name search.
6. **Arabic-first, mirrored by default.** Start/end layouts, LTR codes, Western digits (Constitution VI).
7. **One phone, one till.** The till's name is on every screen.
8. **Long shifts on old phones.** No blur. The only continuous animation is the scan line, and it stops when paused.

## 3. Android design tokens

### 3.1 Colour

Names match `ColorTokens`. (M) marks mobile-only roles.

| Role | Light | Dark |
|---|---|---|
| background | #f5f6f8 | #0d1117 |
| surface | #ffffff | #161b22 |
| surfaceVariant (M) | #eaeef2 | #21262d |
| textPrimary | #1f2328 | #e6edf3 |
| textSecondary | #57606a | #8b949e |
| textDisabled | #8c959f | #484f58 |
| border (dividers only) | #d0d7de | #30363d |
| outline (M; fields, switches) | #6e7781 | #8b949e |
| focusRing | #0969da | #58a6ff |
| primary | #0969da | #58a6ff |
| primaryPressed | #033d8b | #a5d6ff |
| onPrimary, onStatus | #ffffff | #0d1117 |
| success / successSurface | #1a7f37 / #dafbe1 | #3fb950 / #12261e |
| warning / warningSurface | #9a6700 / #fff8c5 | #d29922 / #272115 |
| danger / dangerSurface | #cf222e / #ffebe9 | #f85149 / #2d1618 |
| info / infoSurface | #0e7490 / #ddf4ff | #2dd4bf / #102a3a |
| scanner.stage (M) | #000000 | #000000 |
| scanner.target (M, 80% alpha) | #ffffff | #ffffff |
| scanner.ok / scanner.fail (M) | #3fb950 / #f85149 | same, fixed on stage |

Notes: `surfaceVariant` doubles as the disabled container, and disabled text uses `textDisabled`. `low_stock` is not used because the phone shows no stock. `primary_hover` is replaced by Material state layers.

Contrast:

| Pair | Light | Dark | Requirement |
|---|---|---|---|
| textPrimary on surface | 15.80 | 14.64 | 4.5 pass |
| textSecondary on surface / surfaceVariant | 6.39 / 5.48 | 5.62 / 4.95 | 4.5 pass |
| onPrimary on primary | 5.19 | 7.49 | 4.5 pass |
| focusRing on surface | 5.19 | 6.85 | 3.0 pass |
| outline on surface | 4.55 | 5.62 | 3.0 pass |
| danger on dangerSurface | 4.67 | 5.05 | 4.5 pass |
| success on successSurface | 4.56 | 6.25 | 4.5 pass, narrow |
| warning on warningSurface | 4.52 | 6.33 | 4.5 pass, narrow |
| info on infoSurface | 4.71 | 7.98 | 4.5 pass |
| Desktop border on surface | 1.45 | 1.42 | 3.0 **fail**; do not reuse for fields |
| textDisabled on surface | 3.04 | 2.09 | exempt; never the sole signal |

Scanner stage (#000): white 21.0, ok 8.27, fail 6.26. Light status tints sit close to 4.5:1, so do not lighten their containers.

### 3.2 Typography

| Style | sp / line | Weight | Use |
|---|---|---|---|
| displaySmall | 32 / 40 | Bold | Connection headline |
| headlineSmall | 24 / 32 | Bold | Screen titles |
| titleLarge | 20 / 28 | Bold | Result title |
| titleMedium | 18 / 26 | Medium | Product name, section headers |
| bodyLarge | 16 / 24 | Regular | Body |
| bodySmall | 14 / 20 | Regular | Supporting text |
| labelLarge | 16 / 24 | Medium (primary: Bold) | Buttons |
| labelMedium | 14 / 20 | Medium | Chips, pills |
| barcodeCode (M) | 24 / 32 | Medium, monospace, LTR | Codes, pairing code |

Nothing below 12 sp. The desktop's 11 pt is about 14.7 px at 96 DPI, so the 16 sp body is a deliberate increase.

Family: bundle Noto Sans Arabic (SIL OFL) for Arabic and use platform Roboto for Latin. Bundling pins Arabic metrics across vendor fonts, and the desktop already names this family (`tokens.py:48`). APK cost is unmeasured; measure it in the Phase 1 spike.

Numerals: Western by default. Barcodes are always ASCII, LTR and never transliterated. The desktop transliterates for display only (`numbers.py:64â€“68`) and normalises typed input only (`numbers.py:76â€“78`). A scanned Arabic-Indic payload is refused, not converted.

### 3.3 Spacing, shape, elevation, motion, icons, targets, insets

- **Space:** 4 dp base; S1â€“S8 = 4, 8, 12, 16, 24, 32, 48, 64. Screen margin 16; card padding 16 (desktop 12).
- **Shape:** 4 dp for fields and banners; 8 dp for buttons and cards (desktop 6); 12 dp for the result card (desktop 10); pill for status chips.
- **Elevation:** tonal. The desktop's single shadow (blur 6, y 2) becomes the 2 dp bottom sheet, the only shadowed surface.
- **Motion:** press 100 ms; colour change 150 ms (FastOutSlowIn); result card in 200 ms (LinearOutSlowIn), out 150 ms (FastOutLinearIn); success flash 150 ms plus 150 ms hold; scan line 1,200 ms linear loop, stopped when paused. If `Settings.Global.ANIMATOR_DURATION_SCALE` is 0, remove the sweep and flash and use 100 ms cross-fades.
- **Icons:** 18 dp chips; 24 dp lists and app bar; 32 dp scanner controls. Lucide 24-grid, 2 dp stroke, vendored as vector drawables.
- **Targets:** 48 dp minimum; 64 dp scanner controls; 56 dp setting rows; at least 8 dp between targets. The desktop's 44 px meets WCAG AAA 2.5.5 but is below Material's 48 dp.
- **Insets:** edge-to-edge. The camera fills behind the status bar; the top bar pads for status bars; the control bar pads for navigation bars plus 16 dp; no control sits in a cutout. Portrait-only in v1 (open question).

### 3.4 Mapping to the desktop

Same role names, space ladder, radius names and font families. The Android `ColorScheme` is generated from one table. Deliberate differences: 16 sp body (desktop 11 pt), 48/64 dp targets (desktop 44 px), System theme default (desktop offers only explicit Light or Dark), no hover, and three mobile-only groups (`surfaceVariant`, `outline`, scanner).

## 4. Screen specifications

Each state cites its key. English and Arabic for every key are in آ§8.

### 4.1 Connection

Purpose: install to paired in one tap. Every failure names its next action. Hierarchy: headline, one sentence, one filled action, one outlined action. Components: top app bar, viewfinder, outlined button.

```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”گ
â”‚ â†گ Pair with your till        â”‚
â”‚   â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”گ     â”‚
â”‚   â”‚  camera viewfinder â”‚     â”‚
â”‚   â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”ک     â”‚
â”‚   Point at the code on the   â”‚
â”‚   till screen.               â”‚
â”‚   [ Type code instead ]      â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”ک
```

| State | Key | Primary / secondary |
|---|---|---|
| First run (empty) | pair.title, pair.hint | Live viewfinder / Type code instead |
| Camera denied | pair.camera_needed | Allow camera; Open settings if permanently denied / Type code instead |
| Confirming on till | pair.confirm_on_till | none / Cancel (indeterminate progress) |
| Code expired or used | pair.expired | Retry / Type code instead |
| Connected | connection.connected | Opens scanner |
| Reconnecting | connection.reconnecting | Automatic backoff / none |
| Unreachable (offline) | connection.unreachable | Retry / Diagnostics |
| Revoked | connection.revoked | Pair again / none; no automatic retries |

Several tills: not in v1. One phone pairs with one till, because a scan sent to the wrong till is a financial error. "Type code instead" is in scope. Agent 4 must make the typed code self-contained (address and secret).

### 4.2 Scanner

Purpose: read at speed and show proof of delivery. Hierarchy: viewfinder, then result card, then controls. Components: connection chip, viewport, result card, 64 dp controls. Accessibility: the viewfinder is labelled "Camera, scanning" or "Camera, paused".

```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”گ
â”‚ Till 1 â—ڈ Connected آ· 12  â‹®   â”‚  12 = till-confirmed adds
â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
â”‚        â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”گ      â”‚  dashed corners; nothing
â”‚        â”‚  â”€â”€ scan â”€â”€  â”‚      â”‚  drawn over the target
â”‚        â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”ک      â”‚
â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
â”‚ â—ڈ Added Coca-Cola 330 ml     â”‚  result card
â”‚ 6221000659298          أ—2    â”‚  code in LTR
â”‚ [Add one more]               â”‚
â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
â”‚  [Torch]  [Pause]  [Zoom 1أ—] â”‚  64 dp controls
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”ک
```

| State | Key | Visual and haptic | Action |
|---|---|---|---|
| Idle (empty card) | scan.hint | Dashed frame; line sweeps | none |
| Read | scan.read | Neutral pill (transient) | none |
| Sending | scan.sending | Grey pill with progress | none |
| Added | scan.added | Green, check; flash; confirm haptic | Add one more |
| Added, merged | scan.added_qty | "now {qty}" (needs till quantity) | Add one more |
| Not found | scan.not_found | Amber, alert; double pulse; till beeps too | Scan next |
| Rejected | scan.rejected + till_error.* | Red; reject haptic | Fix on till |
| Not confirmed | scan.not_confirmed | Amber, refresh icon | Retry (same event) |
| Not sent | scan.not_sent | Red, wifi-off icon; nothing queued | Retry (new event) |
| Not a product code | scan.not_product_code | Amber; not sent | none |
| Paused | scan.paused | Scrim over target; line stops | Resume |
| No shift or read-only | scan.no_shift, scan.readonly | Banner; detection paused | Fix on till |
| Camera error | scan.camera_error | Banner | Retry |
| Low light | scan.low_light | Hint after about 10 s with no read | Torch |

"Retry (same event)" reuses the event id, so the till returns its cached outcome and cannot double-add (Agent 4). "Retry (new event)" is safe only because the code never left the phone.

Sound and vibration toggles sit in Settings, one level deep. Zoom cycles 1أ—, 2أ—, 3أ— up to the device maximum; pinch also works.

### 4.3 Settings

Purpose: change how scans feel and manage the till link. One list, one nested screen. Hierarchy: Scanner, Till, Appearance, Support. Primary action: none, since settings apply at once. Secondary: Forget.

```
Scanner```
  Barcode types                     EAN-13, EAN-8 â€¦  â€؛
  Sound                             [on]  follows silent mode
  Vibration                         [on]
  Continuous scanning               [on]
Till
  Till 1 آ· Connected
  Forget this till                  (danger)
Appearance
  Language  Arabic آ· English
  Theme     System آ· Light آ· Dark
  Numerals  Western digits (0-9)
Support
  Copy diagnostics آ· About آ· Privacy
```

Continuous scanning off pauses after each Added result. Forget asks for confirmation, has no default focus, and removes only the phone's credentials; the till keeps its record. Language defaults to the device language (Arabic or English), otherwise Arabic. Language, theme and numeral options reuse the desktop's `settings.language.*`, `settings.theme.*` and `settings.digit_style.*` strings so both products read the same; `System` is the only new option string (`mobile.theme.system`). If the till is unreachable, the Till row shows Unreachable with Retry, and no setting is blocked. A failed action shows `mobile.error.generic`.

Components: 56 dp setting rows with full-row targets, switches, radio lists for language and theme, and one danger row. Accessibility: each row is one focus stop with platform on/off state, Forget is announced as destructive, and section names are headings.

## 5. Interaction model

- **Two-tap rule.** Capturing a code takes zero taps. Pairing takes at most one tap on the phone, plus the till's confirmation. Pause, torch, zoom, Add one more and Retry take one tap each. The rule covers in-sale actions only. Forget is deliberately slower: overflow, Settings, Forget, then Confirm, and the confirm control never has default focus.
- **App open.** Paired and reachable: the scanner opens with the camera running if permission exists. Paired but unreachable: it opens paused with the banner. Unpaired: the connection screen. Without camera permission, the scanner shows the rationale and waits for Allow camera. No system prompt appears at cold launch.
- **Till unreachable.** Detection pauses, the phone retries with backoff (Agent 4), and nothing queues. When the till answers, the phone shows Connected and waits for one tap to resume, so the cashier sees the state before adding more items.
- **Recovery without a manual.** Each state names the till, gives one sentence of cause, and offers one action. No modal error dialogs appear during scanning.
- **Double scans.** Three layers. Camera stabilisation and re-arm (Agent 3) let one label fire once while it stays in view. A Sending lock stops one value queuing twice. Add one more is the explicit gesture for a genuine second unit. The till de-duplicates by event id (Agent 4). A changed code always sends at once.
- **Pause.** Pause stops detection, sound and haptics. The preview and connection stay live, and in-flight sends finish. Backgrounding pauses the scanner; resume is never automatic.
- **Proof of delivery.** Read, then Sending, then (Received, only if Agent 4's protocol separates acknowledgement from result), then Added, Not found, Rejected or Not confirmed. Not sent applies only when the phone never sent the code. Added never appears before the till says so, and Sending is never green.

## 6. Component inventory

| Component | Basis | States | Notes |
|---|---|---|---|
| Primary, secondary, destructive button | M3 filled, outlined, filled in danger colours | enabled, pressed, focused, disabled | Destructive is never default-focused |
| Scanner control | Custom on M3 tonal button | on, off, disabled | 64 dp; torch, pause, zoom |
| Status pill | Custom | neutral, pending, success, warning, danger, info | Icon, word and colour together |
| Connection chip | Custom | connected, reconnecting, offline, revoked | Top bar; progress inside |
| Banner | Custom on M3 Surface | info, warning, danger | One action; polite live region |
| Scanner viewport | Custom: CameraX PreviewView plus Canvas overlay | live, paused, no permission, error, torch | Overlay never covers the target |
| Result card | Custom on M3 Card | empty, read, sending, each outcome | Persistent; not a modal sheet |
| Setting row, switch, radio | Custom ListItem; M3 Switch and RadioButton | default, disabled, destructive | 56 dp rows |
| Empty, error, permission state | Custom | one action each | Error mirrors the desktop `ErrorBanner` |
| Pairing code | Custom, barcodeCode style | active, expired | LTR, grouped 4-4 |
| Progress | M3 indeterminate, 18 dp | in chip, during pairing | Never full-screen over the camera |
| Top app bar, snackbar | M3 defaults | title, chip, overflow | Snackbar never carries scan results |

Mostly Material 3 defaults with RawPOS roles, type and shape. Custom only where M3 has no fit: viewport, status pill, result card, pairing code and banner.

## 7. Accessibility

- **TalkBack.** Every control is labelled. Switches announce on or off through the platform. The result card is a polite live region; Assertive is used only for connection loss and revocation. Announce state changes, never every frame.
- **Codes.** Read digit by digit through a spaced content description, always LTR.
- **Focus order.** Follows the visual order: top bar, connection chip, result card, then the controls left to right, then overflow. RTL mirrors the side controls; Pause stays central in both directions.
- **Targets and contrast.** Per آ§3: at least 48 dp, text at 4.5:1 or better, non-text at 3:1 or better.
- **Never colour alone.** Every status has an icon, a word and a haptic. The frame changes shape: dashed when idle, solid after a result.
- **200% font.** Codes and names wrap to two lines and never truncate. Buttons grow. Test in both languages.
- **Reduced motion.** Honour `ANIMATOR_DURATION_SCALE` (آ§3.3).
- **Camera-free paths.** Type code instead (pairing); typed barcode entry on a numeric keypad with identical validation (open question 4 in آ§13); the till's USB scanner; torch and zoom for small or dark labels.
- **Redundant channels.** Haptics and sound support the visual state and never replace it.
- **Gloves.** Test with cotton and nitrile gloves. Some gloves do not register on capacitive screens, and 64 dp helps but does not guarantee a response.

Next: آ§8 (localisation, copy and the key table), then آ§9â€“آ§13.

---

## 8. Localization and RTL

**Mirroring.** Layouts use start and end, never left and right. Directional icons mirror: chevrons, arrows and back (precedent: `DIRECTIONAL_ICONS`, `lucide/__init__.py:15â€“17`). Torch, pause, check, barcode, camera and zoom do not mirror. Never mirrored: the camera preview, the target frame, the scan line, codes and digit strings. In the scanner's bottom bar, Pause stays centred and Torch and Zoom swap sides. In the result card, the status icon sits at the start and the action at the end.

**Digits and bidi.** Codes, pairing codes and timestamps are forced LTR at element level, each in its own `Text` with `LayoutDirection.Ltr`. Inside Arabic sentences, wrap any inserted Latin or digit value in LRI and PDI (U+2066, U+2069). Pairing-code grouping (4821, 7KQ4-9TMX) is display only and is never stored or sent. Numerals are Western by default. The optional Arabic-Indic setting changes counts, quantities and times, never identifiers (`numbers.py:5â€“7`). A scanned payload containing Arabic-Indic digits is refused, not converted (آ§3.2).

**Copy.** Sentence case. State the result, then the next step, and put the action on the button. Avoid blame and jargon: no "invalid", "socket", "token", "port" or "TLS". Use ظ†ظ‚ط·ط© ط§ظ„ط¨ظٹط¹ for "till", the singular of the desktop title (`main.window_title`). Where the meaning is identical, reuse desktop wording verbatim (marked â€ ).

**Pluralization.** `mobile.session.items` uses `<plurals>` with all six Arabic categories: zero "ظ„ط§ طھظˆط¬ط¯ ط¹ظ†ط§طµط± ظپظٹ ظ‡ط°ظ‡ ط§ظ„ط¬ظ„ط³ط©"; one "ط¹ظ†طµط± ظˆط§ط­ط¯ ظپظٹ ظ‡ط°ظ‡ ط§ظ„ط¬ظ„ط³ط©"; two "ط¹ظ†طµط±ط§ظ† ظپظٹ ظ‡ط°ظ‡ ط§ظ„ط¬ظ„ط³ط©"; few "{count} ط¹ظ†ط§طµط± ظپظٹ ظ‡ط°ظ‡ ط§ظ„ط¬ظ„ط³ط©"; many "{count} ط¹ظ†طµط±ظ‹ط§ ظپظٹ ظ‡ط°ظ‡ ط§ظ„ط¬ظ„ط³ط©"; other "{count} ط¹ظ†طµط± ظپظٹ ظ‡ط°ظ‡ ط§ظ„ط¬ظ„ط³ط©" `[needs review]`. Do not copy the desktop shortcut `audit.count` ("{count} ط­ط¯ط«", `localization/__init__.py:222`), which has no plural categories.

**Error tone.** Till errors arrive as stable codes, never text. The phone maps `till_error.<code>` and falls back to `mobile.error.generic`, mirroring `error_text` (`localization/__init__.py:1371â€“1385`). Other codes (`NoOpenShift`, `LicenseReadOnly`, `NotOwner`) reuse desktop `error.*` text verbatim.

**Key naming.** Dotted IDs (`mobile.<area>.<item>`) are cross-platform. Android resource names use underscores (`mobile_scan_added`) because R fields cannot contain dots. Placeholders stay `{name}` in the catalogue and generate to `%1$s`. `values/` and `values-ar/` must have identical key sets, enforced by a test (Constitution VI). Screen tables in آ§4â€“آ§7 omit the `mobile.` prefix.

**Language default.** Device language if Arabic or English, otherwise Arabic, the desktop default (`localization/__init__.py:11`). The phone's language stays on the phone.

**Mobile starter keys** (`values`, `values-ar`; â€  = desktop text reused)

| Key | English | Arabic |
|---|---|---|
| mobile.pair.title | Pair with your till | ط§ط±ط¨ط· ط§ظ„ظ‡ط§طھظپ ط¨ظ†ظ‚ط·ط© ط§ظ„ط¨ظٹط¹ |
| mobile.pair.hint | Point the camera at the code on the till. | ظˆط¬ظ‘ظ‡ ط§ظ„ظƒط§ظ…ظٹط±ط§ ط¥ظ„ظ‰ ط§ظ„ط±ظ…ط² ط¹ظ„ظ‰ ط´ط§ط´ط© ظ†ظ‚ط·ط© ط§ظ„ط¨ظٹط¹. |
| mobile.pair.camera_needed | Camera access is needed to read the code. | ظ†ط­طھط§ط¬ ط¥ظ„ظ‰ ط¥ط°ظ† ط§ظ„ظƒط§ظ…ظٹط±ط§ ظ„ظ‚ط±ط§ط،ط© ط§ظ„ط±ظ…ط². |
| mobile.pair.allow_camera | Allow camera | ط§ظ„ط³ظ…ط§ط­ ط¨ط§ظ„ظƒط§ظ…ظٹط±ط§ |
| mobile.pair.open_settings | Open settings | ظپطھط­ ط§ظ„ط¥ط¹ط¯ط§ط¯ط§طھ |
| mobile.pair.type_code | Type code instead | ط£ط¯ط®ظ„ ط§ظ„ط±ظ…ط² ظٹط¯ظˆظٹظ‹ط§ |
| mobile.pair.confirm_on_till | Confirm this code on the till: {code} | ط£ظƒظ‘ط¯ ظ‡ط°ط§ ط§ظ„ط±ظ…ط² ط¹ظ„ظ‰ ظ†ظ‚ط·ط© ط§ظ„ط¨ظٹط¹: {code} |
| mobile.pair.expired | This code has expired. Ask the till for a new one. | ط§ظ†طھظ‡طھ طµظ„ط§ط­ظٹط© ط§ظ„ط±ظ…ط². ط§ط·ظ„ط¨ ظ…ظ† ظ†ظ‚ط·ط© ط§ظ„ط¨ظٹط¹ ط±ظ…ط²ظ‹ط§ ط¬ط¯ظٹط¯ظ‹ط§. |
| mobile.connection.connected | Connected to {till} | ظ…طھطµظ„ ط¨ظ€{till} |
| mobile.connection.reconnecting | Reconnectingâ€¦ | ط¬ط§ط±ظچ ط¥ط¹ط§ط¯ط© ط§ظ„ط§طھطµط§ظ„â€¦ |
| mobile.connection.unreachable | Can't reach your till. Check the Wi-Fi. | طھط¹ط°ظ‘ط± ط§ظ„ظˆطµظˆظ„ ط¥ظ„ظ‰ ظ†ظ‚ط·ط© ط§ظ„ط¨ظٹط¹. طھط­ظ‚ظ‚ ظ…ظ† ط´ط¨ظƒط© Wi-Fi. |
| mobile.connection.revoked | Access was removed on the till. | طھظ… ط³ط­ط¨ ط§ظ„طµظ„ط§ط­ظٹط© ظ…ظ† ظ†ظ‚ط·ط© ط§ظ„ط¨ظٹط¹. |
| mobile.chip.connected | Connected | ظ…طھطµظ„ |
| mobile.chip.reconnecting | Reconnecting | ط¬ط§ط±ظچ ط§ظ„ط§طھطµط§ظ„ |
| mobile.chip.offline | Offline | ط؛ظٹط± ظ…طھطµظ„ |
| mobile.chip.revoked | Revoked | ط§ظ„طµظ„ط§ط­ظٹط© ظ…ط³ط­ظˆط¨ط© |
| mobile.action.retry | Retry | ط¥ط¹ط§ط¯ط© ط§ظ„ظ…ط­ط§ظˆظ„ط© |
| mobile.action.pair_again | Pair again | ط¥ط¹ط§ط¯ط© ط§ظ„ط±ط¨ط· |
| mobile.common.cancel â€  | Cancel | ط¥ظ„ط؛ط§ط، |
| mobile.error.generic | Something went wrong. Try again. | ط­ط¯ط« ط®ط·ط£ ظ…ط§. ط­ط§ظˆظ„ ظ…ط±ط© ط£ط®ط±ظ‰. |
| mobile.scan.hint | Point at a barcode | ظˆط¬ظ‘ظ‡ ط§ظ„ظƒط§ظ…ظٹط±ط§ ط¥ظ„ظ‰ ط§ظ„ط¨ط§ط±ظƒظˆط¯ |
| mobile.scan.read | Read {code} | طھظ…طھ ظ‚ط±ط§ط،ط© {code} |
| mobile.scan.sending | Sendingâ€¦ | ط¬ط§ط±ظچ ط§ظ„ط¥ط±ط³ط§ظ„â€¦ |
| mobile.scan.added â€  | Added {name} | طھظ…طھ ط¥ط¶ط§ظپط© {name} |
| mobile.scan.added_qty | Added {name}, now {qty} | طھظ…طھ ط¥ط¶ط§ظپط© {name}طŒ ظˆط§ظ„ظƒظ…ظٹط© ط§ظ„ط¢ظ† {qty} |
| mobile.scan.not_found | Unknown barcode | ط¨ط§ط±ظƒظˆط¯ ط؛ظٹط± ظ…ط¹ط±ظˆظپ |
| mobile.scan.rejected | Not added: {reason} | ظ„ظ… طھطھظ… ط§ظ„ط¥ط¶ط§ظپط©: {reason} |
| mobile.scan.not_confirmed | Not confirmed. Check the till before retrying. | ظ„ظ… ظٹطھظ… ط§ظ„طھط£ظƒظٹط¯. ط±ط§ط¬ط¹ ظ†ظ‚ط·ط© ط§ظ„ط¨ظٹط¹ ظ‚ط¨ظ„ ط§ظ„ظ…ط­ط§ظˆظ„ط©. |
| mobile.scan.not_sent | Not sent. The till is unreachable. | ظ„ظ… ظٹظڈط±ط³ظ„. ظ†ظ‚ط·ط© ط§ظ„ط¨ظٹط¹ ط؛ظٹط± ظ…طھط§ط­ط©. |
| mobile.scan.not_product_code | Not a product barcode. | ظ„ظٹط³ ط¨ط§ط±ظƒظˆط¯ ظ…ظ†طھط¬. |
| mobile.scan.add_one_more | Add one more | ط£ط¶ظپ ظˆط§ط­ط¯ط© ط£ط®ط±ظ‰ |
| mobile.scan.paused | Paused | ظ…طھظˆظ‚ظپ ظ…ط¤ظ‚طھظ‹ط§ |
| mobile.scan.pause | Pause scanning | ط¥ظٹظ‚ط§ظپ ط§ظ„ظ…ط³ط­ ظ…ط¤ظ‚طھظ‹ط§ |
| mobile.scan.resume | Resume scanning | ط§ط³طھط¦ظ†ط§ظپ ط§ظ„ظ…ط³ط­ |
| mobile.scan.torch | Torch | ط§ظ„ظ…طµط¨ط§ط­ |
| mobile.scan.zoom | Zoom {level} | ط§ظ„طھظƒط¨ظٹط± {level} |
| mobile.scan.no_shift | No open shift on the till. Open one to sell. | ظ„ط§ طھظˆط¬ط¯ ظˆط±ط¯ظٹط© ظ…ظپطھظˆط­ط© ظپظٹ ظ†ظ‚ط·ط© ط§ظ„ط¨ظٹط¹. ط§ظپطھط­ظ‡ط§ ظ„ظ„ط¨ظٹط¹. |
| mobile.scan.readonly | The till is read-only. Sales are blocked. | ظ†ظ‚ط·ط© ط§ظ„ط¨ظٹط¹ ظپظٹ ظˆط¶ط¹ ط§ظ„ظ‚ط±ط§ط،ط© ظپظ‚ط·. ط§ظ„ط¨ظٹط¹ ظ…طھظˆظ‚ظپ. |
| mobile.scan.camera_error | The camera stopped. Try again. | طھظˆظ‚ظپطھ ط§ظ„ظƒط§ظ…ظٹط±ط§. ط£ط¹ط¯ ط§ظ„ظ…ط­ط§ظˆظ„ط©. |
| mobile.scan.low_light | Too dark to read? Turn on the torch. | طµط¹ط¨ ط§ظ„ظ‚ط±ط§ط،ط©طں ط´ط؛ظ‘ظ„ ط§ظ„ظ…طµط¨ط§ط­. |
| mobile.till_error.ProductInactive | Product is deactivated and cannot be sold. | ظ‡ط°ط§ ط§ظ„طµظ†ظپ ط؛ظٹط± ظ…ظپط¹ظ‘ظ„ ظˆظ„ط§ ظٹظ…ظƒظ† طµط±ظپظ‡. `[needs review]` (desktop wording; "طµط±ظپ" may read oddly for a sale) |
| mobile.session.items | {count} items this session | see plural rule |
| mobile.settings.title â€  | Settings | ط§ظ„ط¥ط¹ط¯ط§ط¯ط§طھ |
| mobile.settings.formats | Barcode types | ط£ظ†ظˆط§ط¹ ط§ظ„ط¨ط§ط±ظƒظˆط¯ |
| mobile.settings.sound | Sound | ط§ظ„طµظˆطھ |
| mobile.settings.sound_hint | Follows silent mode | ظٹطھط¨ط¹ ظˆط¶ط¹ ط§ظ„طµط§ظ…طھ |
| mobile.settings.vibration | Vibration | ط§ظ„ط§ظ‡طھط²ط§ط² |
| mobile.settings.continuous | Continuous scanning | ط§ظ„ظ…ط³ط­ ط§ظ„ظ…ط³طھظ…ط± |
| mobile.settings.till | Connected till | ظ†ظ‚ط·ط© ط§ظ„ط¨ظٹط¹ ط§ظ„ظ…طھطµظ„ط© |
| mobile.settings.forget | Forget this till | ظ†ط³ظٹط§ظ† ظ†ظ‚ط·ط© ط§ظ„ط¨ظٹط¹ |
| mobile.settings.forget_confirm | Forget {till}? You will need to pair again. | ظ†ط³ظٹط§ظ† {till}طں ط³طھط­طھط§ط¬ ط¥ظ„ظ‰ ط¥ط¹ط§ط¯ط© ط§ظ„ط±ط¨ط·. |
| mobile.settings.language â€  | Language | ط§ظ„ظ„ط؛ط© |
| mobile.settings.theme â€  | Theme | ط§ظ„ط³ظ…ط© |
| mobile.settings.numerals â€  | Number display | ط¹ط±ط¶ ط§ظ„ط£ط±ظ‚ط§ظ… |
| mobile.settings.privacy | Scanned codes go only to your till on this Wi-Fi. Nothing goes to the internet. | طھظڈط±ط³ظ„ ط§ظ„ط±ظ…ظˆط² ط§ظ„ظ…ظ…ط³ظˆط­ط© ط¥ظ„ظ‰ ظ†ظ‚ط·ط© ط§ظ„ط¨ظٹط¹ ط¹ظ„ظ‰ ط´ط¨ظƒط© Wi-Fi ظ‡ط°ظ‡ ظپظ‚ط·طŒ ظˆظ„ط§ ظٹظڈط±ط³ظ„ ط´ظٹط، ط¥ظ„ظ‰ ط§ظ„ط¥ظ†طھط±ظ†طھ. `[needs review]` |
| mobile.settings.diagnostics | Copy diagnostics (codes redacted) | ظ†ط³ط® ظ…ط¹ظ„ظˆظ…ط§طھ ط§ظ„طھط´ط®ظٹطµ (ط§ظ„ط±ظ…ظˆط² ظ…ظڈط®ظپط§ط©) |
| mobile.settings.about | About | ط¹ظ† ط§ظ„طھط·ط¨ظٹظ‚ |
| mobile.theme.system | System | ط§ظ„ظ†ط¸ط§ظ… |

**Desktop keys** (add to `_CATALOGS` in both `ar` and `en`; reuses `common.confirm` and `error.NotOwner`)

| Key | English | Arabic |
|---|---|---|
| status.scanner.off | Phone scanner: off | ظ…ط§ط³ط­ ط§ظ„ظ‡ط§طھظپ: ظ…ط¹ط·ظ‘ظ„ |
| status.scanner.connected | Phone scanner: connected | ظ…ط§ط³ط­ ط§ظ„ظ‡ط§طھظپ: ظ…طھطµظ„ |
| status.scanner.unavailable | Phone scanner: unavailable | ظ…ط§ط³ط­ ط§ظ„ظ‡ط§طھظپ: ط؛ظٹط± ظ…طھط§ط­ |
| settings.mobile.title | Phone scanners | ظ…ط§ط³ط­ط§طھ ط§ظ„ظ‡ط§طھظپ `[needs review]` |
| settings.mobile.add | Add phone | ط¥ط¶ط§ظپط© ظ‡ط§طھظپ |
| settings.mobile.code_hint | Check that the phone shows this code: {code} | طھط­ظ‚ظ‚ ظ…ظ† ط£ظ† ط§ظ„ظ‡ط§طھظپ ظٹط¹ط±ط¶ ظ‡ط°ط§ ط§ظ„ط±ظ…ط²: {code} |
| settings.mobile.manual | Manual code: {code} | ط§ظ„ط±ظ…ط² ط§ظ„ظٹط¯ظˆظٹ: {code} |
| settings.mobile.expires | Expires in {time} | طھظ†طھظ‡ظٹ ط®ظ„ط§ظ„ {time} |
| settings.mobile.revoke | Revokeâ€¦ | ط³ط­ط¨ ط§ظ„طµظ„ط§ط­ظٹط©â€¦ |
| settings.mobile.no_network | No Wi-Fi or Ethernet address found. Connect to a network to pair phones. | ظ„ظ… ظٹظڈط¹ط«ط± ط¹ظ„ظ‰ ط¹ظ†ظˆط§ظ† ط´ط¨ظƒط©. ط§طھطµظ„ ط¨ط´ط¨ظƒط© Wi-Fi ط£ظˆ ط¨ط§ظ„ط¥ظٹط«ط±ظ†طھ ظ„ط±ط¨ط· ط§ظ„ظ‡ظˆط§طھظپ. `[needs review]` |
| settings.mobile.port_blocked | Windows Firewall blocks the scanner port on private networks. | ط¬ط¯ط§ط± ط­ظ…ط§ظٹط© Windows ظٹظ…ظ†ط¹ ظ…ظ†ظپط° ط§ظ„ظ…ط§ط³ط­ ط¹ظ„ظ‰ ط§ظ„ط´ط¨ظƒط§طھ ط§ظ„ط®ط§طµط©. `[needs review]` |
| settings.mobile.read_only | Read-only mode: pairing is unavailable. | ظˆط¶ط¹ ط§ظ„ظ‚ط±ط§ط،ط© ظپظ‚ط·: ط§ظ„ط±ط¨ط· ط؛ظٹط± ظ…طھط§ط­. |
| settings.mobile.reason.lost | Lost or stolen | ظ…ظپظ‚ظˆط¯ ط£ظˆ ظ…ط³ط±ظˆظ‚ |
| settings.mobile.reason.replaced | Phone replaced | طھظ… ط§ط³طھط¨ط¯ط§ظ„ ط§ظ„ظ‡ط§طھظپ |
| settings.mobile.reason.left | Staff member left | ط؛ط§ط¯ط± ط§ظ„ظ…ظˆط¸ظپ `[needs review]` |
| settings.mobile.col_device | Device | ط§ظ„ط¬ظ‡ط§ط² |
| settings.mobile.col_last | Last scan | ط¢ط®ط± ظ…ط³ط­ |

Also add `sales.scan.from_phone` ("{message} (phone)" / "{message} (ظ…ظ† ط§ظ„ظ‡ط§طھظپ)") and audit events `audit.event.device.paired` ("Phone scanner paired" / "طھظ… ط±ط¨ط· ظ…ط§ط³ط­ ظ‡ط§طھظپ") and `audit.event.device.revoked` ("Phone scanner removed" / "طھظ…طھ ط¥ط²ط§ظ„ط© ظ…ط§ط³ط­ ظ‡ط§طھظپ").

## 9. Desktop pairing experience

**Entry points.** One dialog, two entries. A permanent status-bar button beside the shift button (`main_window.py:147â€“153`) shows `status.scanner.*` in role colours (success, warning, neutral), so it is visible from every module. A Settings button opens the same dialog. Neither entry takes keyboard focus from Sales.

**Layout.** Built on the shared `Dialog` base (`dialog.py:19â€“38`), about 480 px wide.

```
Phone scanners
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”گ  Check that the phone shows this code: 4821
â”‚   QR 240 px  â”‚  Expires in 1:58   [New code]   [Confirm]
â”‚              â”‚  Manual code: 7KQ4-9TMX
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”ک
Device          Status      Last scan
Ahmed's phone   Connected   12:04            [Revokeâ€¦]
                              [Add phone]   [Close] default
```

**QR and codes.** The QR is 240 logical px with a four-module quiet zone, black on white in both themes. Add two tokens, `qr_foreground` and `qr_background`, because inverted codes scan poorly and views must not hard-code hex (`tokens.py:3â€“4`). The manual code uses `family_mono` (`tokens.py:49`), defined but unused today.

**Pairing and revoke.** "Add phone" opens Agent 4's 120 s window with the QR, the code and a countdown. On expiry the code area offers "New code". The owner checks that the phone shows the same code and presses Confirm. Closing the dialog cancels the window. Phones sit in the shared `DataTable` (`data_table.py:83`), its first production use. Revoke needs a selected row, uses the danger role (`dialog.py:107`), and opens a `Dialog` with three radio reasons and no free text. Cancel is the default. Revocation takes effect on the phone's next contact (Agent 4).

**States.** Receiver off (default); starting; running with N phones; no network (warning banner); port blocked (danger banner); read-only (pairing refused); recovery (receiver refuses); not owner (`error.NotOwner`). The receiver runs only while a phone is paired or a window is open (Agent 4 to confirm).

**Focus and keyboard safety.**
- Open with `show()`, not `exec()`. A spike confirms that `WA_ShowWithoutActivating` keeps keyboard focus in `scan_edit` (`checkout_view.py:91`).
- No text fields. The wedge filter ignores keystrokes from other windows (`checkout_view.py:294â€“302`), so a stray scan typed into a dialog field would be lost.
- Tab order: list, New code, Confirm, Add phone, Close. Esc closes. Close is the explicit default, so Enter never revokes.
- On close, return focus with `scan_edit.setFocus()`, as `_remove_line` does (`checkout_view.py:430`).
- Receiver state lives in a service. Language and digit switches rebuild the window (`app_shell.py:369â€“417`), so the dialog closes on remount and the window continues in the service.

**Phone scans at the till.** A phone scan calls a barcode-only seam, not `scan_edit`. `_wedge_route` writes and submits that field (`checkout_view.py:333â€“342`), so reusing it would clobber a half-typed code. The status line appends the source and keeps the existing not-found beep (`checkout_view.py:454`). No new animation.

**Light, dark, RTL.** Colours come from roles. Banners use `FeedbackBanner` severities (`feedback_banner.py:10`). `apply_direction` sets the layout (`rtl.py:10â€“14`); the button box follows it; codes stay LTR; text columns follow direction (`rtl.py:17â€“45`).

**Owner rule and audit.** Pair, New code and Revoke are owner-only (آ§13, question 2). Cashiers see status and the list. Pair and revoke write `audit.event.device.*` entries. This dialog is the first real user of `Dialog`, `DataTable` and `FeedbackBanner`, none of which has a production caller today (آ§1). Qt has no QR encoder, so the QR needs a stdlib encoder or a pinned package under the constitution's dependency rule. Agent 4 owns that choice.

## 10. Motion and feedback

| Event | Visual | Haptic | Sound (if on) |
|---|---|---|---|
| Pairing succeeds | Green check fades in over 300 ms, then scanner | CONFIRM | Two-note chime, under 250 ms |
| Added | Target flashes success 150 ms, holds 150 ms; card enters in 200 ms | CONFIRM | Short high tone, about 80 ms |
| Not found | Card turns warning in 150 ms; target stays solid | Two 30 ms pulses, 60 ms apart | Low tone, as the till beeps |
| Rejected or not a product code | Card turns danger or warning in 150 ms | REJECT | Low tone, distinct from Not found |
| Not confirmed, not sent | Card turns warning or danger in 150 ms; no loop | One 30 ms pulse | None |
| Connection lost | Banner enters in 250 ms; one assertive announcement | One 30 ms pulse | None |
| Connection restored | Banner leaves in 200 ms; Resume appears | None | None |

- **Never animated:** codes (no count-up or typing effect), the sale count, result text, error messages (no shake), the viewfinder, the target frame while detection runs, and the countdown, which updates as text only. The success flash is a single 150 ms flash, well under WCAG 2.3.1's three-per-second limit.
- **Never blocking:** feedback never pauses detection. Animations run beside it.
- **Silent mode:** sound follows the ringer, and Settings shows "Follows silent mode" under Sound. A silent phone plays no sound, but haptics and visuals continue. The Vibration switch governs haptics only. The row is hidden on devices without a vibrator.
- **Reduced motion:** when `ANIMATOR_DURATION_SCALE` is 0, remove the sweep, the flash and card motion. State changes use a 100 ms cross-fade. Colour and text do not change.
- **Haptic constants:** API 30+ `CONFIRM` and `REJECT`, with predefined effects below that (external; verify in Phase 1).
- **Desktop:** no new animation. Phone outcomes use the existing status line and beep.

## 11. Design deliverables and QA

**Phase 1 artefacts**
1. **Token source:** one file (آ§3) generating colour, type, shape, dimension and motion constants, plus `qr_foreground` and `qr_background` (آ§9).
2. **Theme:** `RawPosTheme` with Material 3 schemes for Light, Dark and System. Dynamic colour is off, so the RawPOS palette holds on Android 12+.
3. **Icons:** vendored Lucide vector drawables with the ISC licence file: scan-barcode, qr-code, camera, zap, zap-off, pause, play, zoom-in, check, circle-check, circle-alert, triangle-alert, circle-x, refresh-cw, wifi, wifi-off, link, unlink, settings, languages, smartphone, sun, moon, volume-2, volume-x, vibrate, vibrate-off, copy, trash-2, info, arrow-left (mirrored). Verify each name against the vendored release.
4. **Component previews:** every آ§6 component and each آ§4 state, in Light and Dark, LTR and RTL, at font scale 1.0 and 2.0.
5. **Screen specs and catalogues:** آ§4â€“آ§7 versioned beside the code, with `values/strings.xml` and `values-ar/strings.xml`.

**Design QA without a designer**
- **Automated:** a test recomputes contrast from the token table (آ§3.1) and fails below threshold. A parity test fails on any missing `values-ar` key. Android Lint `MissingTranslation` is blocking. Compose semantics tests assert bounds of at least 48 dp and a label on every clickable node. A CI check blocks hex literals outside the token file.
- **Evidence:** per-state screenshots stored with each build and reviewed by a person. No pixel-diff library until one passes the constitution's dependency test.
- **Human review:** a native Arabic speaker checks every `[needs review]` string and each RTL screen. Run Accessibility Scanner on every screen, a TalkBack walkthrough at 200% font, and Agent 6's manual scripts with a non-developer cashier.
- **Field check:** a shop trial with gloves, torch on and off, and a 30-minute session for heat and battery.
- Agent 6 records every artefact. No design pass is claimed without one.

## 12. Risks and alternatives

| Decision | Chosen | Alternative | Risk and mitigation |
|---|---|---|---|
| Toolkit | Compose with Material 3 | XML Views | Recomposition cost on low-end phones: stable state, no per-frame state, measured on the low-end device |
| Brand | M3 components with RawPOS roles, type and shape | Fully custom components | M3 can look generic: customise only the آ§6 custom list |
| Themes | Light and Dark, System default | Dark only | Dark only breaks parity with the desktop; tokens and previews keep the second theme cheap |
| Arabic font | Bundle Noto Sans Arabic | System Arabic | APK cost unmeasured; vendor metrics vary: pin metrics and check the budget |
| Overlay | Outline frame; nothing drawn over the target | Full-screen HUD with text on the camera | Hides codes and shrinks the decode area: a layout test fails if any overlay child intersects the target |
| One-handed reach | Controls in the bottom third; Pause centred | Controls at the top | Large phones are hard to reach: keep only the read-only chip and overflow at the top |
| Gloves | 64 dp controls, high contrast | Glove mode with larger targets | Capacitive gloves may not register: test real gloves; keep the USB scanner and typed fallback |
| Low-end rendering | Flat surfaces, one 2 dp shadow, one animated line | Full motion design | Dropped frames: remove the sweep first, never the feedback |

## 13. Verified, assumed, dependencies and open questions

**Verified in the repo:** token values and roles; QSS and widget behaviour; no production callers of the shared kit (grep); `family_mono`, `radius_lg`, `radius_pill` and `size_display` unused; 27 Lucide icons under ISC; no fonts, logos or brand files; Constitution Principles II and VI and the devices section; `numbers.py` digit rules; the `error.*` keys used for mapping. Contrast ratios in آ§3.1 were computed in this review.

**Assumed or external, verify in Phase 1:** Compose and Material 3 defaults (48 dp, state layers); `ANIMATOR_DURATION_SCALE`; edge-to-edge at targetSdk 35+; haptic constants; Noto Sans Arabic licence (OFL) and APK cost; Roboto as the Latin fallback; Lucide names; Arabic copy and plural forms; real shop lighting.

**Dependencies**
- **Agent 2:** Compose and Material 3 baseline; background and language-change lifecycle; permission flows; insets; minSdk; APK budget; the low-end test device.
- **Agent 6:** test IDs per state; screenshot and accessibility evidence; the full device matrix; manual scripts.
- **Agents 3 and 4:** status codes, thresholds, typed-code contents and the pairing window (آ§4, آ§9).

**Brand assets.** The repo has none: no logo, app icon, brand colour document or font file. Before Phase 1, supply a logo, or confirm that the desktop palette and Noto Sans Arabic are the base. The adaptive icon needs a foreground and a background layer.

**Device matrix (design view).** Smallest 360أ—640 dp; common 412أ—915 dp; tall 360أ—800 dp (20:9); font scale 1.0 and 2.0; gesture navigation with a display cutout; Arabic and English locales; one low-end and one mid-range phone. Agent 6 owns the full matrix.

**Open questions**
1. Brand: is there a logo, brand colour or type standard outside the repo?
2. Who may pair and revoke: owner only (recommended) or any signed-in user?
3. Portrait-only scanner in v1 (recommended)?
4. Typed barcode fallback on a numeric keypad: allow it (recommended), or require the USB scanner?
5. Sound follows the ringer (recommended), or stays independent?
6. After an outage, resume manually (recommended) or automatically?
7. Phone numerals setting in v1 (Constitution VI implies yes)?
8. Revoke requires a reason (recommended)?
9. Privacy wording: legal review before release?
10. Add System to the desktop theme options?
11. Which phone models are in the shop? That fixes the device matrix.