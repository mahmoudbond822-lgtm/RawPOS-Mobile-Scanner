# Phase 1 — Scope Compliance Report

**Authorization:** “Android Foundation Only.”
**Result:** compliant. No scope expansion. No Camera, ML Kit, networking, barcode, QR, desktop receiver,
RawPOS production change, database change, USB/keyboard change, release APK, or store publication.

---

## 1. Approved scope vs delivered

| Approved item | Delivered | Evidence |
|---|---|---|
| Create the independent `RawPOS-Mobile-Scanner` repository | Git repository initialized on branch `main` in the project directory; Android module under `android/` | `.git/`, `git status`; no commits created at the time of this report (see §8) |
| Set up the Kotlin Android project | Kotlin 2.2.21, single `:app` module, Gradle 8.13 wrapper | `android/settings.gradle.kts`, `android/app/build.gradle.kts`, wrapper |
| Configure/verify JDK, Gradle, AGP, SDK platform, build tools | JDK 17.0.21, Gradle 8.13, AGP 8.13.2, platform 36, build-tools 36.0.0 | `environment-report.md`; build output |
| Compose application structure | Single Activity, Compose, Material 3, edge-to-edge | `MainActivity.kt`, `ui/RawPosApp.kt` |
| Single Activity | `MainActivity` only; no other Activity | `AndroidManifest.xml`, source tree |
| Navigation | Compose Navigation with Connection, Scanner, Settings, Diagnostics | `navigation/RawPosNavHost.kt` |
| MVVM + StateFlow | Screen-level ViewModels with immutable UI state and `StateFlow`, collected with `collectAsStateWithLifecycle` | `feature/*/*ViewModel.kt` |
| Manual AppContainer | Constructor-injected graph + `ViewModelProvider.Factory`; no Hilt/Koin | `core/AppContainer.kt`, `core/viewmodel/AppViewModelFactory.kt` |
| Initial theme | Light/dark/system, RawPOS-derived semantic tokens, Material 3 schemes, typography, shapes | `ui/theme/MobileTokens.kt`, `Theme.kt` |
| Arabic/English localization foundations | `values/` + `values-ar/`, runtime language switch, RTL, LTR technical values | resources; screenshots `connection-ar.png`, `settings-ar-fixed.png` |
| Only strictly required dependencies | 10 direct runtime artifacts incl. BOM; test artifacts; no unused tooling | `dependency-inventory.md`; `libs.versions.toml` |
| Foundation tests | 10 JVM tests + 1 instrumented smoke test | `build-evidence.md` |
| Build verification | `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `connectedDebugAndroidTest` all pass | `build-evidence.md` |
| Environment/build/installation documentation | `android/README.md`, `docs/phase-1/*` | files |

---

## 2. Platform baseline

| Requirement | Actual | Status |
|---|---|---|
| minSdk 26 | `minSdk = 26` | Met |
| targetSdk 36, subject to verified compatibility | `targetSdk = 36`, `compileSdk = 36` | Met; verified by build |
| Verify exact SDK/build-tool versions before locking | Platform 36 rev 2, build-tools 36.0.0, AGP 8.13.2, Gradle 8.13 | Met |
| Do not add Camera or Network permissions | Manifest declares none; merged APK has no CAMERA/INTERNET | Met |

The merged manifest contains one AndroidX-generated signature permission,
`com.rawpos.scanner.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, required by the Activity/Compose runtime
for non-exported dynamic receivers. It is not a camera, network, location, storage, or runtime
permission. This is recorded rather than hidden.

`android:usesCleartextTraffic="false"` is a safety flag, not a network capability. There is no
`INTERNET` permission and no network code.

---

## 3. Exclusions — verified

| Exclusion | Verification | Result |
|---|---|---|
| No CameraX | No dependency; no import; no camera code | Pass |
| No ML Kit | No dependency; no import | Pass |
| No barcode scanning | No detector, validator, or scan-event implementation | Pass |
| No HTTP/TLS/networking | No OkHttp/Retrofit/Ktor; no `java.net`/`android.net` imports; no INTERNET permission | Pass |
| No QR pairing | Only Phase 1 placeholder copy in strings | Pass |
| No desktop receiver | No files outside this repository | Pass |
| No RawPOS production changes | RawPOS `git status` clean; HEAD `797167ac…` unchanged | Pass |
| No CheckoutService/database changes | No such files or references | Pass |
| No USB/keyboard scanner changes | No RawPOS files touched | Pass |
| No release APK/store publication | `app-release.apk` does not exist; only `app-debug.apk` and the test APK | Pass |
| No commits/push/fetch/branch deletion | `git rev-list HEAD` fails: zero commits; no remote; no push/fetch | Pass at time of report; one commit exists now — see §8 |

The only source-level occurrences of “camera”, “barcode”, or “QR” are Phase 1 **placeholder strings** and
a `cameraEnabled = false` UI flag. They describe future phases and implement nothing.

---

## 4. Safeguards

| Safeguard | Action |
|---|---|
| Inspect environment before implementation | Verified no JDK/Gradle/SDK; recorded before provisioning |
| Preserve existing user files | Phase 0 docs retained; no file deleted or rewritten outside documentation updates |
| Do not modify original RawPOS | Read-only audit and tests only; `git status` clean before/after |
| No push/fetch/delete branches | No remote configured; no such command run |
| Report unavailable tools/tests honestly | Environment, emulator, unverified physical device, Arabic review, and release exclusions are recorded |
| Keep implementation limited to Phase 1 | No camera/network/barcode/receiver code |

---

## 5. Deviations and judgment calls

1. **Toolchain provisioning.** Phase 1 explicitly authorized configuring JDK/Gradle/SDK. The machine had
   none, so JDK 17, Gradle 8.13, platform 36, build-tools 36.0.0, platform-tools, emulator, and one
   system image were installed per-user under `%LOCALAPPDATA%\Android`. No system PATH was changed.
2. **Repository location/name.** The existing project directory is the independent repository; the
   Android module is under `android/`, matching the Phase 0 architecture. The directory name retains its
   existing spelling rather than being renamed mid-session.
3. **No initial commit.** Repository creation was authorized; committing was not explicitly authorized,
   so at the time of this report the repository had zero commits and all files were untracked. The “diff”
   is therefore an all-new file set, not a patch against a baseline. **Superseded — see §8.**
4. **SharedPreferences instead of DataStore.** Phase 1 needs only language/theme persistence. Using the
   platform store avoids a dependency; the `SettingsRepository` interface allows DataStore later.
5. **No dependency locking.** Recommended before Phase 2; not required for the Phase 1 build.
6. **Emulator instead of a physical phone.** No phone was attached. A headless API 36 emulator provided
   install/launch evidence. Device coverage remains a Phase 3/6 gap.
7. **Version downgrades.** Navigation 2.10.2 and Lifecycle 2.11.0 were rejected because they require
   compileSdk 37/AGP 9.1. Navigation 2.9.8 and Lifecycle 2.9.4 were pinned instead. This is a
   compatibility decision, not a scope change.

---

## 6. Final change inventory

- `git status --porcelain -uall`: **87 candidate files** (90 Phase 1 files + `.gitattributes`,
  minus the 4 local-only evidence artifacts now ignored by `.gitignore`).
- `android/app/src`: **39 source/resource/test files**.
- No commit exists (`HEAD` has no revision) **at the time of this report — see §8**.
- No release artifact.
- RawPOS working tree: clean; HEAD unchanged.

Key additions:

```
.gitattributes
.gitignore
android/README.md
android/settings.gradle.kts
android/build.gradle.kts
android/gradle.properties
android/gradle/libs.versions.toml
android/gradlew / gradlew.bat / wrapper
android/app/build.gradle.kts
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/com/rawpos/scanner/**  (24 Kotlin files)
android/app/src/main/res/values/strings.xml
android/app/src/main/res/values-ar/strings.xml
android/app/src/main/res/**                      (icons, theme, backup rules, locales)
android/app/src/test/**                          (3 JVM test classes)
android/app/src/androidTest/**                   (1 instrumented smoke test)
docs/phase-1/environment-report.md
docs/phase-1/dependency-inventory.md
docs/phase-1/build-evidence.md
docs/phase-1/scope-compliance.md
docs/phase-1/evidence/*.png                  (six final screenshots; local-only before-fix image and UI XML are ignored)
```

Documentation updates: root `README.md`, `AGENTS.md`, `docs/planning/implementation-roadmap.md`, and the
Phase 1 rows of `docs/quality/test-matrix.md` now reflect the implemented foundation. The architecture
and constitution are unchanged in substance.

---

## 7. Completion gate

| Gate item | Status |
|---|---|
| Final diff / change set | All-new files; 87 candidates (4 local-only evidence artifacts ignored); no baseline commit at the time of this report (reported honestly; superseded by §8) |
| Project structure | `android/README.md` §6 and this report §6 |
| Dependency inventory | `docs/phase-1/dependency-inventory.md` |
| Build/test evidence | `docs/phase-1/build-evidence.md` |
| Installation evidence | API 36 emulator: install Success, launch Success, screenshots; physical device not available |
| Scope-compliance report | This document |

**Phase 1 is complete. Phase 2 has not started.**

---

## 8. Post-report correction — repository now has one commit

**Recorded:** 2026-11-20, after this report was written. This section corrects §3, §5.3, §6, and §7; the
sections above are kept as the point-in-time record and are not rewritten.

| Claim in this report | Current state |
|---|---|
| "zero commits", `HEAD` has no revision (§3, §5.3, §6) | One commit exists: `b1e332f` — `feat: initial baseline for RawPOS Mobile Scanner (Phase 1)`, authored 2026-11-20 17:41:21 +0200 |
| "no baseline commit" (§7) | `git rev-list --count HEAD` → `1`; `b1e332f` is the root commit |
| "no remote" | Still true: no remote is configured; nothing was pushed, fetched, or merged |

**Effect on the report's conclusions:** none of the scope findings change. The commit contains the same
Phase 1 file set the report describes, it was created after the report rather than during it, and it does
not add Camera, ML Kit, networking, barcode, QR, or desktop code. The change set is now reviewable as a
diff against an empty tree instead of as a list of untracked files.

**What this correction does not establish:** who authorized the commit, or when. The Phase 1
authorization ("Android Foundation Only", §Authorization) did not cover committing, so the authorization
for `b1e332f` should be recorded by the owner if it is to be treated as a controlled baseline.
