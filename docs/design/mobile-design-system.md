# Mobile Design System

**Status:** proposed
**Basis:** verified RawPOS desktop tokens and widgets at HEAD `797167a`, plus the independent UI/UX review
**Related:** `mobile-ux-specification.md`, `../agents/agent-5-uiux.md`

---

## 1. What the desktop actually is

The desktop has a real design system, not an aspiration:

- 25 semantic color roles for light and dark (`src/rawpos/ui/theme/tokens.py:17-43`, `:168-222`);
- one QSS generator (`ui/theme/qss.py`), persisted by `ThemeManager` (`theme_manager.py:63-90`);
- a 4 px spacing ladder 4–64, radius 4/6/10, control heights 32/44/56, touch target 44;
- a stone type ramp (Segoe UI / Tahoma / Noto Sans Arabic; body 11 pt, heading 13, title 16, display 20);
- a Lucide icon set (27 SVGs, ISC);
- shared widgets: buttons, card, data table, dialog, empty/loading/error/feedback states, section
  header, text/number fields, RTL helpers.

It also has verified gaps that we must not copy:

| Gap | Evidence | Consequence |
|---|---|---|
| Input border contrast is 1.45:1 (light) / 1.42:1 (dark) | `tokens.border` on `surface` | Fails WCAG 1.4.11 (3:1) for fields; mobile uses a stronger `outline` |
| Status is often a plain label | `checkout_view.py` status label | Success and error look alike; mobile uses icon + word + color |
| Low stock is color-only | `line_grid.py:145-151` | Not accessible; mobile shows no stock, so not applicable |
| Shared widget kit is largely unadopted | no production `PrimaryButton`/`Card`/`FeedbackBanner` construction | Desktop pairing dialog must choose its primitives deliberately |
| 44 px touch targets | `tokens.touch_target_min` | Below Material's 48 dp; mobile uses 48/64 |
| 11 pt body | `tokens.typography.size_md` | Too small for a phone; mobile uses 16 sp |

The 014 audit documents are partly stale: they say no theme system exists, but the code now has one.
This document follows the code, not the stale prose.

---

## 2. Brand thesis

**Point. See the till confirm. Move on.**

The phone shares RawPOS's identity — role names, blue primary, neutral surfaces, Western digits,
Arabic-first mirroring — but not its density or input model. It is a scanner, not a small POS.

Principles:

1. **Proof, not optimism.** "Added" appears only after the till confirms.
2. **Zero taps to scan.** Capture is automatic; mid-sale actions take at most two taps.
3. **Built for gloves, glare, and one hand.** 48 dp targets, 64 dp scanner controls, 4.5:1 text.
4. **Noisy floors, silent phones.** Every outcome has a visual state and, if enabled, a haptic.
5. **Never guess a code.** Non-digit payloads are refused on the phone.
6. **Arabic-first.** Start/end layouts, LTR digit runs, Western numerals.
7. **One phone, one till.** The till name is always visible.
8. **Long shifts on old phones.** No blur; the only continuous animation is the scan line, and it
   stops when paused.

---

## 3. Color

Names mirror the desktop `ColorTokens`. `(M)` marks mobile-only roles.

| Role | Light | Dark |
|---|---|---|
| `background` | `#f5f6f8` | `#0d1117` |
| `surface` | `#ffffff` | `#161b22` |
| `surfaceVariant` (M) | `#eaeef2` | `#21262d` |
| `textPrimary` | `#1f2328` | `#e6edf3` |
| `textSecondary` | `#57606a` | `#8b949e` |
| `textDisabled` | `#8c959f` | `#484f58` |
| `border` (dividers only) | `#d0d7de` | `#30363d` |
| `outline` (M; fields, switches) | `#6e7781` | `#8b949e` |
| `focusRing` | `#0969da` | `#58a6ff` |
| `primary` | `#0969da` | `#58a6ff` |
| `primaryPressed` | `#033d8b` | `#a5d6ff` |
| `onPrimary` / `onStatus` | `#ffffff` | `#0d1117` |
| `success` / `successSurface` | `#1a7f37` / `#dafbe1` | `#3fb950` / `#12261e` |
| `warning` / `warningSurface` | `#9a6700` / `#fff8c5` | `#d29922` / `#272115` |
| `danger` / `dangerSurface` | `#cf222e` / `#ffebe9` | `#f85149` / `#2d1618` |
| `info` / `infoSurface` | `#0e7490` / `#ddf4ff` | `#2dd4bf` / `#102a3a` |
| `scanner.stage` (M) | `#000000` | `#000000` |
| `scanner.target` (M, 80% alpha) | `#ffffff` | `#ffffff` |
| `scanner.ok` / `scanner.fail` (M) | `#3fb950` / `#f85149` | same, fixed on stage |

Notes:

- `surfaceVariant` is the disabled container / grouped background.
- `low_stock` is not used: the phone never shows stock.
- `primaryHover` is replaced by Material state layers; hover does not exist on touch.
- `scanner.stage` is pure black to maximize camera contrast and hide preview letterboxing.

### 3.1 Contrast (computed with WCAG 2.x, re-verified)

| Pair | Light | Dark | Requirement |
|---|---:|---:|---|
| textPrimary on surface | 15.80 | 14.64 | 4.5 ✅ |
| textSecondary on surface | 6.39 | 5.62 | 4.5 ✅ |
| onPrimary on primary | 5.19 | 7.49 | 4.5 ✅ |
| focusRing on surface | 5.19 | 6.85 | 3.0 ✅ |
| outline on surface | 4.55 | 5.62 | 3.0 ✅ |
| danger on dangerSurface | 4.67 | 5.05 | 4.5 ✅ |
| success on successSurface | 4.56 | 6.25 | 4.5 ✅ (narrow) |
| warning on warningSurface | 4.52 | 6.33 | 4.5 ✅ (narrow) |
| info on infoSurface | 4.71 | 7.98 | 4.5 ✅ |
| desktop border on surface | 1.45 | 1.42 | 3.0 ❌ — do not use for fields |
| textDisabled on surface | 3.04 | 2.09 | exempt; never the sole signal |
| white on scanner stage | 21.0 | 21.0 | ✅ |
| scanner.ok on stage | 8.27 | 8.27 | ✅ |
| scanner.fail on stage | 6.26 | 6.26 | ✅ |

`success`/`warning` on their surfaces are close to the 4.5 threshold. Do not lighten those containers,
and do not put small text in them without re-checking.

---

## 4. Typography

| Style | sp / line | Weight | Use |
|---|---|---|---|
| `displaySmall` | 32 / 40 | Bold | Connection headline |
| `headlineSmall` | 24 / 32 | Bold | Screen titles |
| `titleLarge` | 20 / 28 | Bold | Result title |
| `titleMedium` | 18 / 26 | Medium | Section headers |
| `bodyLarge` | 16 / 24 | Regular | Body |
| `bodySmall` | 14 / 20 | Regular | Supporting text |
| `labelLarge` | 16 / 24 | Medium (primary actions Bold) | Buttons |
| `labelMedium` | 14 / 20 | Medium | Chips, pills |
| `barcodeCode` (M) | 24 / 32 | Medium, monospace, LTR | Codes, pairing code |

- Nothing below 12 sp.
- Latin: platform Roboto. Arabic: **bundle Noto Sans Arabic** (SIL OFL). Bundling pins metrics across
  vendor fonts; the desktop already names this family (`tokens.py:48`). Measure the APK cost in the
  Phase 1 spike.
- Numerals: **Western** by default, matching RawPOS's default `digit_style`. Barcodes are always ASCII,
  LTR, and never transliterated. Arabic-Indic input is rejected, not converted.
- Line height 1.5 for Arabic body text (diacritics and descenders need it).

---

## 5. Spacing, shape, elevation, motion, icons, targets

- **Spacing:** 4 dp base; 4, 8, 12, 16, 24, 32, 48, 64. Screen margin 16; card padding 16.
- **Shape:** 4 dp fields/banners; 8 dp buttons/cards; 12 dp result card; pill for status chips.
- **Elevation:** tonal, not shadow-heavy. A single 2 dp bottom sheet is the only shadowed surface.
- **Motion:**
  - press 100 ms;
  - color change 150 ms (FastOutSlowIn);
  - result card in 200 ms (LinearOutSlowIn), out 150 ms (FastOutLinearIn);
  - success flash 150 ms + 150 ms hold;
  - scan line 1,200 ms linear loop, stopped when paused;
  - if `ANIMATOR_DURATION_SCALE == 0`, remove sweep/flash and use 100 ms cross-fades.
- **Icons:** Lucide 24-grid, 2 dp stroke, vendored as vector drawables. 18 dp chips, 24 dp lists/app bar,
  32 dp scanner controls.
- **Targets:** 48 dp minimum; 64 dp scanner controls; 56 dp setting rows; ≥ 8 dp between targets.
- **Insets:** edge-to-edge. Camera fills behind the status bar; top bar pads for the status bar;
  controls pad for the navigation bar + 16 dp; no control in a cutout.
- **Orientation:** portrait-first. Rotation is supported with a camera rebind, but the scanner layout
  is designed for one hand in portrait. Do not lock orientation in v1 unless testing shows a duplicate
  risk that cannot be fixed.

---

## 6. Mapping to the desktop

| Desktop | Mobile | Why |
|---|---|---|
| 25 semantic color roles | same names + `outline`, `surfaceVariant`, scanner roles | brand continuity |
| 4 px spacing ladder | same | continuity |
| radius 4/6/10 | 4/8/12 | touch-friendly |
| 11 pt body | 16 sp body | phone reading distance |
| 44 px target | 48/64 dp | Material/accessibility/gloves |
| Light + Dark explicit | System default, plus Light/Dark overrides | phone-wide setting |
| Hover states | state layers / pressed | no pointer |
| Left module list, 9-column grid | single-purpose screens | different job |
| Keyboard-first | camera-first, touch-first | different input |

The mobile theme is generated from one token table (`MobileTokens.kt`) and mapped into
`MaterialTheme(colorScheme, typography, shapes)`. No hard-coded colors in composables.

---

## 7. Component inventory

| Component | Basis | States | Notes |
|---|---|---|---|
| Primary / secondary / destructive button | M3 filled / outlined / filled-danger | enabled, pressed, focused, disabled | Destructive never default-focused |
| Scanner control | Custom on M3 tonal button | on, off, disabled | 64 dp: torch, pause, zoom |
| Status pill | Custom | neutral, pending, success, warning, danger, info | Icon + word + color |
| Connection chip | Custom | connected, reconnecting, offline, revoked | Top bar; progress inside |
| Banner | Custom on M3 Surface | info, warning, danger | One action; polite live region |
| Scanner viewport | CameraX `PreviewView` + Canvas overlay | live, paused, no permission, error, torch | Overlay never covers the target |
| Result card | Custom on M3 Card | empty, read, sending, each outcome | Persistent, not a modal sheet |
| Setting row / switch / radio | Custom ListItem + M3 Switch/RadioButton | default, disabled, destructive | 56 dp rows |
| Empty / error / permission state | Custom | one action each | Mirrors desktop `ErrorBanner` tone |
| Pairing code | Custom `barcodeCode` | active, expired | LTR, grouped 4-4 |
| Progress | M3 indeterminate 18 dp | in chip, during pairing | Never full-screen over the camera |
| Top app bar / snackbar | M3 defaults | title, chip, overflow | Snackbar never carries scan results |

Custom only where Material 3 has no fit: viewport, status pill, result card, pairing code, banner.

---

## 8. Accessibility

- **TalkBack:** every control labelled; switches announce on/off via the platform.
- **Live regions:** result card is polite; connection loss/revocation is assertive.
- **Codes:** read digit by digit via a spaced content description, always LTR.
- **Focus order:** top bar → connection chip → result card → controls left-to-right → overflow. In RTL,
  side controls mirror; Pause stays central.
- **Color is never the only signal:** icon + word + haptic; the frame changes shape (dashed idle, solid
  after a result).
- **200% font:** codes and names wrap to two lines, never truncate; buttons grow.
- **Reduced motion:** honour `ANIMATOR_DURATION_SCALE`.
- **Camera-free paths:** typed pairing code; typed barcode entry (open question); the till's USB
  scanner; torch/zoom.
- **Gloves:** test cotton and nitrile; 64 dp helps but capacitive gloves are not guaranteed.

---

## 9. Theme behavior

- Default: follow the system setting.
- User override: System / Light / Dark (desktop currently offers only Light/Dark; `System` is a mobile
  addition).
- Changing the theme must not stop the camera, drop the connection, or reset scan state.
- The scanner stage stays black in both themes; only the chrome around it changes.
- Test both themes with Arabic and English, at 100% and 200% font.

---

## 10. Do / don't

**Do**

- Use semantic tokens, never hex literals in composables.
- Show the till name and connection state at all times.
- Keep the last code and last outcome visible until the next scan.
- Use one large total on the phone only if a total is ever shown — it is not, in v1.
- Respect the system back gesture; never trap the user in the scanner.

**Don't**

- Copy 11 pt body, 44 px targets, hover states, or the sidebar model.
- Show product names, prices, cart contents, or totals.
- Animate a success state before the desktop confirms.
- Use color alone for status.
- Put a modal over the camera during scanning.
- Add a "trust anyway" path for a certificate mismatch.

---

## 11. Implementation notes (Phase 1)

- `android/app/src/main/java/.../ui/theme/MobileTokens.kt` — the single token source.
- `Theme.kt` — light/dark `ColorScheme`, `Typography`, `Shapes`.
- `Type.kt` — Noto Sans Arabic + Roboto family, line heights.
- `Dimens.kt` — spacing, targets, radii, motion durations.
- Component previews via `@Preview` for every state in §7, both themes, `ar` and `en`.
- A tiny screenshot test (Roborazzi or Paparazzi) is optional; if it adds a dependency, defer to Phase 6.
- Design QA without a designer: a checklist derived from §3–§8, plus a manual review on the device
  matrix. Arabic copy requires a native reviewer.

---

## 12. Open items

1. Brand assets: the repo has **no logo, icon, or font file**. Is there an official RawPOS mark or brand
   color outside the code? If not, use the token `primary` and a neutral wordmark.
2. Bundle Noto Sans Arabic vs rely on the device font: bundle for consistency; measure APK size.
3. Portrait-only vs rotation: recommend portrait-first with rotation supported.
4. Typed barcode entry as a camera-free path: useful for damaged labels, but it expands the scan input
   surface. Recommend yes, with the same digits-only validation and a clear "typed" marker in diagnostics.
5. Theme default: System vs Light. Recommend System.
6. Desktop pairing dialog primitives: use the existing `Card`/`FeedbackBanner`/`PrimaryButton` kit
   (first production adoption) or style raw widgets with the same QSS roles. Recommend the kit, with a
   smoke test, to avoid a one-off screen.
