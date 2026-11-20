# RawPOS Mobile Scanner — Android

**Phase 1: Foundation only.** This module contains no camera, barcode, network, TLS, or pairing code.
It exists to prove the toolchain, navigation, MVVM/StateFlow, manual dependency injection, theme, and
Arabic/English localization before any scanning work begins.

---

## 1. Verified toolchain

| Tool | Version | Location |
|---|---|---|
| JDK | Temurin 17.0.21+9 | `%LOCALAPPDATA%\Android\jdk17\jdk-17.0.21+9` |
| Gradle | 8.13 (wrapper) | `android/gradle/wrapper` |
| Android Gradle Plugin | 8.13.2 | version catalog |
| Kotlin | 2.2.21 | version catalog |
| Android SDK Platform | 36 (Android 16) | `%LOCALAPPDATA%\Android\Sdk\platforms\android-36` |
| Build Tools | 36.0.0 | `%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0` |
| Platform Tools (adb) | 37.0.3 | `%LOCALAPPDATA%\Android\Sdk\platform-tools` |
| Emulator | 37.3.10 | `%LOCALAPPDATA%\Android\Sdk\emulator` |
| System image | android-36;default;x86_64 | AVD `rawpos-phase1` |

The machine had **no JDK, Gradle, or Android SDK** before this phase. They were provisioned per-user
under `%LOCALAPPDATA%\Android`; no system-wide PATH or environment variable was changed. Gradle finds
the SDK through the git-ignored `android/local.properties`.

### Version compatibility note

The first build failed because the newest Navigation (`2.10.2`) and Lifecycle (`2.11.0`) artifacts
require `compileSdk 37` / AGP 9.1. The project pins the newest **SDK-36-compatible** versions instead:

- Navigation **2.9.8** (`minCompileSdk 35`, min AGP 8.6.0)
- Lifecycle **2.9.4** (`minCompileSdk 35`, min AGP 8.6.0)
- Activity **1.12.4** (`minCompileSdk 36`, min AGP 8.9.1)
- Compose BOM **2026.04.01** → Compose UI 1.11.0 / Material3 1.4.0 (`minCompileSdk 35`)

Do not raise these without re-checking AAR metadata and the targetSdk 36 permission boundary.

---

## 2. Prerequisites

- Windows with PowerShell.
- JDK 17 (the provisioned Temurin path above, or another 17 JDK).
- Android SDK platform 36 and build-tools 36.0.0.
- `android/local.properties` pointing at the SDK, or `ANDROID_HOME` set.
- An emulator or a USB-debugging device for installation evidence.

`local.properties` is intentionally git-ignored because it is machine-specific.

---

## 3. Build and test

From `android/`:

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Android\jdk17\jdk-17.0.21+9"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

.\gradlew.bat --no-daemon assembleDebug testDebugUnitTest lintDebug
```

Expected (Phase 1 evidence):

- `assembleDebug`: BUILD SUCCESSFUL, `app-debug.apk` ~11.9 MB.
- `testDebugUnitTest`: 10 tests, 0 failures.
- `lintDebug`: 0 errors, 26 warnings (mostly deliberate version pins and API-33 attributes).

Instrumented smoke test (requires a device/emulator):

```powershell
.\gradlew.bat --no-daemon connectedDebugAndroidTest
```

The connected test **uninstalls** the app and test APK when it finishes. Reinstall before manual use.

---

## 4. Install and launch

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.rawpos.scanner/.MainActivity
```

Package: `com.rawpos.scanner`
Version: `1.0.0-phase1` (debug suffix `-debug`)
Min SDK: 26
Target/compile SDK: 36

---

## 5. Emulator setup (used for Phase 1 evidence)

```powershell
sdkmanager "emulator" "system-images;android-36;default;x86_64"
"no" | avdmanager create avd -n rawpos-phase1 `
  -k "system-images;android-36;default;x86_64" -d pixel_6 --force
emulator -avd rawpos-phase1 -no-window -no-audio -no-boot-anim `
  -gpu swiftshader_indirect -no-snapshot -no-snapshot-save
```

WHPX acceleration was available on the build machine. A software-rendered emulator is slower than a
real phone; do not use its timings as performance evidence.

---

## 6. Project structure

```
app/src/main/java/com/rawpos/scanner/
  RawPosScannerApplication.kt      Application; builds the container
  MainActivity.kt                  single Activity; locale wrap; Compose host
  core/
    AppContainer.kt                manual composition root
    AppInfo.kt                     package/version/SDK facts
    settings/                      LanguageMode, ThemeMode, repository
    viewmodel/AppViewModelFactory.kt
  navigation/
    RawPosDestination.kt
    RawPosNavHost.kt               top-bar navigation; Connection → Scanner/Settings → Diagnostics
  feature/
    connection/                    screen + ViewModel + state
    scanner/                       placeholder screen + ViewModel + state
    settings/                      language/theme/about + ViewModel + state
    diagnostics/                   build facts screen + ViewModel
  ui/
    RawPosApp.kt
    components/Common.kt           status pill, info row, phase notice
    locale/LocaleSupport.kt        app-language wrapping + RTL decision
    theme/                         tokens, Material3 schemes, type, shapes
app/src/main/res/values/           English strings
app/src/main/res/values-ar/        Arabic strings
app/src/test/                      JVM tests
app/src/androidTest/               Compose smoke test
```

Architecture rules enforced in Phase 1:

- One Activity, Compose Navigation.
- Screen-level ViewModels, immutable UI state, `StateFlow`/`collectAsStateWithLifecycle`.
- Constructor injection through `AppContainer`; no service locator, no Hilt.
- Semantic tokens only in `ui/theme`; screens do not hard-code colors.
- No camera/network classes or permissions.

---

## 7. Phase 1 scope boundaries

**Included:** project setup, toolchain, Compose shell, navigation, MVVM/StateFlow, manual DI, theme
tokens (light/dark/system), Arabic/English resources and runtime switch, RTL, basic tests, build,
install, documentation.

**Excluded:** CameraX, ML Kit, barcode scanning, HTTP/TLS/networking, QR pairing, desktop receiver,
RawPOS changes, database changes, USB/keyboard changes, release APK, store publication.

The launcher declares **no CAMERA or INTERNET permission**. The only merged permission is the
AndroidX-generated `com.rawpos.scanner.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, a signature-level
internal permission required by the Activity/Compose runtime; it is not a user-facing capability.

---

## 8. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `JAVA_HOME is not set` | Export the JDK path above or set `org.gradle.java.home` in `gradle.properties` (do not commit a machine path). |
| `SDK location not found` | Set `ANDROID_HOME` or recreate `local.properties` with `sdk.dir=...`. |
| AAR metadata requires SDK 37 | You upgraded Navigation/Lifecycle too far; restore 2.9.8 / 2.9.4. |
| `adb: no devices` | Start the emulator or connect a USB-debugging phone. |
| App is missing after `connectedDebugAndroidTest` | The test runner uninstalls it; reinstall the debug APK. |
