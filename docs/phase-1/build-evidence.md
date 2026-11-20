# Phase 1 — Build, Test, and Installation Evidence

**Date:** 2026-11-20
**Project:** `android/` in `RawPOS Mobile Scanner`
**Result:** Phase 1 foundation builds, unit-tests, lints, installs, launches, and passes the instrumented
smoke test on an API 36 emulator. Release build, physical-device testing, and performance measurement
were **not** part of Phase 1 and were not performed.

---

## 1. Final commands

Run from `android/` with the provisioned toolchain:

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Android\jdk17\jdk-17.0.21+9"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

.\gradlew.bat --no-daemon --rerun-tasks assembleDebug testDebugUnitTest lintDebug
.\gradlew.bat --no-daemon connectedDebugAndroidTest
```

`--rerun-tasks` was used so the reported tests were executed, not restored from the Gradle cache.

---

## 2. Build result

| Item | Result |
|---|---|
| Task | `assembleDebug` |
| Result | **BUILD SUCCESSFUL** |
| APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Size | **11,794,030 bytes** (~11.8 MB) |
| SHA-256 | `19DBFA66089BB3E4ECD5E2C9560D755203128D64FB50FB66FDEEB9B961D45BA3` |
| Package | `com.rawpos.scanner` |
| Version | `1.0.0-phase1-debug` (versionCode 1) |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |

No release APK was produced. `app-release.apk` does not exist.

---

## 3. JVM unit tests

Command: `testDebugUnitTest` (forced re-execution).

| Test class | Tests | Failures | Errors |
|---|---:|---:|---:|
| `SettingsViewModelTest` | 3 | 0 | 0 |
| `LocalizationParityTest` | 2 | 0 | 0 |
| `ContrastTest` | 5 | 0 | 0 |
| **Total** | **10** | **0** | **0** |

What they prove:

- `SettingsViewModel` exposes the repository's initial language/theme, and writes through on change.
- Arabic and English string key sets are identical; no string value is blank.
- Light/dark body text meets 4.5:1; outlines meet 3:1; primary button labels meet 4.5:1.

JUnit XML is under `android/app/build/test-results/testDebugUnitTest/`.

---

## 4. Instrumented test

Command: `connectedDebugAndroidTest` on AVD `rawpos-phase1`, Android 16 / API 36.

| Test class | Tests | Failures | Errors |
|---|---:|---:|---:|
| `MainActivitySmokeTest` | 1 | 0 | 0 |

The test launches `MainActivity` and asserts the `connection_screen` test tag exists. The runner installs
both APKs, runs the test, and uninstalls them afterwards.

Result XML: `android/app/build/outputs/androidTest-results/connected/`.

---

## 5. Lint

Command: `lintDebug`.

| Result | Count |
|---|---:|
| Errors | **0** |
| Warnings | **14** |

All 14 warnings are "a newer version is available" notices (Gradle 8.14.8, AGP 9.5.1, Kotlin 2.4.21,
newer AndroidX/test artifacts). They are deliberate: the project is pinned to the newest versions that
support **compileSdk 36** and AGP 8.13.2, as proven by the AAR metadata in
`dependency-inventory.md`. No functional, accessibility, permission, or localization warnings remain.

The previously reported `AppBundleLocaleChanges`, `UseKtx`, `MonochromeLauncherIcon`,
`DataExtractionRules`, and `UnusedResources` warnings were fixed.

HTML report: `android/app/build/reports/lint-results-debug.html`.

---

## 6. Installation and launch evidence

Device: AVD `rawpos-phase1`, Android 16 (API 36), x86_64, WHPX acceleration, headless.

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
Performing Streamed Install
Success

adb shell am start -W -n com.rawpos.scanner/.MainActivity
Status: ok
LaunchState: COLD
Activity: com.rawpos.scanner/.MainActivity
TotalTime: 3989 ms
WaitTime: 3991 ms
```

Package facts read back from the device:

```
versionCode=1 minSdk=26 targetSdk=36
versionName=1.0.0-phase1-debug
```

**Cold-launch timing is emulator evidence, not a phone performance claim.** On this software-rendered
headless emulator, observed cold launches ranged from **1,881 ms** (warm emulator) to **3,989 ms**
(freshly booted). A physical-device startup benchmark is deferred to Phase 3/6.

### Merged permissions

`aapt2 dump permissions` on the final APK:

```
package: com.rawpos.scanner
permission: com.rawpos.scanner.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
uses-permission: name='com.rawpos.scanner.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

There is **no** `CAMERA`, `INTERNET`, location, storage, notification, or `ACCESS_LOCAL_NETWORK`
permission. The single permission is a signature-level AndroidX runtime permission for non-exported
dynamic receivers, added by the Activity/Compose dependency; it grants no user-facing capability.

---

## 7. Screenshots and UI evidence

The **six final screenshots** are retained in the Git baseline under `docs/phase-1/evidence/`. The
obsolete before-fix image and the three UIAutomator XML dumps are preserved locally but excluded from
Git by `.gitignore` (they are generated/local-only evidence).

| File | What it shows |
|---|---|
| `connection-en-final.png` | Final APK, English, light theme, Connection screen |
| `settings-en.png` | English settings: language, theme, version, diagnostics |
| `settings-dark-en.png` | Dark theme in English |
| `connection-ar.png` | Arabic RTL Connection screen |
| `settings-ar-fixed.png` | Arabic RTL settings after the LTR version fix |
| `diagnostics-ar.png` | Arabic RTL diagnostics with package/version/SDK rows |

Local-only, not in the Git baseline:

- `settings-ar-before-bidi-fix.png` — preserved as historical evidence of the version-string defect.
- `ui-connection-ar.xml`, `ui-diagnostics-ar.xml`, `ui-settings-ar-fixed.xml` — generated UIAutomator
  dumps used during verification.

The Arabic screenshots prove:

- runtime language switching works without AppCompat;
- the layout mirrors (top-bar actions swap sides, text aligns right);
- Latin package/version/SDK values are isolated LTR and no longer reorder;
- light/dark themes render through the same token set.

The Arabic copy has **not** been reviewed by a native speaker in this phase; that remains a release gate.

---

## 8. Failures found and fixed during Phase 1

| # | Failure | Root cause | Fix |
|---|---|---|---|
| 1 | `checkDebugAarMetadata` failed: Navigation 2.10.2 and Lifecycle 2.11.0 require compileSdk 37 / AGP 9.1 | Newest artifacts crossed the SDK 36 boundary | Pinned Navigation **2.9.8** and Lifecycle **2.9.4**; recorded AAR metadata evidence |
| 2 | `Unresolved reference: BuildConfig` | AGP 8 does not generate BuildConfig by default | `buildFeatures { buildConfig = true }` |
| 3 | `Unresolved reference: dp` in diagnostics | Missing import after a screen split | Added `androidx.compose.ui.unit.dp` |
| 4 | `Operator '==' cannot be applied to Int and LayoutDirection` | `Configuration.layoutDirection` is a platform int, not a Compose enum | Compared with `View.LAYOUT_DIRECTION_RTL` |
| 5 | `Unresolved reference: assertExists` | The function is a member of `SemanticsNodeInteraction`, not a top-level import | Removed the incorrect import |
| 6 | Version string rendered as `phase1-debug-1.0.0` in Arabic | Bidi reordering of a Latin technical value inside RTL | Wrapped technical values in LRI/PDI (`\u2066…\u2069`) |
| 7 | Lint warnings for KTX, monochrome icon, backup rules, unused strings | Foundation cleanup | Fixed each; lint now 0 errors / 14 version notices |

---

## 9. Not verified in Phase 1

- **No physical Android device.** Only the API 36 emulator was used.
- **No Android 8–15 device.** minSdk 26 compatibility is declared, not device-tested.
- **No release build, R8, signing, or store artifact** (explicitly excluded).
- **No performance, battery, camera, thermal, or network measurement** (no such code in Phase 1).
- **No native Arabic review.**
- **No CI.** All commands were run locally; the output is the evidence.
- **No dependency locking/verification metadata.** Recommended before Phase 2 adds networking/crypto.

---

## 10. Evidence locations

| Evidence | Path |
|---|---|
| Unit test XML | `android/app/build/test-results/testDebugUnitTest/` |
| Instrumented XML | `android/app/build/outputs/androidTest-results/connected/` |
| Lint report | `android/app/build/reports/lint-results-debug.html` / `.txt` |
| APK | `android/app/build/outputs/apk/debug/app-debug.apk` |
| Build logs | `%TEMP%\rawpos-phase1-*.log` (machine-local, not committed) |
| Screenshots | `docs/phase-1/evidence/` |
