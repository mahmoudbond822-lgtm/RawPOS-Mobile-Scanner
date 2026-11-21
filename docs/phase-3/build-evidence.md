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

### Device: authorized, install blocked by MIUI

`adb devices` now shows the phone as `device` (M2007J20CG, Android 12, `surya_global`). The instrumented
run failed before any test executed:

```
INSTALL_FAILED_USER_RESTRICTED: Install canceled by user
Failed to commit install session … package install-commit …
```

That is MIUI's per-app install restriction, not a test failure and not a code defect. Enabling
**Developer options → Install via USB** (and, if it persists, **USB debugging (Security settings)**)
is required before any instrumented test can run.

### Not proven yet

- Real barcode decoding on the device (needs the install to succeed and a physical label).
- Camera preview rendering (same).
- The instrumented camera smoke test — not written yet; it belongs with the first successful install.

---

## Open items

1. **MIUI install restriction** — enable *Install via USB* so `connectedDebugAndroidTest` and manual
   camera testing can run.
2. **Real-label decoding** — unproven until the APK installs; needs physical labels, and the 20 sample
   labels are synthetic, so the pre-pilot capture still stands as a blocking gate (R13).
3. **Instrumented camera smoke test** — to be written with the first successful install.
4. **Arabic review** — 20 new scanner strings are written but not reviewed by a native speaker (Q22).
5. **APK size** — 36.8 MB debug with the bundled model; measure release after R8 in Phase 8.
6. **720p / ~10 fps tuning** — the analyzer currently uses CameraX defaults; measurements belong to
   Phase 6.
