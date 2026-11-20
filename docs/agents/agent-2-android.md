I’ll keep this read-only: first I’ll check the shared facts and the project plan for desktop/protocol constraints, then I’ll return the Android spec here without changing files.# Android Technical Specification — RawPOS Wireless Barcode Scanner

**Scope:** A foreground-only Android client that decodes product barcodes and transmits them to a paired RawPOS Desktop instance over the local network. The desktop remains the sole authority for lookup and business operations. The app has no cloud dependency and stores no barcode history.

## 1. Decision summary — the 10 decisions that matter

1. **Baseline:** Kotlin 2.x, one `:app` module, `minSdk 26`, `compileSdk 36`, `targetSdk 36`.
2. **Permission boundary:** Do not target API 37 in v1. Android 17 requires the new `ACCESS_LOCAL_NETWORK` runtime permission for apps targeting 37+, while targets 36 and below retain local-network access through `INTERNET`. This honors the requested permission limit; reconsider before raising target SDK.
3. **UI and navigation:** One Activity using Jetpack Compose and Navigation Compose. No scanner Activity, bound service, or foreground service.
4. **Camera and decoder:** CameraX with the rear camera and ML Kit’s **bundled** barcode model. Runtime barcode decoding works without Play Services or internet.
5. **Scan acceptance:** Accept only non-empty ASCII digits. Require a stable result across consecutive frames, select at most one barcode per scan, and do not infer or rewrite UPC/EAN values.
6. **Architecture:** MVVM, unidirectional state flow, screen-level ViewModels, and constructor injection through a small manual `AppContainer`.
7. **Transport:** OkHttp over HTTPS, with trust restricted to the desktop certificate delivered by the pairing QR and an SPKI pin. Never fall back to cleartext or trust-all TLS.
8. **Persistence:** DataStore for ordinary preferences; Keystore-backed AES-GCM encryption for pairing identity and credentials. No persisted barcode history or product data.
9. **Lifecycle:** Camera and transmission operate only in the foreground. Backgrounding stops the camera and cancels active network work; no wake locks or background scanning.
10. **Distribution:** Signed APK for shop side-loading. No Play Services, cloud updates, telemetry, or in-app updater.

## 2. Project structure and dependency baseline

Use a single Gradle application module. For this app’s three main flows—pair, scan, settings—package boundaries are sufficient; extra Gradle modules would add build and DI overhead without providing reuse.

```text
RawPOSMobileScanner/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/
        │   │   ├── values/strings.xml
        │   │   ├── values-ar/strings.xml
        │   │   └── xml/network_security_config.xml
        │   └── kotlin/com/rawpos/scanner/
        │       ├── RawPosScannerApplication.kt
        │       ├── MainActivity.kt
        │       ├── di/AppContainer.kt
        │       ├── domain/
        │       │   ├── model/BarcodeReading.kt
        │       │   ├── model/ScanEvent.kt
        │       │   ├── model/DeliveryResult.kt
        │       │   └── repository/ScanTransport.kt
        │       ├── data/
        │       │   ├── camera/CameraXSessionFactory.kt
        │       │   ├── camera/MlKitBarcodeDecoder.kt
        │       │   ├── camera/BarcodeCandidateSelector.kt
        │       │   ├── network/OkHttpScanTransport.kt
        │       │   ├── network/PinnedClientFactory.kt
        │       │   ├── persistence/PairingRecordStore.kt
        │       │   ├── persistence/AndroidKeyStoreSecretBox.kt
        │       │   └── persistence/PreferencesDataStore.kt
        │       ├── ui/
        │       │   ├── navigation/AppNavigation.kt
        │       │   ├── pairing/PairingScreen.kt
        │       │   ├── pairing/PairingViewModel.kt
        │       │   ├── scanner/ScannerScreen.kt
        │       │   ├── scanner/ScannerViewModel.kt
        │       │   ├── settings/SettingsScreen.kt
        │       │   └── theme/
        │       └── platform/CameraPermissionFlow.kt
        ├── test/
        └── androidTest/
```

`MainActivity` is the only Activity. Navigation Compose owns the Pairing, Scanner, and Settings destinations. The CameraX session is created and disposed with the scanner screen’s lifecycle; ViewModels must not retain Activity, View, or `PreviewView` references.

The theme belongs in `ui/theme`; camera and ML Kit code in `data/camera`; OkHttp and TLS setup in `data/network`; DataStore and encrypted pairing storage in `data/persistence`. Use Arabic as the assumed default and English as the secondary locale, with Compose RTL mirroring and mobile-specific typography derived from RawPOS’s Arabic-first design.

### Gradle and SDK baseline

| Setting | Candidate baseline |
|---|---|
| JDK / Gradle | JDK 17 / Gradle 8.13 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.2.21; use the matching Kotlin Compose compiler plugin |
| `compileSdk` / `targetSdk` / `minSdk` | 36 / 36 / 26 |
| Compose | Stable BOM compatible with compile SDK 36; candidate `2026.04.01`, subject to resolved-AAR verification |
| CameraX | `androidx.camera:camera-camera2`, `camera-lifecycle`, `camera-view`; candidate 1.6.2 |
| ML Kit | `com.google.mlkit:barcode-scanning:17.3.0` |
| Persistence / networking | `androidx.datastore:datastore-preferences`; `com.squareup.okhttp3:okhttp` |

**Version-catalog policy:** Put every direct plugin and library version in `gradle/libs.versions.toml`; use exact versions only—no `+`, ranges, snapshots, or `latest.release`. Pin the exact versions in Phase 1 after resolving dependencies, checking minimum compile SDK requirements, and running device smoke tests. Use the Compose BOM to align Compose artifacts and omit their individual versions. Enable Gradle dependency locking and dependency verification.

There is a material toolchain constraint: current Compose releases from 1.12 require `compileSdk 37` and AGP 9; the current BOM is therefore not suitable for this AGP 8 / SDK 36 baseline. Use the newest stable BOM whose resolved artifacts remain compatible with SDK 36, and record that mapping at Phase 1. AGP 8.13 supports API 36.1.

`minSdk 26` deliberately exceeds CameraX and bundled ML Kit’s technical minimums. It avoids supporting older retail devices, gives a consistent platform floor for TLS and Keystore-backed AES-GCM, and better fits the expected life of shop hardware. Android Keystore exists earlier, but AES-GCM and camera APIs are not the only considerations; the supported-device policy is the primary reason for this floor.

CameraX 1.7.0 became stable on November 18, 2026, shortly before this specification date. Prefer the more established 1.6.2 line for Phase 1, then evaluate 1.7.0 on representative shop devices before upgrading.

## 3. Runtime architecture

Use MVVM and unidirectional data flow. `ScannerViewModel` exposes an immutable `StateFlow<ScannerUiState>` containing pairing/transport state, camera state, queue count, and the latest acknowledgement. Compose collects it with `collectAsStateWithLifecycle`. User actions enter through ViewModel methods; the UI does not call repositories or platform services directly.

Boundaries:

- **Camera session:** owns CameraX use cases and ML Kit lifetime; emits validated candidate readings to the scanner flow. It is tied to the screen lifecycle, not application lifetime.
- **Scan coordinator:** handles stable-frame selection, duplicate suppression, event IDs, and the bounded in-memory send queue.
- **Transport repository:** maps an accepted `ScanEvent` to the protocol agreed with Agent 4 and returns typed transport or desktop outcomes.
- **Pairing store:** reads and writes the encrypted device identity, token, endpoint, and paired certificate.
- **Preferences repository:** wraps DataStore for language and scanner UI preferences.

Use **manual constructor injection** through `AppContainer`. The graph is small and there is one Gradle module; Hilt would add KSP/compiler and generated-code dependencies without a clear benefit. The container is the composition root, not a global service locator: construct it once in the Application, pass dependencies to ViewModel factories, and instantiate fakes directly in tests.

ViewModel work belongs in `viewModelScope`; blocking file and Keystore operations use `Dispatchers.IO`. Do not use `GlobalScope`, `runBlocking` on the UI thread, or uncancelled app-wide coroutine jobs. Wrap OkHttp’s asynchronous `Call` so coroutine cancellation calls `Call.cancel()`. Give the camera analyzer a single-thread executor and a clear disposal path: remove the analyzer, unbind use cases, close ML Kit after outstanding tasks complete, and ignore stale callbacks after session replacement.

Do not add a generic event bus, universal plugin framework, local database, background sync engine, generic permission framework, or abstraction for every button. In particular, do not create a repository around each frame or expose camera frames to the ViewModel.

## 4. CameraX pipeline

Use `ProcessCameraProvider` with `Preview` and `ImageAnalysis`, bound to the scanner screen’s lifecycle. Present preview in a `PreviewView` hosted by Compose via `AndroidView`. Use the rear camera only. Bind after the provider future completes and permission is granted; on resume, check permission and rebind if needed. On stop, unbind to release the camera. Ignore late provider/task completions for a stale screen session.

Configure analysis for `YUV_420_888` at a target around **1280×720**; accept the closest supported lower resolution and adjust only after device testing. This is adequate for retail 1D codes in normal use while avoiding native multi-megapixel frames. Use `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`, a single analyzer executor, and a nominal 8–10 analysis frames per second. Do not convert frames to `Bitmap` or queue them.

```text
Rear camera
  ├─ Preview → PreviewView → Compose viewfinder
  └─ ImageAnalysis (YUV, ~1280×720, KEEP_ONLY_LATEST)
       → one analyzer / one ML Kit task in flight
       → InputImage(media.Image, rotationDegrees)
       → filter + candidate selection + stable-frame check
       → bounded in-memory scan queue
       → OkHttp HTTPS request
       → desktop acknowledgement/rejection → StateFlow UI
```

Use a **manual `ImageAnalysis.Analyzer`**, not CameraX’s `MlKitAnalyzer`, in v1. There are no bounding-box overlays requiring CameraX/ML Kit coordinate mapping. The manual path makes frame throttling, one-task-at-a-time processing, filtering, and error handling explicit. It avoids the `androidx.camera:camera-mlkit-vision` dependency; add it only if a later overlay requirement justifies it.

Build `InputImage` from the `ImageProxy`’s `media.Image` and `imageInfo.rotationDegrees`; do not rotate pixels yourself. The media image remains valid only while its proxy is open. Close the `ImageProxy` on every path—after ML Kit’s Task completes, including failure; close immediately if the image is absent or task creation fails. Never retain a proxy or media image. Rebind/recreate the session on resume or when the preview surface is recreated, and guard async results with a session generation token.

Camera controls:

- **Focus:** use CameraX’s continuous autofocus defaults; allow tap-to-focus via `PreviewView.meteringPointFactory` and `CameraControl.startFocusAndMetering`.
- **Torch:** off by default; offer an explicit button only if the rear camera has flash. Turn it off when scanning stops. In low light, explain focus/lighting and let the cashier choose torch; do not flash automatically.
- **Zoom:** support pinch-to-zoom through `CameraControl.setZoomRatio`, plus reset-to-1×. Enable ML Kit zoom suggestions using `setZoomSuggestionOptions()` and the camera’s actual maximum zoom, with a tested product cap and a guard for a closed camera. This is supported by the bundled model. If auto-zoom proves confusing in shop trials, disable it without changing transport.
- **Autofocus and low light:** if a device lacks autofocus or flash, show a guidance state rather than repeatedly retrying at full rate.
- **Thermal:** sustained torch and camera use heat the phone. Keep analysis throttled; on API 29+ reduce analysis rate or turn off torch at severe thermal status. Do not run an unbounded thermal monitor.

Use the default back-camera selector. Do not offer a front-camera mode in v1; it is generally less suitable for retail labels and increases configuration paths. If the rear camera is unavailable, show a recoverable unsupported-camera state.

For multiple detected barcodes, accept only one candidate inside the central guide region. If multiple candidates make the target ambiguous, do not transmit; prompt the user to isolate one label. Require the same candidate in two consecutive analyzed frames and latch it until absent from the guide for about 500 ms. This suppresses unstable decodes and repeated emissions while a product is held in view.

**Latency budget (targets to measure, not vendor guarantees):**

| Stage | Budget |
|---|---:|
| Wait for next analyzed frame at 10 fps | 0–100 ms |
| Frame handoff and candidate validation | ≤10 ms |
| Warm ML Kit decode | p95 ≤120 ms |
| Two-frame stability confirmation | 100–200 ms |
| Event creation/enqueue | ≤5 ms |
| Warm HTTPS request and desktop response on shop LAN | p95 ≤200 ms |
| Stable barcode to desktop acknowledgement | p95 ≤500 ms; verify on reference devices |

A new TLS connection can add several hundred milliseconds; keep a pooled client while active and show connection/retry state rather than claiming a fixed end-to-end latency.

## 5. ML Kit integration

Use **bundled ML Kit**, not the dynamically delivered Play Services model:

- `com.google.mlkit:barcode-scanning:17.3.0` — model included in the app.
- Pairing mode uses an ML Kit client configured for QR codes only. Product scan mode uses a separate client configured for relevant 1D formats; pairing QR contents are never sent as product scans.

Google documents the bundled model as adding about **2.4 MB** to app size and being immediately available; the unbundled alternative is about 200 KB but may need a model download. Bundling is required for reliable offline operation and for devices without Play Services. Barcode decoding itself needs no network and no location permission; the app’s `INTERNET` permission is only for sending to RawPOS on the LAN.

Map ML Kit constants to an app enum, never use raw integer constants outside the adapter:

| ML Kit format | App-level enum |
|---|---|
| `FORMAT_EAN_13`, `FORMAT_EAN_8` | `EAN_13`, `EAN_8` |
| `FORMAT_UPC_A`, `FORMAT_UPC_E` | `UPC_A`, `UPC_E` |
| `FORMAT_CODE_128`, `FORMAT_CODE_39`, `FORMAT_CODE_93` | `CODE_128`, `CODE_39`, `CODE_93` |
| `FORMAT_CODABAR`, `FORMAT_ITF` | `CODABAR`, `ITF` |
| `FORMAT_QR_CODE` in pairing mode only | `PAIRING_QR` |
| Anything else | `UNSUPPORTED` |

Transmit only a non-null, non-empty `rawValue` that consists exclusively of ASCII `0`–`9`. Do not convert Unicode numerals, parse QR payloads as product data, apply check-digit rules, or impose an EAN-specific length. RawPOS stores opaque digit strings and does not enforce a maximum length; Agent 4 must define any protocol request limit and the desktop must validate it too.

ML Kit does not expose a calibrated confidence score for a decoded barcode. Treat two consecutive matching frames as a stability heuristic, not a confidence metric. Its known failure modes include blur, small/distant symbols, glare, poor lighting, unsupported formats, and format-specific decode behavior. ML Kit documents an edge case where a leading-zero EAN-13 can be identified as UPC-A with the leading zero omitted. **Preserve `rawValue` exactly; do not silently add or remove zeroes based on reported format.** Test UPC-A 12-digit and zero-prefixed EAN-13 labels against the actual catalog convention before release. Because RawPOS treats barcodes as opaque, normalization belongs in an explicitly agreed business rule, not in the Android camera adapter.

## 6. Lifecycle and Android platform behavior

- **Process death:** persist pairing/preferences only. `SavedStateHandle` may restore the route and harmless UI flags, never a barcode or pending payload. After process recreation, re-check permission, rebind the camera, and reconnect. A pending request can have an unknown outcome if the process died before its acknowledgement; show that uncertainty rather than silently retransmitting with a new event ID.
- **Configuration change:** ViewModels retain UI state; Compose recreates the preview surface and CameraX session. Do not lock orientation. Support portrait, landscape, foldables, and tablet-sized windows.
- **Screen awake:** set `FLAG_KEEP_SCREEN_ON` only while the scanner is actively open; allow a user preference to disable it. Remove it immediately when scanning stops.
- **Backgrounding:** on `ON_STOP`, stop/unbind camera, turn off torch, cancel in-flight calls, evict or allow the OkHttp pool to close, and send nothing in background. Keep only pairing state and any pending event data in volatile memory while the process survives; require explicit retry on return. No persistent offline queue.
- **Doze/battery:** no scheduled work or background retries, so do not request battery-optimization exemptions. No wake locks in v1.
- **Foreground service:** not needed because scanning stops in background. No service means no service notification and no `POST_NOTIFICATIONS` requirement.
- **Back and window behavior:** use Navigation Compose and normal predictive-back handling; navigating away disposes the scanner. Call `enableEdgeToEdge()` and handle safe drawing/system-bar insets explicitly. Use RTL-aware Compose layouts and test cutouts, gesture navigation, and three-button navigation.

## 7. Permissions

Declare only:

- `CAMERA` — dangerous runtime permission.
- `INTERNET` — normal install-time permission for HTTPS requests.
- `POST_NOTIFICATIONS` — **omit**; v1 has no notifications or foreground service.
- `VIBRATE` — **omit**; use Compose/view haptic feedback if desired, without direct vibrator access.

Do not request location, storage/media, Bluetooth, Wi-Fi state, or `ACCESS_NETWORK_STATE`. No location permission is needed to scan a barcode or connect to the paired endpoint.

Request camera permission only after an explicit user action. Show a short rationale before the system prompt explaining that the camera is used for barcode and pairing-QR scanning. If denied, leave pairing/scanning unavailable and offer a retry. If the user has denied previously and `shouldShowRequestPermissionRationale()` is false, show an “open App settings” path instead of looping the system request; track whether the app has already asked, because false can also mean “not asked yet.” On return from Settings, re-check permission before rebinding.

**Android 17 constraint:** apps targeting API 37+ need `ACCESS_LOCAL_NETWORK` to connect to LAN devices. Apps targeting 36 or lower retain implicit local access through `INTERNET`; do not declare or request the new permission while targetSdk remains 36. If a future Play/distribution policy requires target 37, the permission list must be revisited and approved before that upgrade.

## 8. Local persistence and secrets

Use Preferences DataStore for small non-secret settings: language, theme, keep-screen-on preference, and scanner controls. Keep a single DataStore instance in the app container.

Store the pairing record—random app-install device UUID, endpoint, server certificate/pin, and pairing token—in an app-private encrypted record. Generate the UUID locally; never use IMEI, serial number, or another hardware identifier. Encrypt and decrypt through a small `SecretBox` wrapper backed by an Android Keystore AES key using `AES/GCM/NoPadding`. Store only the generated IV, ciphertext, and authentication tag in a versioned envelope; use the platform-generated IV and authenticated encryption. Do not roll custom cryptography or export the key. StrongBox may be used if available, but must not be required.

`androidx.security:security-crypto` is not the default choice: its APIs were deprecated in stable 1.1.0 in favor of platform APIs and direct Android Keystore use, with no subsequent releases planned. The small wrapper has one purpose and can be tested through an injected interface.

Do not persist barcode history, product data, camera frames, or scan payloads. Do not include barcodes in logs, crash reports, analytics, or telemetry. Keep only a bounded, volatile queue of unacknowledged events; the UI should show status/count, not a durable scan list. Disable Android backup/device transfer for pairing secrets—restored ciphertext cannot be decrypted if the Keystore key did not transfer. Key loss or corruption means explicit re-pairing, never silently generating a replacement key and accepting stale ciphertext.

## 9. Networking client

Choose **OkHttp** (`com.squareup.okhttp3:okhttp`) over `HttpsURLConnection` or Ktor. OkHttp provides connection pooling, configurable TLS and timeouts, and straightforward cancellation; it is a smaller fit than Ktor plus an engine and serializer stack. Do not add Retrofit or a general networking framework until the protocol requires it.

The QR pairing contract must provide a versioned endpoint, the desktop’s certificate/trust material or a reviewed pin-bootstrap format, and a short-lived pairing credential. Per paired desktop, build a client whose trust is restricted to the QR-supplied certificate; additionally pin its SHA-256 SPKI using OkHttp’s `CertificatePinner`. Keep hostname verification enabled. The desktop certificate must include a SAN matching the endpoint host or IP. **A pin alone does not establish normal certificate-chain trust**; if the QR carries only a digest, Agent 4 must define and review the trust-bootstrap behavior. Never use a permissive `TrustManager`, disable hostname verification, or fall back to HTTP. A certificate rotation requires deliberate re-pairing unless a rollover procedure is agreed.

Use a `Network Security Config` with cleartext disabled. The QR-selected client should not follow redirects to another endpoint. Do not probe arbitrary hosts or perform cloud discovery; the QR supplies the local endpoint.

Android’s protocol requirements for Agent 4:

- One immutable event identifier per scan, reused on every retry as the idempotency key; include the paired device identity and raw ASCII digits according to the agreed contract.
- Bounded timeouts: initial targets of 3 s connect, 5 s read/write, and 10 s overall call.
- No unbounded automatic retry. Retry only transient transport failures, with a small bounded backoff, and reuse the same idempotency key. Do not retry TLS/pin failures, authentication failures, malformed protocol responses, or business rejections.
- Use OkHttp’s pooled connection while the scanner is active; no keepalive heartbeat or background connection. Cancellation must call `Call.cancel()`.
- Maintain a bounded FIFO in memory (initial cap: 16 events). On overflow, pause accepting scans and show a clear message. On transport failure, pause delivery and let the cashier retry or discard; never persist an offline queue.
- Distinguish `TransportFailure` (timeout, unreachable host, TLS/pin mismatch) from a parsed desktop business rejection. Only an acknowledged outcome removes an event. Show a minimal desktop acknowledgement/rejection; do not display or cache product data.

The exact endpoint, QR schema, request/response fields, error codes, and idempotency semantics belong to Agent 4’s protocol. Android must not invent them.

## 10. Battery and performance targets

These are release targets to measure on a named low/mid-range shop phone, not promises across all OEMs:

- Cold app time-to-first-draw: **≤2 s**; permission-granted preview: **≤1.5 s**; first bundled-model decode after preview: **≤1 s**.
- Warm scan-to-desktop-ack: **p95 ≤500 ms** on a stable shop LAN.
- Warm process PSS budget: **120–180 MiB expected**, investigate sustained **>200 MiB** on the reference 4 GB device.
- Active scan drain: target **≤12% battery over a 30-minute** test at fixed brightness, torch off; record repeated runs and thermal status.
- No camera use when not visible, no full-resolution frame copies, 8–10 fps analysis, `KEEP_ONLY_LATEST`, and torch off by default. No wake locks or background work.
- Bundled ML Kit adds about **2.4 MB** per Google’s current documentation. Measure final minified APK and installed size; do not estimate from Gradle dependency declarations alone.

Verify startup with Macrobenchmark, memory with Android Studio/Perfetto, battery/thermal with repeated real-device runs, and scan latency from camera candidate timestamp to desktop acknowledgement. Include low-light, long-run, and camera-rebind tests in the release checklist.

## 11. Build, signing, and distribution

Maintain `debug` and `release` build types. Debug uses a distinct application ID suffix and test credentials; it must not introduce cleartext or trust-all TLS shortcuts. Release enables R8/minification and resource shrinking. Validate the minified release on devices because CameraX/ML Kit use library rules that must survive shrinking.

Keep the release signing key outside source control and ordinary CI logs. Designate a custodian, keep encrypted offline backups, and document recovery: losing the key prevents in-place updates; a changed signer requires uninstall/reinstall and loses local pairing. `versionCode` increases monotonically; `versionName` follows semantic versioning.

For shops, distribute a signed universal APK over USB or another approved local transfer. An AAB is useful for Play distribution but is not the simplest offline side-load artifact. Updates must use the same package name and release signing key. Provide a SHA-256 checksum and release notes; do not implement self-update or require Play Services. The build uses Google/Maven repositories at development time, but the installed app performs no online dependency/model download.

For reproducibility, commit the Gradle wrapper and distribution checksum, pin toolchain/plugins/dependencies, enable dependency locking and artifact verification, and record the JDK/build inputs for each release. Shop troubleshooting should cover enabling “install unknown apps” for the chosen installer, camera permission, same-LAN/guest-Wi-Fi client isolation, desktop firewall/port availability, device time/certificate validity, certificate mismatch requiring re-pairing, and update signature mismatch. Never troubleshoot a TLS failure by enabling cleartext.

## 12. Testing hooks

Inject `BarcodeSource`/scanner-session factory, `ScanTransport`, `PairingStore`, `SecretBox`, monotonic `Clock`, and event-ID generator. A fake scanner feeds decodes, multiple candidates, invalid characters, and failures; a fake transport simulates delay, cancellation, acceptance, business rejection, TLS failure, and ambiguous timeout. Use the monotonic clock for duplicate suppression and retry timing; wall-clock time is separate for certificate validity and display.

JVM tests should cover format mapping, ASCII validation, multi-candidate selection, consecutive-frame stability, re-arm behavior, queue bounds, idempotency-key reuse, and ViewModel state. Instrumented tests should cover permission flows, rotation/rebind, process recreation, real CameraX/ML Kit, and an offline decode with no Play Services. Add a TLS test server for trusted QR certificate, wrong pin, expired certificate, hostname mismatch, and redirects. Run end-to-end tests against the desktop protocol in an isolated LAN. No hidden static singleton should prevent test construction.

## 13. Android-specific risks and mitigations

1. **OEM camera/HAL differences or bind failures:** use CameraX 1.6.2, conservative resolution, fallback UI, and a representative device matrix.
2. **ImageProxy leak stalls analysis:** close on every ML Kit completion/failure path; test long sessions and repeated rebinds.
3. **Bundled model accidentally replaced with unbundled artifact:** verify decoding in airplane mode and on a device without Play Services.
4. **Leading-zero UPC/EAN mismatch:** preserve decoder `rawValue`, test both catalog conventions, and escalate canonicalization to the desktop/protocol owners.
5. **False or repeated reads mutate the sale multiple times:** require stable frames, target one central barcode, latch until the label leaves, and use protocol idempotency.
6. **Non-digit token hits RawPOS’s name-search fallback:** filter on Android and require the desktop receiver to validate digits independently.
7. **Self-signed TLS fails or is bypassed:** QR supplies trust material; verify SAN and SPKI; keep hostname verification; never offer a trust-all recovery path.
8. **Wi-Fi loss or ambiguous timeout duplicates a scan:** reuse event IDs on retries, bound the volatile queue, and distinguish “not acknowledged” from “rejected.”
9. **Android 17 target upgrade blocks LAN traffic:** hold target 36 under the current permission boundary; make target 37 an explicit permission and toolchain decision.
10. **Long camera/torch sessions drain or overheat the phone:** throttle frames, stop in background, default torch off, and benchmark 30-minute sessions.
11. **Permission denied or revoked in Settings:** explain the camera requirement, track prior requests, provide a Settings path, and re-check on resume.
12. **Keystore key is lost after restore/device replacement:** exclude pairing data from backup and provide an explicit re-pair flow.
13. **Desktop IP/certificate changes:** QR must refresh the endpoint and trust material; show “re-pair required” rather than retrying insecurely.
14. **Process death while an event is in flight:** volatile events are lost; show uncertain-delivery guidance on backgrounding, and do not silently generate a new retry ID.
15. **Camera scan works but desktop receiver is unsafe to call from a network thread:** desktop must marshal validated scans to its GUI thread; Android cannot compensate for desktop thread-safety gaps.

## 14. Alternatives considered

- **Compose vs XML:** choose Compose for a new, small app with RTL layouts and state-driven screens. XML is viable but offers no advantage here.
- **Manual DI vs Hilt:** choose manual injection for the one-module graph. Hilt is reasonable if the app grows to more services/workers or shared navigation-scoped dependencies; it is unnecessary now.
- **CameraX vs Camera2:** choose CameraX for lifecycle binding and OEM compatibility. Camera2 is justified only if device testing proves CameraX cannot meet a scanner-specific control requirement.
- **Manual analyzer vs `MlKitAnalyzer`:** manual analyzer wins for explicit backpressure and proxy lifetime. `MlKitAnalyzer` becomes attractive if coordinate-mapped bounding overlays are added.
- **Bundled vs unbundled ML Kit:** bundle for no-network reliability; accept the roughly 2.4 MB increase.
- **OkHttp vs `HttpsURLConnection` vs Ktor:** OkHttp best fits the pinned HTTPS client and coroutine cancellation. `HttpsURLConnection` reduces a dependency but complicates the dynamic pin/client setup; Ktor adds an engine and more configuration surface.
- **Foreground service vs Activity-only:** Activity-only is correct because scanning is explicitly foreground work. A foreground service would add notifications, lifecycle complexity, and permission work without a requirement.
- **`security-crypto` vs Keystore wrapper:** use the small platform wrapper because the Jetpack APIs are deprecated; do not adopt deprecated encryption APIs merely to avoid a small, testable adapter.

## 15. Verified facts, assumptions, dependencies, and open questions

### Verified

The shared RawPOS fact pack confirms that the desktop barcode store accepts non-empty ASCII digits, treats values as opaque strings, has no barcode maximum or EAN/UPC normalization, and currently has no network receiver or public non-UI scan entry point. `CheckoutService.resolve_scan()` mutates the in-memory checkout draft, is not thread-safe, and falls back from unmatched barcodes to product search. Therefore Android must reject non-digits, and the desktop receiver must validate independently and dispatch safely onto the GUI thread. Desktop business behavior remains authoritative.

Official Android references: [bundled ML Kit barcode scanning and size](https://developers.google.com/ml-kit/vision/barcode-scanning/android), [CameraX releases](https://developer.android.com/jetpack/androidx/releases/camera), [Android 17 local-network permission](https://developer.android.com/privacy-and-security/local-network-permission), [Compose compiler/SDK compatibility](https://developer.android.com/develop/ui/compose/compiler), [Android Keystore](https://developer.android.com/privacy-and-security/keystore), and [security-crypto deprecation](https://developer.android.com/privacy-and-security/cryptography#deprecated_functionality).

### Assumptions and cross-agent dependencies

- The shop installs a signed APK locally; Google Play distribution is not a v1 requirement.
- Pairing is via a RawPOS-generated QR containing an endpoint and reviewed TLS trust material. Agent 4 owns its format, token lifecycle, certificate generation/rotation, and wire protocol.
- Agent 4 must define idempotency, acknowledgements/rejections, retry semantics, protocol size limits, and any barcode-format metadata.
- Desktop architecture must provide a safe receiver-to-UI-thread seam without changing checkout rules implicitly.
- Arabic-first/English UI and RTL behavior are assumed from the desktop product context; confirm with the UX/design owner.

### Open questions for the user

1. Is the “no permissions beyond CAMERA/INTERNET” limit hard? If yes, keep targetSdk 36; target 37 requires `ACCESS_LOCAL_NETWORK`.
2. Are catalog UPC-A values stored as 12 digits or as 13-digit EAN-13 with a leading zero? Android will not normalize without an explicit decision.
3. Should scanning pause on a LAN failure with a bounded in-memory queue, as specified, or should it stop accepting scans immediately?
4. What maximum barcode length should Agent 4 put in the protocol, given that RawPOS currently has no maximum?
5. Are shop devices Android 8/API 26 or newer, and is APK-only distribution acceptable?