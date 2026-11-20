# Android Architecture

**Status:** proposed
**App:** RawPOS Mobile Scanner (companion, scan-only)
**Related:** `barcode-scanning.md`, `networking-protocol.md`, `security-design.md`,
`../design/mobile-ux-specification.md`, `../design/mobile-design-system.md`

---

## 1. Platform baseline

| Item | Choice | Rationale |
|---|---|---|
| Language | Kotlin 2.2.x (pin at Phase 1) | Modern, coroutines, Compose |
| UI | Jetpack Compose + Material 3 | Less boilerplate, strong state model, theming |
| Min SDK | 26 (Android 8.0) | CameraX/ML Kit support, TLS 1.2, Keystore, realistic retail fleet |
| Target/compile SDK | **36 / 36** | Android 17 (target 37+) requires the new `ACCESS_LOCAL_NETWORK` runtime permission; v1 keeps the CAMERA+INTERNET boundary. Revisit target 37 as an explicit decision |
| JDK / Gradle | 17 / 8.13 (pin) | AGP requirement; reproducible build |
| AGP | 8.13.x (pin) | Supports API 36; AGP 9 is not required |
| Build | Gradle Kotlin DSL + version catalog + dependency locking | Reproducible, no dynamic versions |
| Architecture | Single Activity, Compose Navigation, MVVM + StateFlow | One job, few screens |
| DI | Manual `AppContainer` (constructor injection) | Hilt adds build complexity for ~8 objects; manual DI is testable and transparent |
| Camera | CameraX 1.6.x (evaluate 1.7 later) | Lifecycle-aware, vendor-quirky-camera abstraction; 1.6 is established |
| Analyzer | Manual `ImageAnalysis.Analyzer` | Explicit backpressure, proxy lifetime, throttling; no `camera-mlkit-vision` dependency |
| Decoder | ML Kit **bundled** barcode scanning 17.3.x | Must work offline on first launch; no Play Services dependency; ~2.4 MB |
| Compose BOM | Newest stable compatible with SDK 36 (pin) | Current Compose may require SDK 37/AGP 9; do not chase it at the cost of the permission boundary |
| HTTP | OkHttp + pin-only trust manager | Mature timeouts, connection pool, cancellation; pinning is implemented correctly |
| Storage | Android Keystore (AES-GCM) + DataStore | Token protection + simple preferences; no Room |
| Background | None (no service, no wake lock) | Scanning only while visible; battery and privacy |

No Google Play Services requirement. No Firebase. No analytics. No cloud.

**Android 17 note:** if a later store policy forces target 37+, the app must declare and request
`ACCESS_LOCAL_NETWORK`, which changes the permission UX and the install story. That is a separate,
approved decision; v1 stays on 36. Verify the exact platform behavior at Phase 1.

---

## 2. Project structure

A single `:app` module, package-by-feature. Do not split into 6 Gradle modules for a 4-screen app.

```
android/
  settings.gradle.kts
  build.gradle.kts
  gradle/libs.versions.toml
  gradle/wrapper/…
  app/
    build.gradle.kts
    proguard-rules.pro
    src/main/
      AndroidManifest.xml
      res/
        values/strings.xml
        values-ar/strings.xml
        xml/network_security_config.xml
        mipmap-*/ic_launcher…
        font/noto_sans_arabic_*.ttf
      java/com/rawpos/scanner/
        RawPosApp.kt                 Application; builds AppContainer
        MainActivity.kt              single Activity; edge-to-edge; nav host
        AppContainer.kt              manual DI graph
        navigation/Routes.kt
        core/
          model/ScanEvent.kt
          model/ScanStatus.kt
          model/BarcodeFormat.kt
          model/ConnectionState.kt
          barcode/BarcodeValidator.kt
          barcode/ScanGate.kt
          barcode/ScanGateConfig.kt
          net/Transport.kt           interface
          net/OkHttpTransport.kt
          net/PairingClient.kt
          net/ScanClient.kt
          net/ContextClient.kt
          net/TlsPinning.kt
          net/ProtocolJson.kt
          storage/CredentialStore.kt  interface
          storage/KeystoreCredentialStore.kt
          storage/PreferencesStore.kt
          storage/PendingEventStore.kt
          clock/Clock.kt
          util/Result.kt
        feature/
          connection/ConnectionScreen.kt
          connection/ConnectionViewModel.kt
          connection/PairingQrAnalyzer.kt
          scanner/ScannerScreen.kt
          scanner/ScannerViewModel.kt
          scanner/CameraController.kt
          scanner/ScanOverlay.kt
          scanner/ResultCard.kt
          settings/SettingsScreen.kt
          settings/SettingsViewModel.kt
          settings/DiagnosticsScreen.kt
        ui/
          theme/MobileTokens.kt
          theme/Theme.kt
          theme/Type.kt
          theme/Dimens.kt
          components/…
    src/test/java/…                 JVM tests
    src/androidTest/java/…          instrumented tests
  protocol/fixtures/                shared JSON fixtures (copied/verified by hash)
```

Rules:

- `core/barcode` and `core/model` are pure Kotlin/JVM: no `android.*` imports. This is enforced by a
  test or a lint rule.
- `feature/*` may depend on `core/*`; `core/*` never depends on `feature/*`.
- `ui/theme` contains the only color literals.
- No class outside `core/net` performs HTTP.
- No class outside `core/storage` touches the Keystore.

---

## 3. Runtime architecture

```
MainActivity
  └── RawPosNavHost
        ├── ConnectionScreen ── ConnectionViewModel ── PairingClient, CredentialStore
        ├── ScannerScreen ───── ScannerViewModel ───── CameraController, ScanGate,
        │                                             BarcodeValidator, ScanClient
        ├── SettingsScreen ──── SettingsViewModel ──── PreferencesStore, CredentialStore
        └── DiagnosticsScreen ─ DiagnosticsViewModel ─ event log (redacted)
```

### 3.1 State

- Each ViewModel exposes one immutable `UiState` via `StateFlow`.
- UI events are methods; no business logic in composables.
- Camera analysis never writes UI state from the analysis thread; it emits candidates into a
  `Channel`/`StateFlow` confined to the ViewModel scope.
- One-shot effects (sound/haptic) are `SharedFlow` events, not state, so they do not replay on
  recomposition.

### 3.2 Coroutines

- `viewModelScope` for UI work.
- `Dispatchers.IO` for OkHttp/DataStore/Keystore.
- `Dispatchers.Default` for validation/stabilization if it becomes non-trivial.
- Analysis runs on the CameraX executor (single thread); it only appends candidates.
- Structured concurrency: cancel the in-flight send when the ViewModel clears; the event id remains
  persisted for a safe retry.
- Do not use `GlobalScope`.

### 3.3 Dependency injection

`AppContainer` builds:

- `Clock` (system or fake),
- `PreferencesStore`,
- `KeystoreCredentialStore`,
- `OkHttpClient` (pinning applied per desktop when paired),
- `PairingClient`, `ScanClient`, `ContextClient`,
- `BarcodeValidator`, `ScanGate`,
- `PendingEventStore`.

ViewModels receive interfaces, not singletons. Tests construct the graph with fakes. No service
locator, no reflection.

---

## 4. Camera pipeline

```
CameraX Preview ──► PreviewView (UI)
      │
      └── ImageAnalysis (1280×720, YUV_420_888, KEEP_ONLY_LATEST, ~10 fps)
              │
              ▼
        ML Kit BarcodeScanner (bundled)
              │  rawValue, format, boundingBox
              ▼
        BarcodeValidator (digits, length, enabled format)
              │
              ▼
        ScanGate (2-frame stability, cooldown, 500 ms re-arm, one-in-flight)
              │
              ▼
        ScanEvent (UUIDv4 + sequence + timestamp)
              │
              ▼
        ScanClient (TLS + pin + retry)
```

### 4.1 Binding

- `ProcessCameraProvider` is obtained once per lifecycle owner.
- `bindToLifecycle(owner, DEFAULT_BACK_CAMERA, preview, analysis)`.
- Rebind on resume; unbind on pause/stop. Release the provider on destroy.
- `PreviewView.ImplementationMode.COMPATIBLE` or `PERFORMANCE` per device testing.
- `ImageAnalysis.setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)`.
- `ImageAnalysis.setOutputImageRotationEnabled(true)` or pass `rotationDegrees` to ML Kit; do not do
  both incorrectly.
- Close every `ImageProxy` in a `finally` block.

### 4.2 Focus, torch, zoom

- Continuous autofocus where supported; `CameraControl.startFocusAndMetering` on tap.
- Torch via `CameraControl.enableTorch`; catch failures and show an error.
- Zoom via `CameraControl.setZoomRatio`; clamp to `ZoomState.maxZoomRatio`.
- No automatic zoom loop in v1; user pinch/slider only. Evaluate ML Kit zoom suggestions in Phase 6,
  off by default.

### 4.3 Analysis rate

- Throttle to ~10 fps inside the analyzer (drop frames by timestamp); CameraX may deliver 30.
- ML Kit runs on the analyzer executor; do not spawn a task per frame.
- Use a manual `ImageAnalysis.Analyzer`; do not add `camera-mlkit-vision` for v1 (no overlay needs its
  coordinate mapping).
- Close the image before returning.
- If analysis takes longer than the frame interval, `KEEP_ONLY_LATEST` drops frames; that is expected.

### 4.4 Failure handling

- Camera permission denied → rationale screen, no crash.
- Camera in use by another app → bind failure → show "camera unavailable", retry.
- Camera disconnected → unbind; rebind on resume.
- ML Kit initialization failure → show a clear error; disable scanning.
- Low light → after 10 s without a read, show a torch hint (do not auto-enable).

---

## 5. ML Kit

- Artifact: bundled `com.google.mlkit:barcode-scanning` (verify exact version at Phase 1).
- Formats: EAN-13, EAN-8, UPC-A, Code 128 by default; others per settings (UPC-E, Code 39, ITF).
- QR is enabled only in the pairing screen's scanner instance, not the product scanner.
- Use `Barcode.getRawValue()`, never `getDisplayValue()`.
- Map `Barcode.FORMAT_*` to an app enum; unknown → `unknown`.
- Multiple barcodes in a frame: if more than one eligible in the target region, emit none.
- Do not use ML Kit's "zoom suggestion" without a pinned version and measurement.
- No network, no location permission.
- Pin the model version; rerun golden-label tests on upgrade.

---

## 6. Lifecycle, permissions, and process death

### 6.1 Lifecycle

- Scanner analyzes only while the screen is `RESUMED` and the app is foregrounded.
- On `ON_PAUSE`: stop analysis, release the camera, cancel unsubmitted candidates, keep the connection.
- On `ON_RESUME`: rebind, but require a tap to resume scanning (avoid invisible capture).
- `FLAG_KEEP_SCREEN_ON` only while the scanner is visible and active.
- No foreground service, no wake lock, no background scanning.

### 6.2 Process death

- Credentials and preferences are persisted.
- `SavedStateHandle` may restore the route and harmless UI flags only. It must never restore a
  barcode or a pending payload.
- At most one pending event is persisted (event id, payload, desktop instance id, context). On restart,
  if the desktop instance id is unchanged, the user may retry the same event; otherwise mark it
  uncertain and do not auto-replay.
- Camera state is not restored; the scanner starts paused.

### 6.3 Permissions

| Permission | When | Denial |
|---|---|---|
| `CAMERA` | First time the pairing or scanner screen needs it | Rationale; "Open settings" if permanently denied; no crash |
| `INTERNET` | Install-time; required for any socket on Android | — |

No location, storage, contacts, phone, Bluetooth, Wi-Fi-state, notification, or VIBRATE permission in
v1. Haptics use platform/Compose haptic feedback, which does not require VIBRATE; add VIBRATE only if
custom multi-pulse patterns are proven necessary.

**Android 17:** do not target 37+ in v1. That would require `ACCESS_LOCAL_NETWORK` and a new runtime
permission flow. Target 36 keeps LAN access through `INTERNET`. Revisit only as an approved change.

### 6.4 Orientation

Portrait-first. Rotation is supported with a camera rebind; test for duplicate scans. Do not lock
orientation unless field testing shows a duplicate risk that cannot be fixed.

---

## 7. Storage and credentials

### 7.1 Preferences

DataStore (Preferences) for:

- language, theme, numerals;
- sound, vibration, continuous, torch-on-start;
- enabled barcode formats;
- `desktop_name`, `host`, `port`, `desktop_id`, `pin`;
- `last_sequence`;
- diagnostics enabled (default off).

No barcode history. No product data.

### 7.2 Credentials

`KeystoreCredentialStore`:

- Generate/obtain an AES-256-GCM key in `AndroidKeyStore`
  (`setRandomizedEncryptionRequired(true)`, no user-auth requirement).
- Encrypt `{device_id, token, desktop_id, pin, host, port}` as one JSON blob.
- Store ciphertext + IV in DataStore; never in plain SharedPreferences.
- `android:allowBackup="false"`; exclude credential files from backup/transfer.
- On revoke/pin mismatch/forget: delete the key alias and ciphertext.
- Do not use the deprecated `androidx.security:security-crypto`.

### 7.3 Pending event

One record: `event_id`, `barcode`, `format`, `scanned_at`, `sequence`, `context`,
`server_instance_id`. Encrypted with the same Keystore key or stored in app-private storage; no
product data. Cleared on acknowledgment or explicit discard.

---

## 8. Networking

- One `OkHttpClient` with:
  - connect 2 s, read 4 s, write 4 s, call 6 s;
  - `retryOnConnectionFailure(false)` (we own retries);
  - connection pool 5, keep-alive 10 s;
  - no redirects, no cookies;
  - TLS 1.2+ only;
  - a **pin-only `X509TrustManager`** (accepts exactly the leaf whose SPKI hash matches the paired
    pin, checks validity); standard hostname verification stays enabled; `CertificatePinner` is
    optional as a second check.
  - A one-certificate `KeyStore` is not used: same-key reissue changes the DER and would be rejected.
- `PairingClient` uses a temporary client with the QR pin; no token.
- `ScanClient` sends one request at a time; retries reuse the event id.
- `ContextClient` refreshes the checkout context after reconnect or a stale-context rejection.
- No cleartext: `network_security_config` denies cleartext; the app never connects over HTTP.
- Pin mismatch is a terminal error until re-pair; no "trust anyway".
- Host/port edit for a known desktop keeps the pin; a pin change requires re-pair.
- Error mapping:
  - transport/timeout → "not confirmed" or "not sent" depending on whether the request left;
  - 401/403 → clear credentials, go to Connection;
  - 429/503 → retry with backoff;
  - 200 status → render exactly.

### 8.1 Event identity

- `UUID.randomUUID()` per accepted physical scan.
- `sequence` increments monotonically per installation; persisted.
- One in-flight event; no offline queue.
- Persist before send; clear after a final response.

---

## 9. Battery and performance

- 720p analysis, ~10 fps; preview may run faster.
- `KEEP_ONLY_LATEST`; close proxies promptly.
- Camera off in background/pause.
- No wake locks; `KEEP_SCREEN_ON` only on the active scanner.
- No polling loop; connection health checks are event-driven (connect/resume/manual retry).
- Haptics/sound are short and user-controlled.
- Target: ≤ 200 MB PSS, ≤ 15%/h scanning, ≤ 2%/h idle background.
- Measure with `dumpsys meminfo`, `dumpsys battery`, `batterystats`, and Macrobenchmark if justified.
- Low-end devices: avoid blur/shadows, one animated scan line, no per-frame Compose state.

---

## 10. Build, signing, and distribution

- `debug` (test server allowed, verbose logs) and `release` (R8, no verbose logs, strict pin).
- Debug must not introduce cleartext or trust-all TLS shortcuts.
- Version catalog pins every dependency; no `+`, ranges, or snapshots. Enable dependency locking and
  artifact verification.
- `versionCode` monotonic; `versionName` semver.
- Signing key outside the repo; documented custody; never commit.
- APK for side-loading in v1 (no Play Store requirement). An AAB can be produced later.
- R8 rules for OkHttp/ML Kit as needed; test the release build, not just debug.
- Reproducible build settings where practical; record the toolchain.
- No dynamic feature modules; bundled ML Kit increases size but guarantees offline first use.
- APK target ≤ 30 MB.

---

## 11. Test seams

| Seam | Interface | Fake |
|---|---|---|
| Camera | `CameraController` / analyzer input | Fixture bitmaps / fake `ImageProxy` |
| Decoder | `BarcodeSource` / `BarcodeDecoder` | Pre-programmed detections |
| Clock | `Clock` | Fixed/virtual time |
| Event id | `EventIdGenerator` | Deterministic sequence |
| Transport | `Transport` | Scripted responses, delays, failures |
| Storage | `CredentialStore`, `PreferencesStore` | In-memory |
| Pending event | `PendingEventStore` | In-memory |
| Haptics/audio | `Feedback` | Recording fake |

JVM tests cover validator, ScanGate, retry, context, status mapping, credential-store logic (with a
fake crypto provider), and JSON. Instrumented tests cover CameraX binding, Keystore, TLS pinning, and
Compose UI.

---

## 12. Alternatives considered

| Decision | Chosen | Alternative | Why not |
|---|---|---|---|
| UI | Compose | XML Views | More boilerplate, weaker state model; Compose is stable |
| DI | Manual | Hilt | Build complexity for a tiny graph; harder to see the graph |
| Camera | CameraX | Camera2 | Camera2 vendor quirks and much more code |
| Analyzer | Manual `ImageAnalysis.Analyzer` | `MlKitAnalyzer` | Explicit backpressure/proxy lifetime; add `camera-mlkit-vision` only for a coordinate-mapped overlay |
| Target SDK | 36 | 37+ | 37 adds `ACCESS_LOCAL_NETWORK`; keep the CAMERA+INTERNET boundary in v1 |
| Decoder | Bundled ML Kit | Unbundled / Play Services | Requires download/Play Services; violates offline-first |
| HTTP | OkHttp | `HttpsURLConnection` | Pinning and timeouts are clearer; OkHttp is a justified dependency |
| Storage | Keystore + DataStore | Room | No relational data; Room is overkill |
| Modules | Single `:app` | Multi-module | 4 screens do not justify the ceremony |
| Background | None | Foreground service | Scanning while not visible is a privacy/duplicate risk |

---

## 13. Risks

| Risk | Mitigation |
|---|---|
| Camera/ML Kit variance by device | Device matrix; 720p/10 fps; tune in Phase 6 |
| Bundled model size | Measure APK; acceptable ≤ 30 MB |
| OkHttp pinning mistakes | One pinning class; matrix tests; no hostname bypass |
| Keystore loss after restore | Disable backup; re-pair on failure |
| Process death mid-send | Persist one event; server instance check; no auto-replay |
| Low-end jank | Flat UI; one animation; Macrobenchmark if needed |
| Rotation duplicate | Rebind test; ScanGate reset; ENV-04 |
| Android 17 target 37 forces `ACCESS_LOCAL_NETWORK` | Stay on 36; treat 37 as an approved future change with a permission flow |
| Compose BOM requires SDK 37/AGP 9 | Pin a BOM compatible with 36; record the mapping |
| No Android tooling/CI | Procure before Phase 1; local scripts first |

## 14. Open questions

1. Target SDK: 36 (recommended, CAMERA+INTERNET only) or 37+ with `ACCESS_LOCAL_NETWORK`?
2. Exact minSdk: 26 (recommended) or 24? CameraX/ML Kit support 21+, but TLS/Keystore/retail fleet
   favor 26.
3. OkHttp vs platform HTTP: OkHttp recommended; confirm the dependency is acceptable.
4. Bundled Noto Sans Arabic: yes (recommended); measure APK.
5. Portrait-first vs locked portrait: portrait-first, rotation supported (recommended).
6. Typed barcode fallback: allow (recommended) or USB only?
7. One active device per desktop (global decision).
8. Which devices are available for the matrix?
9. Bounded in-memory queue (Agent 2) vs one in-flight (recommended): one in-flight; confirm.
