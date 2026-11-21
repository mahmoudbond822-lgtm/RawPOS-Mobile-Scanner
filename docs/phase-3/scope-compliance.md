# Phase 3 — Scope Compliance Report

**Authorization:** Phase 3 (Android camera and barcode scanning), A18, owner-approved 2026-11-21.
**Repository:** this one only. The RawPOS repository is untouched at `7efa178`, clean tree.
**Result:** compliant on scope. No network, no pairing client, no checkout integration, no `INTERNET`
permission.

---

## 1. Approved scope vs delivered

| Approved deliverable (`implementation-roadmap.md` Phase 3) | Delivered | Evidence |
|---|---|---|
| CameraX `Preview` + `ImageAnalysis` | Bound to the lifecycle, released on dispose | `ScannerScreen.kt`, `dumpsys media.camera` log |
| Bundled ML Kit barcode scanning | `barcode-scanning` 17.3.0, formats restricted to the enabled set | `BarcodeAnalyzer.kt`, `BarcodeDecodingTest` |
| `ScanGate` — stability, cooldown, re-arm, one in flight | 2 frames, 150 ms interval, 500 ms absence, latched value | `ScanGate.kt`, 13 tests |
| `Validation` — predicate and rejection reasons | `^[0-9]{1,64}$`, four typed reasons, no trimming or normalisation | `BarcodeValidator.kt`, 11 tests |
| Scanner screen — viewport, target, torch, pause, zoom, result card, states | All present, plus a permission panel | `ScannerScreen.kt`, `ScannerScreenTest` |
| Feedback — sound, haptics, reduced motion | Haptic + beep, silent-mode aware, opt-out setting. **No motion: nothing animated to suppress** | `fireScanFeedback`, settings switch |
| `FakeTransport` + test doubles | `FakeTransport` in the test source set; time passed explicitly instead of a `FakeClock` | `FakeTransport.kt`, `ScannerViewModelTest` |
| Instrumented camera smoke with fixture images | `BarcodeDecodingTest` decodes a generated EAN-13 on the device; `ScannerScreenTest` asserts the screen and controls | 3 instrumented tests, all passing |

---

## 2. Exclusions — verified

| Exclusion | Verification | Result |
|---|---|---|
| No network | Manifest has no `INTERNET` permission; the transport is `null` in Phase 3 | Pass |
| No pairing client | No pairing code in the Android project | Pass |
| No checkout integration | No RawPOS reference of any kind | Pass |
| No RawPOS modification | RawPOS `HEAD 7efa178`, clean tree | Pass |
| No release artifact | Debug APK only | Pass |
| No business logic on the phone | No price, stock, or product data; the screen shows a barcode and a status | Pass |

---

## 3. Gates — measured

| Gate | Command | Result |
|---|---|---|
| Build | `.\gradlew.bat --no-daemon assembleDebug` | `BUILD SUCCESSFUL` |
| Unit tests | `.\gradlew.bat --no-daemon testDebugUnitTest` | **45 tests**, 0 failed |
| Lint | `.\gradlew.bat --no-daemon lintDebug` | 0 errors, 18 warnings (all version notices) |
| Instrumented | `am instrument` on the device | **3 tests**, 0 failed, 5.75 s |

Unit-test breakdown: 10 from Phase 1, 11 validator, 13 gate, 2 transport, 8 scanner view model,
4 settings. Instrumented: launch smoke, ML Kit decode, scanner screen.

---

## 4. Device verification

| Item | Value |
|---|---|
| Device | POCO X3 NFC, `M2007J20CG`, Android 12, `surya_global` |
| Preview, one scan per held label, second scan after re-presenting | owner-observed |
| Two labels → "Frame one barcode", torch, pause | owner-observed |
| Camera bind/unbind | `dumpsys media.camera` CONNECT/DISCONNECT cycle |
| ML Kit decoding | generated EAN-13 decoded on the device in an automated test |

The manual observations are recorded as **owner-observed**, not agent-measured. The decoding result is
automated and reproducible.

---

## 5. Deviations and judgment calls

1. **Instrumented runs bypass the Gradle task.** MIUI refused USB installs
   (`INSTALL_FAILED_USER_RESTRICTED`) and blocks `pm grant` and `settings put system`. The APKs were
   therefore installed from the device's file manager and the suite was run with `am instrument`.
   **This is a workaround, not a fix**; `connectedDebugAndroidTest` remains unusable on this phone until
   *Install via USB* is enabled.
2. **CameraX 1.6.2 and ML Kit 17.3.0.** Both sit inside D21's proposed families and inside A18's scope.
   Pinned in `libs.versions.toml`.
3. **No `FakeClock` class.** Time is an explicit parameter on every gate call, which is stricter than a
   fake clock: it cannot be forgotten or manipulated globally.
4. **Reduced motion** has nothing to act on, because no feedback is animated. If animation is added, it
   must consult the system setting; that is recorded rather than silently skipped.
5. **720p / ~10 fps tuning was not implemented.** The analyzer uses CameraX defaults. Measurements belong
   to Phase 6, and claiming a frame rate without one would be a fabrication.
6. **APK is 36.8 MB in debug** with the bundled model. Release size is a Phase 8 measurement after R8.
7. **Rejection rendering is unverified on the device.** The state logic is unit-tested; no non-numeric or
   disabled symbol was presented on the phone.

---

## 6. Acceptance criteria

| Criterion | Status |
|---|---|
| One physical label produces one event while held | **met** — owner-observed + gate tests |
| A re-presented label after re-arm produces a second | **met** — owner-observed + gate tests |
| Non-digit / alphanumeric / QR rejected with the right message | **partly** — logic tested, display unobserved |
| Leading zeros preserved | **met** — unit test |
| No network call when offline | **met** — no `INTERNET` permission, transport `null` |
| Camera releases on background/pause | **met** — device log + dispose unbind |

Five of six met outright; the sixth is met in logic and unverified in presentation.

---

## 7. What is not proven

- A rejection sentence rendered on the phone.
- Real-label accuracy under low light, motion, or a dirty lens.
- Frame rate and battery behaviour (Phase 6).
- The Arabic copy of 22 scanner and settings strings (Q22).
- Real shop data: the pre-pilot label capture remains a blocking gate for any deployment (R13), and
  A10 is still unsigned.

---

## 8. Open items

1. **Install via USB** — enable it, or Phase 4 instrumented runs must keep using the manual path.
2. **Rejection display** — present a non-numeric label and confirm the sentence appears.
3. **Arabic review** (Q22).
4. **Frame-rate and battery measurement** (Phase 6).
5. **Release APK size** (Phase 8).
6. **A10 and the pre-pilot capture** — before any shop pilot.

---

## 9. Inventory

- **New source:** `core/barcode/{BarcodeFormat,BarcodeValidator,ScanGate,DecodedBarcode}.kt`,
  `core/camera/BarcodeAnalyzer.kt`, `core/transport/ScanTransport.kt`.
- **New tests:** 4 unit test classes, 2 instrumented test classes, 1 checked-in EAN-13 fixture.
- **Modified:** `ScannerScreen.kt`, `ScannerViewModel.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`,
  `SettingsRepository.kt`, `AndroidSettingsRepository.kt`, `RawPosNavHost.kt`, `AndroidManifest.xml`,
  `libs.versions.toml`, `build.gradle.kts`, both string catalogs.
- **Commits:** `99de3db` logic layer, `9f10261` camera + screen, `368e958` device decode test,
  `091e453` manual check, `54f99db` feedback + zoom + smoke test.
- **RawPOS:** unchanged at `7efa178`.

---

## 10. Closure statement

Phase 3 delivered the camera, the decoder, the duplicate-suppression gate, the validation predicate, the
screen, feedback, zoom, and three passing instrumented tests on real hardware. It added exactly the
permissions and dependencies the camera needs — and no network permission.

The phone can now scan a real label, refuse a second reading until the label leaves the frame, refuse
ambiguity, and confirm with a beep and a tick.

**Phase 3 is complete as an Android phase. Phase 4 has not started: A12 is unsigned, and the scan
protocol needs its own authorization.**
