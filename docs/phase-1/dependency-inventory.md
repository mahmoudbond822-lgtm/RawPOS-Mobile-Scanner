# Phase 1 — Dependency Inventory

**Scope:** only the dependencies strictly required to build the Phase 1 foundation. No camera, network,
barcode, QR, database, or DI framework is present.

---

## 1. Build plugins

| Plugin | Version | Purpose |
|---|---|---|
| `com.android.application` | 8.13.2 | Android application build |
| `org.jetbrains.kotlin.android` | 2.2.21 | Kotlin compilation |
| `org.jetbrains.kotlin.plugin.compose` | 2.2.21 | Compose compiler, matched to Kotlin |

## 2. Runtime dependencies

| Artifact | Version | Why it is required |
|---|---|---|
| `androidx.core:core-ktx` | 1.17.0 | Kotlin extensions used by the settings store (`SharedPreferences.edit`) and platform interop |
| `androidx.activity:activity-compose` | 1.12.4 | `ComponentActivity`, `setContent`, edge-to-edge |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.9.4 | `collectAsStateWithLifecycle` |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.9.4 | `viewModel(factory = ...)` in Compose |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.9.4 | `viewModelScope` |
| `androidx.navigation:navigation-compose` | 2.9.8 | Single-Activity navigation |
| `androidx.compose:compose-bom` | 2026.04.01 | Aligns Compose artifact versions |
| `androidx.compose.ui:ui` | via BOM (1.11.0) | Compose runtime/UI |
| `androidx.compose.ui:ui-graphics` | via BOM (1.11.0) | `Color`, shapes, drawing |
| `androidx.compose.material3:material3` | via BOM (1.4.0) | Material 3 components and theming |

`androidx.compose.ui:ui-tooling` and `ui-tooling-preview` were deliberately **removed**: Phase 1 has no
`@Preview` functions, so they were not strictly required. They can return when previews are added.

## 3. Test dependencies

| Artifact | Version | Scope | Purpose |
|---|---|---|---|
| `junit:junit` | 4.13.2 | `testImplementation` | JVM unit tests |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.10.2 | `testImplementation` | Main-dispatcher replacement and coroutine tests |
| `androidx.test.ext:junit` | 1.2.1 | `androidTestImplementation` | Instrumented test runner |
| `androidx.test.espresso:espresso-core` | 3.6.1 | `androidTestImplementation` | Android test infrastructure |
| `androidx.compose.ui:ui-test-junit4` | via BOM | `androidTestImplementation` | Compose smoke test |
| `androidx.compose.ui:ui-test-manifest` | via BOM | `debugImplementation` | Test activity manifest for Compose |

## 4. Deliberately not added

| Candidate | Why absent |
|---|---|
| CameraX | Phase 3 |
| ML Kit barcode scanning | Phase 3 |
| OkHttp / Retrofit / Ktor | Phase 2/4 |
| QR library | Phase 2 |
| DataStore | Phase 1 uses platform `SharedPreferences`; the repository interface allows a later DataStore replacement without a new dependency now |
| Room | The phone stores no business data |
| Hilt / Koin | Manual `AppContainer` is sufficient for the current graph |
| Kotlin serialization / Gson | No wire format in Phase 1 |
| Coil / image loaders | No remote images |
| Firebase / analytics / crash reporting | Forbidden by the project constitution |
| `androidx.security:security-crypto` | Deprecated; Keystore wrapper arrives with credentials in Phase 2 |

## 5. Version compatibility evidence

The first build failed on the newest Navigation/Lifecycle artifacts. AAR metadata was inspected before
choosing the pins:

| Artifact | `minCompileSdk` | `minAndroidGradlePluginVersion` | Decision |
|---|---:|---:|---|
| Activity 1.12.4 | 36 | 8.9.1 | accepted |
| Core 1.17.0 | 36 | 8.9.1 | accepted |
| Compose UI 1.11.0 | 35 | 8.6.0 | accepted |
| Navigation 2.9.8 | 35 | 8.6.0 | accepted |
| Navigation 2.10.2 | **37** | **9.1.0** | rejected |
| Lifecycle 2.9.4 | 35 | 8.6.0 | accepted |
| Lifecycle 2.11.0 | **37** | **9.1.0** | rejected |

The project stays on compile/target SDK 36 to keep the Phase 1 permission boundary
(CAMERA + INTERNET only later; no Android 17 `ACCESS_LOCAL_NETWORK`). Raising Navigation/Lifecycle
requires compileSdk 37 and AGP 9.1, which is a separate decision.

## 6. Policy

- Every direct version is pinned in `gradle/libs.versions.toml`; no `+`, ranges, or snapshots.
- The Gradle wrapper pins the distribution and its checksum is verified by Gradle.
- Dependency locking and artifact verification are **not enabled** in Phase 1. They are recommended
  before Phase 2 adds networking/crypto dependencies.
- No dependency is added for a feature that is not in the current phase.
