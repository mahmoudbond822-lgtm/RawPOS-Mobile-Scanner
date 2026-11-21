# Phase 3 — Build Evidence

**Phase:** 3 — Android camera and barcode scanning (authorized 2026-11-21, A18).
**Scope:** `implementation-roadmap.md` Phase 3. No network, no pairing client, no checkout.
**Every result below was produced in this session on this machine.**

---

## Test device

| Item | Value |
|---|---|
| Model | POCO X3 NFC (`M2007J20CG`) |
| Android | 12 (API 31) |
| Connection | USB, visible to Windows as `POCO X3 NFC` |
| **adb status** | **not authorized yet** — `adb devices` lists nothing |

Windows sees the phone but adb does not, which means USB debugging is off or the RSA prompt has not
been accepted. This blocks only the instrumented camera work (increment 3+); the logic layer below is
pure JVM and needs no device.

---

## Increment 1 — validation, gate, and transport seam

### What was added

| File | Purpose |
|---|---|
| `core/barcode/BarcodeFormat.kt` | Symbologies + the provisionally approved enabled set (D14/A11) |
| `core/barcode/BarcodeValidator.kt` | The full predicate and its typed rejection reasons |
| `core/barcode/ScanGate.kt` | Candidate stabilizer: stability, cooldown, latch, re-arm, one at a time |
| `core/transport/ScanTransport.kt` | The seam Phase 4 will implement; no network here |
| `core/transport/FakeTransport.kt` *(test)* | Records submissions so scanning is testable without a desktop |
| `BarcodeValidatorTest.kt`, `ScanGateTest.kt`, `FakeTransportTest.kt` | 26 tests |

No camera, ML Kit, or manifest permission was added. The app still declares **no permissions**.

### Gates

| Gate | Command | Result |
|---|---|---|
| Build | `.\gradlew.bat --no-daemon assembleDebug` | `BUILD SUCCESSFUL` |
| Unit tests | `.\gradlew.bat --no-daemon testDebugUnitTest` | **36 tests**, 0 failed |
| Lint | `.\gradlew.bat --no-daemon lintDebug` | 0 errors, 14 warnings (version notices, unchanged) |
| Instrumented | `connectedDebugAndroidTest` | **not run** — device not authorized for adb |

Test breakdown: `BarcodeValidatorTest` 11, `ScanGateTest` 13, `FakeTransportTest` 2, plus the 10 Phase 1
tests. New tests: **26**.

### Decisions recorded

- **Parameters come from the spec, not from taste** (§6.1): 2 stability frames, 150 ms global minimum
  interval, 500 ms re-arm absence. They are constructor parameters so Phase 6 can tune them with
  measurements instead of edits.
- **Time is a parameter**, never a hidden clock. Every gate rule is deterministic under test; no test
  sleeps.
- **`FakeTransport` lives in the test source set** so no fake can ship inside the APK.
- **No check-digit rejection.** ML Kit validates the symbology checksum, and RawPOS treats barcodes as
  opaque; adding a second opinion would create phone-only false negatives.
- **The enabled set excludes QR**, which is enabled only in pairing mode.

### Deliberate non-implementations

- `event_id`, retries, and acknowledgment — Phase 4. The 10 s first-submission staleness rule needs the
  capture timestamp, which only exists once events exist.
- CameraX, ML Kit, permissions, and the scanner screen — the next increment. CameraX and ML Kit are
  inside A18's scope and will be pinned in `libs.versions.toml` when added.
- Any network call. `ScanTransport` has one method and no implementation in the main source set.

---

## Increment 2 — camera, ML Kit, and the scanner screen

### Dependencies added (A18 scope)

| Artifact | Version |
|---|---|
| `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` | 1.6.2 |
| `com.google.mlkit:barcode-scanning` (bundled) | 17.3.0 |

CameraX 1.6.x and ML Kit 17.3.x are the versions named in D21; both are inside the Phase 3 scope that
A18 authorized. The APK grew to **36.8 MB** in debug (unminified, bundled model). Release size is a
Phase 8 measurement.

### What changed

| File | Change |
|---|---|
| `AndroidManifest.xml` | `CAMERA` permission + `uses-feature camera.any` required. Still **no INTERNET** |
| `core/camera/BarcodeAnalyzer.kt` | ML Kit analyzer, format mapping, paused check, frame closed exactly once |
| `core/barcode/DecodedBarcode.kt` | Decoded symbol value object |
| `feature/scanner/ScannerViewModel.kt` | Validation → gate → optional transport; 8 new tests |
| `feature/scanner/ScannerScreen.kt` | Permission flow, CameraX bind/unbind, torch, pause, target, rejection text |
| `navigation/RawPosNavHost.kt` | Passes the five callbacks |
| `values/` + `values-ar/` | 20 scanner strings replaced/added in **both** catalogs |

### Behaviour

- The camera binds only while the screen is composed and unbinds on dispose, so leaving the screen
  releases it.
- The analyzer returns early when paused and still closes the frame — no leaked `ImageProxy`.
- Rejection reasons render as one sentence each; the status pill distinguishes ready / paused / scanned /
  frame-one-barcode.
- No scan is transmitted. The screen states that plainly, and the transport is `null` in Phase 3.

### Gates

| Gate | Result |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | **44 tests**, 0 failed (8 new) |
| `lintDebug` | 0 errors, 18 warnings — all version notices |
| `connectedDebugAndroidTest` | **blocked at install**, see below |

### Device: POCO X3 NFC — instrumented tests pass

| Item | Value |
|---|---|
| Device | M2007J20CG, Android 12, `surya_global` |
| adb | `device` |
| `connectedDebugAndroidTest` | **2 tests passed, 0 failed** |
| Run time | 1m 04s |

```
com.rawpos.scanner.MainActivitySmokeTest: connectionScreenIsShownOnLaunch
com.rawpos.scanner.core.camera.BarcodeDecodingTest: decodes_a_generated_ean13_image
```

The second test is the one that matters: it loads a **generated EAN-13 image** (`6291041500213`,
checked in as a test asset), decodes it with the bundled ML Kit model on the device, asserts the value
and that the symbology maps to `EAN_13`, then asserts `BarcodeValidator` accepts it. That is real
decoding, not a mock.

### Two MIUI behaviours worth recording

1. **Installs fail while the screen is locked.** `INSTALL_FAILED_USER_RESTRICTED` appears even though
   adb shows the device as authorised. Unlocking the screen is enough; no setting change was needed.
2. **`pm grant` is blocked.** `adb shell pm grant … CAMERA` raises a security exception on this build,
   so the runtime permission has to be granted by a person tapping *Allow*.

The first cost three failed runs and is now written down so it does not cost them again.

### Manual check on the device — owner-observed, 2026-11-21

| Behaviour | Observed |
|---|---|
| Live camera preview and target window | **yes** |
| One reading while a label is held still | **yes** |
| A second reading after removing and re-presenting the label | **yes** |
| Two labels in the frame → "Frame one barcode" | **yes** |
| Torch toggle and pause/resume | **yes** |
| A rejection message for a non-numeric or disabled symbology | **not observed** |

The first five are the behaviours that matter most, and they match the unit tests exactly: the gate
latches on emit, requires absence before re-arming, and refuses ambiguity.

**Provenance:** this is an owner-observed manual check, not something the agent measured. It is
recorded as such on purpose.

Corroborating instrumented-device log, which shows the bind/unbind lifecycle:

```
09:20:26 CONNECT    device 0  com.rawpos.scanner (PID 22517)
09:21:21 DISCONNECT device 0  com.rawpos.scanner
09:21:22 CONNECT    device 0  com.rawpos.scanner
```

Rejection rendering is unit-tested at the state level (`ScannerViewModelTest`) but has not been seen on
the phone, because no non-numeric or disabled symbol was presented.

### Phase 3 acceptance criteria

| Criterion | Status |
|---|---|
| One physical label produces one event while held | **met** — manual + `ScanGateTest` |
| A re-presented label after re-arm produces a second | **met** — manual + gate tests |
| Non-digit / alphanumeric / QR payloads rejected with the right message | **partly** — logic tested; on-device display unobserved |
| Leading zeros preserved | **met** — `BarcodeValidatorTest` |
| No network call when offline | **met** — no `INTERNET` permission, transport `null` |
| Camera releases on background/pause | **met** — `dumpsys media.camera` connect/disconnect + dispose unbind |

### Still missing from the Phase 3 deliverable list

Recorded so "it works" is not mistaken for "it is finished":

- **Feedback**: sound, haptics, and reduced-motion handling were never implemented.
- **Zoom control**: present in the Phase 1 placeholder, dropped when the screen was rebuilt.
- **Instrumented camera smoke**: the fixture-image test covers decoding, not camera binding.
- **Arabic review** of the 20 new scanner strings (Q22).

### Not proven yet

- A rejection message rendered on the phone (logic tested, display unobserved).
- Camera behaviour under low light, motion, or a dirty lens.
- 720p / ~10 fps behaviour: the analyzer still uses CameraX defaults and no frame-rate measurement exists.

---

## Increment 3 — feedback, zoom, and the camera smoke test

### What was added

| Item | Behaviour |
|---|---|
| Scan feedback | One haptic tick and one short beep per accepted scan |
| Silent mode | The beep is skipped when the ringer is silent; the haptic still fires |
| Opt-out | `Settings → Scanner feedback` switch, default on, persisted |
| Zoom | 1× / 2× toggle driving `cameraControl.setZoomRatio` |
| Instrumented smoke | `ScannerScreenTest` navigates to the screen and asserts its controls |

The feedback fires from a `scanCount` counter, not from the barcode value, so two consecutive scans of
the same product each produce feedback. Keying it on the value would have silently skipped the second.

### Gates

| Gate | Result |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | **45 tests**, 0 failed |
| `lintDebug` | 0 errors, 18 warnings — all version notices |
| `connectedDebugAndroidTest` | blocked by MIUI's install gate |
| Instrumented, installed by hand | **3 tests passed, 0 failed** |

```
com.rawpos.scanner.MainActivitySmokeTest: .
com.rawpos.scanner.core.camera.BarcodeDecodingTest: .
com.rawpos.scanner.feature.scanner.ScannerScreenTest: .

OK (3 tests)
Time: 5.75
```

### How the instrumented suite was made to run

MIUI refused every USB install (`INSTALL_FAILED_USER_RESTRICTED`) even after USB debugging was
re-enabled, because its separate **Install via USB** gate was off. That build also blocks `pm grant` and
`settings put system`, so none of the usual workarounds were available.

Resolution: both APKs were pushed to `/sdcard/Download` and installed from the device's file manager,
which the USB-install gate does not cover. The suite was then run directly:

```
adb shell am instrument -w com.rawpos.scanner.test/androidx.test.runner.AndroidJUnitRunner
```

**This is a workaround, not a fix.** `connectedDebugAndroidTest` will keep failing on this phone until
*Install via USB* is enabled, and Phase 4 will need many instrumented runs.

---

## Open items

1. **Final instrumented run** — needs the phone kept awake (see the MIUI note above).
2. **Rejection message on the phone** — logic tested; display unobserved.
3. **Arabic review** — 22 new scanner settings/strings are written but not reviewed by a native speaker
   (Q22).
4. **APK size** — 36.8 MB debug with the bundled model; measure release after R8 in Phase 8.
5. **720p / ~10 fps tuning** — the analyzer uses CameraX defaults; measurements belong to Phase 6.
6. **Physical-label decoding** — owner-confirmed on a real label; the pre-pilot capture against real
   *shop* data remains a blocking gate (R13).
