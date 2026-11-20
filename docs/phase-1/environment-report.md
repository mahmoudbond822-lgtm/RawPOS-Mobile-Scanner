# Phase 1 — Environment Report

**Date:** 2026-11-20
**Host:** Dell Precision 7750, Intel Core i7-10850H (6C/12T), 16 GB RAM
**OS:** Windows 11 Pro 10.0.26300 (64-bit)
**Tooling style:** command line only; Android Studio was not installed or used.

---

## 1. State before provisioning

Verified before any project work:

| Tool | State |
|---|---|
| `java` / `javac` | not on PATH |
| `gradle` | not on PATH |
| `JAVA_HOME` | unset |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | unset |
| `%LOCALAPPDATA%\Android\Sdk` | did not exist |
| `adb` | not available |
| Android Emulator | not installed |
| Git | 2.53.0.windows.2 |
| curl | available (`C:\Windows\System32\curl.exe`) |
| Disk free | ~187 GB |

No dependency was assumed to exist. The first Phase 1 action was an environment inspection, as required.

---

## 2. Provisioned toolchain

Everything was installed **per-user**, outside the repository, under
`%LOCALAPPDATA%\Android`. No system PATH or machine-wide environment variable was changed.

| Component | Version | Path |
|---|---|---|
| Eclipse Temurin JDK | 17.0.21+9 | `%LOCALAPPDATA%\Android\jdk17\jdk-17.0.21+9` |
| Gradle (distribution) | 8.13 | `%LOCALAPPDATA%\Android\gradle-8.13` |
| Android command-line tools | 19.0 | `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest` |
| Android SDK Platform | 36 (Android 16) | `%LOCALAPPDATA%\Android\Sdk\platforms\android-36` |
| Android Build Tools | 36.0.0 | `%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0` |
| Android Platform Tools (adb) | 37.0.3 | `%LOCALAPPDATA%\Android\Sdk\platform-tools` |
| Android Emulator | 37.3.10 | `%LOCALAPPDATA%\Android\Sdk\emulator` |
| System image | `system-images;android-36;default;x86_64` | `%LOCALAPPDATA%\Android\Sdk\system-images\android-36\default\x86_64` |
| AVD | `rawpos-phase1` (Pixel 6 profile) | `%USERPROFILE%\.android\avd\rawpos-phase1.avd` |

The SDK also contains `build-tools;35.0.0`; it was already present in the SDK package index and is not
used by this project.

### Verification commands and results

```
java -version        → openjdk version "17.0.21" 2026-10-20 (Temurin-17.0.21+9)
gradle --version     → Gradle 8.13
sdkmanager --version → 19.0
adb version          → Android Debug Bridge 1.0.41 / 37.0.3
emulator-check accel → WHPX(10.0.26300) is installed and usable
```

The Gradle wrapper pins the same distribution (`gradle-8.13-bin.zip`) for reproducibility.

---

## 3. SDK packages installed

```powershell
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
sdkmanager "emulator" "system-images;android-36;default;x86_64"
```

`--list_installed` confirmed:

```
build-tools;36.0.0
emulator 37.3.10
platform-tools 37.0.3
platforms;android-36 (revision 2)
system-images;android-36;default;x86_64 (revision 2)
```

All SDK licences were accepted during provisioning.

---

## 4. Project environment wiring

- `android/local.properties` contains a `sdk.dir` entry pointing at the local Android SDK. The file is
  machine-specific and is git-ignored; it must never be committed.
- The file is **git-ignored** because it is machine-specific.
- Builds were run with per-command environment variables:

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Android\jdk17\jdk-17.0.21+9"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
```

This keeps the machine clean and makes the commands reproducible. Another machine only needs JDK 17,
the SDK packages above, and its own `local.properties`.

---

## 5. Network endpoints used during provisioning

| Purpose | Endpoint |
|---|---|
| JDK | `api.adoptium.net` |
| Android command-line tools | `dl.google.com` |
| SDK packages | `dl.google.com` / Google Maven |
| Gradle distribution | `services.gradle.org` |
| Maven dependencies | `dl.google.com/dl/android/maven2`, `repo.maven.apache.org` |

No cloud service, analytics, telemetry, or runtime network endpoint is used by the app.

---

## 6. Emulator used for installation evidence

| Property | Value |
|---|---|
| AVD | `rawpos-phase1` |
| Device profile | Pixel 6 |
| Android release | 16 |
| API level | 36 |
| ABI | x86_64 |
| Model reported | `Android SDK built for x86_64` |
| Acceleration | WHPX |
| Renderer | `swiftshader_indirect` (headless) |
| Window | `-no-window` |

The emulator booted in ~21 seconds and was used for install, launch, screenshots, and the instrumented
smoke test. It is a test device, not a performance reference.

---

## 7. What remains unavailable

- No physical Android phone was connected. `adb devices` was empty before the emulator was created.
- No Android Studio or IDE debugger was used.
- No CI runner is configured.
- No Android 8/9/10 device was available; only API 36 was exercised.
- No native Arabic speaker reviewed the copy in this phase.
- Emulator timing is software-rendered; it must not be used as p95 latency evidence for Phase 3/4.

These are reported as limitations, not as passed checks.
