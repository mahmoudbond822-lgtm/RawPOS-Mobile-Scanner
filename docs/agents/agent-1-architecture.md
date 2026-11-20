The key constraint is sharper than “don’t call checkout off-thread”: `resolve_scan()` also falls back to product-name search, and it does not itself require an open shift. The mobile path therefore needs a distinct exact-barcode submission seam, plus a queued GUI-thread bridge; I’m treating the small `CheckoutService` addition as an approval gate, not an assumed change.# RawPOS Mobile Scanner v1.0 — Architecture Proposal

## 1. Executive decision summary

- **Keep RawPOS Desktop as the only business authority.** Android sends barcode text and transport metadata; it never looks up products, prices, quantities, inventory, carts, or payments. This matches the stated scope (`shared-facts.md:170-178`).
- **Use an explicitly enabled, LAN-only HTTPS receiver.** Keep it off by default, bind only to a selected private interface, and make no outbound calls. The trade-off is manual setup; the benefit is keeping the desktop’s offline guarantee meaningful.
- **Run network handling off the Qt event loop.** A receiver thread parses, authenticates, rate-limits, and deduplicates requests, but has no database, repository, service, or widget access.
- **Cross into checkout only through a Qt queued-signal bridge.** The bridge resolves the current Sales screen on the GUI thread and rejects stale requests; the receiver thread never calls business code.
- **Add a distinct exact-barcode checkout path.** Do not reuse `CheckoutService.resolve_scan()` for mobile: it falls back to product-name search. Approval is required for a narrow `resolve_barcode()` addition; without it, do not ship mobile scanning.
- **Admit scans only for a signed-in, full-mode session with an open shift and active Sales screen.** Reject rather than navigate, queue for later, or mutate a hidden cart.
- **Use per-event IDs and a checkout-context token.** Retries of the same event in the same desktop process return the original outcome; requests for a previous checkout context are rejected instead of landing in a subsequent sale.
- **Keep mobile state out of the POS database.** Preserve the existing in-memory cart draft and avoid a schema migration. If paired-device credentials persist, store them in a DPAPI-protected Windows secret store—not `config.json`.
- **Keep the dependency surface small.** Use Python’s standard library and the existing `cryptography` dependency on desktop; use Android platform networking and secure storage. Add an offline barcode decoder only after Barcode-agent confirmation.
- **Stop or disarm the receiver before sign-out, recovery, restore, and shutdown.** This prevents queued requests from crossing lifecycle transitions and keeps the receiver independent of database availability.

## 2. System context

### Boundary diagram

```text
┌──────────────────────────────┐
│ Android Scanner              │  Untrusted client, even after pairing
│ camera decode; no POS rules  │
└──────────────┬───────────────┘
               │ HTTPS over selected private LAN interface
               │ scan: barcode + event ID + desktop epoch + checkout context
               │ auth: per-device token; TLS identity pinned during pairing
               ▼
┌─────────────────────────────────────────────────────────────────┐
│ RawPOS Desktop process                                           │
│                                                                 │
│  TLS/HTTP receiver thread                                       │
│  strict parsing · auth · rate limits · event dedupe             │
│  NO UI, database, repository, session, or business-service calls │
│               │ Qt queued command / bounded completion          │
│               ▼                                                 │
│  GUI-thread MobileScanBridge                                    │
│  signed-in/full-mode/shift/Sales/context checks                  │
│               │                                                 │
│               ▼                                                 │
│  Active CheckoutView.submit_scan()                              │
│               │                                                 │
│               ▼                                                 │
│  CheckoutService.resolve_barcode() → ProductRepository → DB     │
└─────────────────────────────────────────────────────────────────┘
               │
               └── Small outcome acknowledgement only; no product
                   names, prices, totals, or cart state returned
```

**Actors and trust zones.** The cashier uses RawPOS Desktop; an owner pairs and revokes devices. The Android device and local network are untrusted input sources. The receiver is a restricted ingress adapter, not a second business tier. RawPOS Desktop’s GUI, services, repositories, and SQLCipher database remain authoritative.

**What crosses the network boundary:** a versioned scan event containing an opaque event ID, desktop-process epoch, checkout-context token, and unmodified barcode string; a per-device credential; and a minimal acknowledgement or refusal. The phone must not receive product records, prices, stock, cart contents, invoice data, or payment capabilities.

**State ownership:** Android owns camera state and pending transport metadata. The desktop owns pairing, connection admission, product resolution, cart state, shift rules, and all business records. Existing checkout drafts are in memory in `CheckoutService` (`src/rawpos/services/checkout_service.py:253-267`).

**Explicitly not in v1:** cloud relay, Internet service, remote product/catalogue search, mobile cart or pricing, discounts, payments, invoice finalization, inventory changes, shared database access, HID keyboard emulation, automatic Sales navigation, and queued offline scans that silently replay later. A single pending event may be retained for safe retry, but disconnected scanning is disabled.

## 3. Desktop integration strategy

`CheckoutService.resolve_scan()` is not a safe mobile entry point: it tries exact barcode lookup, then falls back to general product search, and a successful result mutates the draft (`src/rawpos/services/checkout_service.py:523-539`). General search matches names as well as barcodes (`src/rawpos/repositories/product_repository.py:207-217`). Product barcodes are nonempty ASCII digits (`src/rawpos/migrations/versions/0004_products_inventory.py:40-48`; `src/rawpos/services/product_service.py:59-64`). The mobile path must preserve the token as text and do exact barcode lookup only.

| Candidate | Thread safety and fidelity | Blast radius, tests, failures | Latency and security |
|---|---|---|---|
| **a. Network thread calls `resolve_scan()` directly** | Unsafe: it would mutate the shared draft and use the shared repository/database path outside the GUI thread. It also inherits name-search fallback. The service has mutable draft state and no thread-affinity guard (`checkout_service.py:253-267, 523-539`). | Small code change, but broad race surface with UI reads and actions. Easy to unit-test in isolation, hard to test concurrent behavior reliably. Restore/close and checkout operations can race. | Lowest latency, wrong choice. A paired network client would directly invoke business mutation and could add unintended products. |
| **b. Network thread emits a Qt queued signal to a GUI bridge** | Safe if the slot is explicitly queued and the bridge calls the live view on the GUI thread. Calling current `scan()` is insufficient: it reads `scan_edit` and returns no result (`checkout_view.py:441-457`). Add a source-specific `submit_scan()` instead. | Narrow seam and straightforward `pytest-qt` coverage. Requires a bounded reply path so the network handler can acknowledge or time out; queued requests must be cancelled or rejected if stale. | One Qt event-loop turn plus desktop lookup. Best balance of fidelity and isolation. |
| **c. Synthetic `QKeyEvent` via `QApplication.postEvent`** | Event delivery can reach the GUI thread, but it impersonates keyboard input rather than a scan command. The application-wide wedge filter recognizes fast printable keys and Enter (`checkout_view.py:286-372`). | High blast radius: focus, quantity editing, tender entry, and Enter behavior are involved. Existing tests explicitly protect those paths (`tests/gui/test_checkout_ui.py:428-525`). Event timing and focus make tests brittle. | Low latency, poor security: remote input can act on whichever widget has focus or accidentally interact with payment controls. Reject. |
| **d. Local IPC or queue consumed by a UI timer** | A bounded in-process queue can be thread-safe if only the GUI drains it; filesystem/local IPC adds another parser and trust surface. | Testable, but adds polling, shutdown, backpressure, and stale-item handling. An unbounded queue risks replaying scans into a later cart. | Timer interval adds latency; IPC ACLs and protocol handling add security work. Useful only if the receiver becomes a separate process, which is unnecessary for v1. |
| **e. Typed command + queued bridge + live-view resolver — recommended** | Receiver emits only a validated `ScanCommand`; a GUI-thread bridge resolves the active Sales view and calls `submit_scan()`. Use an event completion object for the response, not a direct service call. | Small, explicit seam. It isolates HTTP and Qt, enables fake-dispatcher tests, and lets the bridge reject stale context, inactive Sales, closed shifts, and shutdown. | Slightly more plumbing than direct invocation; one event-loop turn. It provides the strongest enforceable separation without a second process. |

**Recommendation:** use **e**, implemented with the queued-signal mechanism in **b**. Keep `infra/mobile_scanner` free of imports from `rawpos.services`, `rawpos.repositories`, and `rawpos.ui`; inject a dispatcher interface at the composition root. The bridge should call the currently active `CheckoutView.submit_scan()`, never simulate keystrokes.

**Required existing-file changes:**

- `src/rawpos/app.py`: compose the receiver and bridge; stop the receiver before detaching audit and closing the database (`app.py:181-267, 329-345`).
- `src/rawpos/ui/app_shell.py`: own lifecycle and resolve the active screen; disarm before recovery/sign-out and remounts (`app_shell.py:75-141, 369-417`).
- `src/rawpos/ui/main_window.py`: expose a safe active-module/current-view accessor rather than making the bridge inspect private widget storage (`main_window.py:213-226`).
- `src/rawpos/ui/sales/checkout_view.py`: add `submit_scan()` and a checkout-context token. Leave `scan()` and wedge handling intact.
- `src/rawpos/services/checkout_service.py`: **approval required** for an exact-barcode resolver that checks open shift, does not call `products.search()`, and uses the existing inactive-product and add/merge behavior.
- `src/rawpos/localization/__init__.py`: add any scanner-specific messages to both Arabic and English catalogs; those catalogs already contain scan messages (`localization/__init__.py:604-605, 1272-1273`).
- Add new receiver/bridge/UI files and tests. Extend the no-network tests deliberately; do not remove their outbound-network denials.

No change is recommended to `repositories/base.py`, `infra/workers.py`, database migrations, or checkout’s keyboard-wedge code.

## 4. Threading and lifecycle model

**Ownership and invariant.** `CheckoutService` and its draft remain owned by the GUI thread. The receiver thread may parse bytes, validate protocol/authentication, update its own bounded dedupe state, and enqueue a command; it may not access `Session`, services, repositories, the database, or widgets. `Database.connect()` uses `check_same_thread=False`, but that is not a synchronization guarantee (`src/rawpos/repositories/base.py:24-50`); the checkout draft is mutable and unsynchronized (`checkout_service.py:253-267`).

> **Invariant: no business call from a non-GUI thread.**

Enforce this structurally and in tests: make the bridge slot assert `QThread.currentThread() == self.thread()`; add a GUI test that records the thread entering `submit_scan()` when a real loopback request is dispatched; add a test that calling the UI entry point from a worker fails; and add an import-architecture test forbidding `rawpos.services`, `rawpos.repositories`, and `rawpos.ui` imports from the receiver package. Do not use the existing generic `Worker` for dispatch: its callbacks run from the thread executing `run_now()` (`src/rawpos/infra/workers.py:49-71`), not automatically on the GUI thread.

The receiver handler posts a command with a deadline and waits a bounded time for completion. If it times out before the GUI slot begins, mark it cancelled so the slot will not later mutate the cart. If the GUI slot has already begun, let its short operation finish and retain the result for same-process retry dedupe.

**Screen and business-state gates:**

- Start the listener only after sign-in and explicit user enablement. Pairing is an owner-authorized action; scanning is admitted only with a signed-in session, full mode, open shift, active Sales screen, and matching checkout-context token.
- MainWindow mounts module widgets lazily and caches them (`main_window.py:213-226`). Sales is currently the first module, but the bridge must not rely on it being mounted or active (`module_registry.py:55`, `main_window.py:139-142`). If Sales is absent or inactive, return `SALES_NOT_ACTIVE`; do not auto-mount or navigate.
- Resolve the view for each command. Language/digit changes remount the entire `MainWindow` (`app_shell.py:369-417`); a stored widget reference can point to a deleted view. Keep the bridge on the shell and resolve through the current `MainWindow` accessor. Rotate the checkout-context token on hold, resume, cancel, finalize, and remount; a queued event with an old token is rejected rather than entering a new invoice.
- The current `CheckoutView.refresh()` checks for an open shift, but current `scan()` itself calls `resolve_scan()` without an explicit shift guard (`checkout_view.py:375-390, 441-457`; `checkout_service.py:523-539`). The new exact resolver must reject before draft mutation when there is no open shift.
- Do not admit scans in Login, Activation, First Run, Recovery, or Safe Failure. Stop/disarm before `ApplicationShell.enter_recovery_mode()` closes the database and clears database-dependent services (`app_shell.py:75-141`; `startup_service.py:167-215`). Backup restore also refuses while connections remain open (`backup_service.py:116-129`).
- In read-only license mode, refuse scans even though the scan itself is in-memory; it should not create a misleading draft that cannot be completed. The existing application exposes full/read-only state to its main window (`main_window.py:292-305`).
- On shutdown, stop accepting connections, resolve/cancel pending replies, join receiver workers with a bounded timeout, then continue audit detach and DB close (`app.py:323-345`). Recovery and shutdown must not depend on the network thread touching the DB.

## 5. State ownership matrix

| State | Owner and lifetime | Survives restart? |
|---|---|---|
| Device registry and desktop TLS identity | Desktop scanner module; recommended separate DPAPI-protected per-user secret store for device-token hashes, device labels, and private key. Do not put secrets in `ConfigStore`, which explicitly holds no secrets (`infra/config.py:1-7`). | Yes, if DPAPI persistence is approved. Failure to decrypt must disable the receiver, not fall back to plaintext. |
| Pairing session | Desktop receiver memory: one-use code, expiry, attempt counter. | No. A code is invalid after use, expiry, or restart. |
| Event dedupe cache | Desktop receiver memory: `(device_id, epoch, event_id)` plus payload digest and final outcome; bounded by age and size. | No. Desktop epoch changes on restart; old pending events are refused, not silently replayed. |
| Cart draft | Existing `CheckoutService` in-memory draft. Remounting a view does not construct a new checkout service; the shell’s Sales factory captures the context service (`app.py:227-240`; `app_shell.py:242-265, 402-417`). | No, consistent with current behavior. Held invoices remain the existing database feature, not scanner state. |
| Connection state | Desktop listener/bridge and Android UI. | No. Reconnect after restart; no business state is inferred from a stale connection indicator. |
| Preferences | Desktop’s existing non-secret config for existing preferences; scanner listener defaults off and is not auto-started. Android may persist language, haptics, and endpoint preferences locally. | Non-secret preferences may persist. The desktop listener remains explicitly enabled per signed-in session. |
| Android pending request | At most one encrypted transport record: barcode, event ID, and the desktop epoch it was created under. | Yes on the phone, to retry the same event in the same epoch. If the epoch changed, show “delivery uncertain”; never rewrite the epoch and auto-replay. |

No scanner table or schema migration is needed. Durable dedupe across desktop restarts would require a durable inbox linked atomically to the cart lifecycle; that conflicts with the existing in-memory draft and should be a separate approved scope.

## 6. Component decomposition

**Desktop modules and allowed dependency direction**

- `rawpos.infra.mobile_scanner.protocol`: strict versioned request/reply parsing, input limits, event IDs, and protocol errors; imports no business modules.
- `rawpos.infra.mobile_scanner.receiver`: TLS listener, pairing/authentication, rate limits, and dedupe; depends only on protocol and an injected dispatcher interface.
- `rawpos.ui.mobile_scanner_bridge`: GUI-thread gate and queued dispatch; resolves the current active Sales view and returns a minimal acknowledgement.
- `CheckoutView.submit_scan`: UI adapter that validates ASCII digits, checks its context token, calls the exact service path, and renders the established scan status. It does not change focus or synthesize keys.
- `CheckoutService.resolve_barcode` (subject to approval): desktop-owned exact lookup and add/merge operation; reuses repository/service rules.
- `ApplicationShell`/`AppContext`: composition and lifecycle only. `app.py` is already the service composition root (`src/rawpos/app.py:127-165`).

Dependency direction remains **UI → services → repositories**; the receiver has no dependency on those layers. RawPOS documents this layering and prohibits SQL/calculation in widgets (`specs/003-cashier-login-shifts/plan.md:83-88`).

**Android layers**

- **Presentation:** one scanner/pairing screen and a small ViewModel state machine: disconnected, paired, armed, sending, acknowledged, rejected, uncertain. Display no product catalogue or cart.
- **Domain:** only transport values and state transitions—`ScanEvent`, `DesktopEpoch`, `CheckoutContext`, and `SendOutcome`. No product, pricing, inventory, or invoice model.
- **Data:** camera/decoder adapter, HTTPS gateway, Android Keystore-backed credential storage, and one pending-event record.

Reject extra interactors/use-case layers, Room, a general repository abstraction, or an Android “POS domain.” They add indirection without adding business capabilities; the phone has one command and no local business database.

## 7. Dependency policy

| Dependency | Purpose | Alternative | Verdict |
|---|---|---|---|
| Python `socket`, `ssl`, `http.server`, `threading`, `json`, `uuid`, `secrets`, `hashlib`, `hmac`, `ipaddress` | LAN TLS receiver and strict protocol | FastAPI/Uvicorn, WebSockets, or third-party server | Use stdlib. Keep a single small POST endpoint and bounded request parsing. |
| Existing `cryptography==49.0.0` | Generate/maintain desktop TLS identity | New crypto package or unauthenticated HTTP | Reuse; it is already a RawPOS runtime dependency (`pyproject.toml:5-14`; bundled status is recorded in `shared-facts.md:153-156`). |
| Windows DPAPI via `ctypes` | Protect desktop private key and paired-device records at rest | Plaintext `config.json`, DB migration, or re-pair every launch | Recommended, subject to approval. No package addition, but it creates Windows-specific code and requires fail-closed tests. |
| Android `HttpsURLConnection`, `AndroidKeyStore`, `org.json` | One small pinned-TLS request path and credential storage | OkHttp/Retrofit plus serializer | Start with platform APIs; one endpoint does not justify another HTTP stack. Do not disable hostname/certificate checks. Reconsider if the Networking agent finds pinning cannot be implemented and tested safely with the platform trust store. |
| Android camera and offline decoder | Read product barcodes locally | Camera2 plus custom decoder, or cloud decoding | Allow CameraX and one bundled on-device decoder after Barcode-agent confirmation. Do not use a Play-Services/cloud-dependent runtime decoder. |
| QR generator such as Segno | Encode endpoint, pairing code, and certificate identity | Manual host/code entry plus fingerprint comparison | Defer. The no-dependency v1 can show manual pairing information; add a pinned QR library only if UI/UX concludes manual pairing is unacceptable. |
| Flask/FastAPI/Requests/`zeroconf`/database client on desktop; Room/Retrofit on Android | Convenience, discovery, or local storage | Small explicit protocol and platform APIs | Forbid in v1: each broadens the attack surface or creates unnecessary business-state copies. |

## 8. Error and failure domains

| Failure | Detected by | Desktop user sees | Android user sees | Can business state be corrupted? |
|---|---|---|---|---|
| Receiver down | Android connection failure; desktop listener health | Scanner control shows stopped/failed | “Desktop unavailable” | No request reaches checkout. |
| Phone offline | Android network status; desktop heartbeat/last-seen timeout | Disconnected indicator; no scan event | “Reconnect to RawPOS” | No. No scan is accepted offline. |
| TLS failure or pin mismatch | Android TLS validation; receiver handshake log | Security/connection status, no credentials or barcode logged | “Secure connection failed; verify desktop identity” | No request reaches business code. |
| Authentication failure | Receiver token validation and rate limiter | Owner-facing security notice after repeated failures | “Device not paired—re-pair” | No. No scan is dispatched. |
| Duplicate event ID | Receiver dedupe cache | No second cart mutation | Original acknowledgement / “already delivered” | No duplicate from transport retry. A new event ID for a deliberate repeated scan still increments quantity. |
| Unknown barcode | Desktop exact lookup | Existing not-found status and beep behavior (`checkout_view.py:441-457`) | Generic “not found; check desktop” | No cart mutation. |
| Inactive product | Desktop resolver | Existing inactive-product message | “Cannot add; check desktop” | No cart mutation. |
| No open shift | Bridge and exact service resolver | Shift-required prompt | “Open a shift on desktop” | No mutation; resolver must reject before draft change. |
| Sales inactive or absent | GUI bridge’s live-view resolver | Scanner panel says Sales must be active | “Open Sales on desktop” | No mutation; no auto-navigation. |
| Read-only license mode | GUI bridge checks full-mode state | Existing read-only banner plus scanner unavailable | “Scanner unavailable; desktop is read-only” | No draft mutation. |
| DB closed during restore | Shell disarms before recovery; bridge checks lifecycle generation | Recovery screen; receiver stopped | Connection unavailable | No scan path uses the closed DB. Receiver has no DB dependency. |
| App shutdown mid-request | Receiver shutdown/cancellation and GUI completion | Normal shutdown; pending event cancelled or completed before close | Timeout/disconnect; if uncertain, inspect desktop | A started scan can mutate only the in-memory draft; no partial sale write. |
| Phone killed mid-request | Server completion plus Android pending-event record | May see the scan result on desktop | On restart, retry the same event only in the same epoch; otherwise show “delivery uncertain” | At most one mutation per event in one epoch. An unacknowledged event is never silently replayed after desktop restart. |

A scan itself must not finalize or persist a sale. The remaining business risk is an operator finalizing an unintended quantity after a duplicate physical scan or manually retrying an uncertain event without checking the cart.

## 9. Architectural risks

| Risk | Likelihood / impact | Mitigation | Owner |
|---|---|---|---|
| Mobile becomes a second POS by accident | Medium / High | Keep Android payload/result limited to scan transport; add no product, price, cart, or payment endpoints; enforce imports and API-surface tests. | Architect + Android |
| Mobile reuses name-search fallback | High / High | Require an approved exact-barcode service method; test numeric barcode absent but same digits present in a product name. | Desktop |
| Duplicate quantity from retries or camera frames | High / High | Stable event UUID, same-epoch dedupe, one pending event, camera re-arm/debounce only until code leaves frame; never dedupe solely by barcode because repeated scans are meaningful. | Networking + Barcode + QA |
| Keyboard-wedge regression | Medium / High | Keep `scan()` and `eventFilter` unchanged; mobile uses a separate entry point; preserve all existing wedge/focus/tender tests (`tests/gui/test_checkout_ui.py:428-525`). | Desktop UI + QA |
| Network guarantee tests weakened | Medium / High | Keep outbound socket denials; add a separate explicit inbound-listener test fixture and assert no outbound DNS/Internet use. Existing tests patch network calls (`tests/failure/test_no_network.py:18-27`; `tests/integration/test_no_network.py:29-35`). | QA + Networking |
| Pairing bypass or token theft | Medium / High | Owner-only one-use pairing code, short expiry/attempt cap, pinned TLS identity, unique device token, DPAPI/Android Keystore storage, immediate revoke, no plaintext fallback. | Networking + Security |
| Shell remount leaves a stale bridge/view | Medium / High | Bridge belongs to shell, resolves the active view per event, and rejects stale checkout-context tokens. Test language/digit remount with a pending request (`app_shell.py:402-417`). | Desktop UI |
| Restore races an in-flight scan | Medium / High | Disarm first, cancel queued requests, then enter recovery/close DB; bridge checks generation; test a queued request against restore (`app_shell.py:75-141`; `startup_service.py:167-215`). | Desktop + QA |
| Late scan lands in the next invoice | Medium / High | Rotate an opaque checkout-context token on invoice lifecycle changes and remounts. Old-token requests fail closed; Android must refresh context and must not auto-retry into a new context. | Desktop UI + Android |
| Persistent-secret or listener exposure | Medium / High | Bind only to selected private interface, explicit enablement, strict body/concurrency limits, DPAPI-protected store, and fail closed on vault errors. Test packaged Windows behavior. | Networking + Desktop |

## 10. Alternatives considered and rejected

- **WebSocket-first:** bidirectional updates are unnecessary for one-shot scans and acknowledgements. WebSockets add reconnect, heartbeat, backpressure, and long-lived credential handling. Revisit only if real-time desktop-to-phone updates become a requirement.
- **mDNS discovery:** convenient but exposes a broadcast discovery surface and introduces multicast/firewall/spoofing behavior. Manual endpoint setup or an approved pairing QR is sufficient for v1.
- **Cloud relay:** violates offline/local-only operation and sends shop data outside the store.
- **Desktop-as-client / phone-as-server:** Android background lifecycle and LAN reachability are unreliable; the desktop is the natural local authority and listener.
- **Phone as HID keyboard:** loses device identity and event idempotency and depends on focus/wedge behavior. It can interact with quantity or tender controls; existing wedge tests show those are sensitive (`tests/gui/test_checkout_ui.py:428-525`).
- **Shared database:** bypasses RawPOS services, couples Android to SQLCipher and schema, and risks data corruption. It directly contradicts the desktop-authority boundary.
- **Embedding an HTTP server in the Qt event loop:** slow or malicious clients could block UI input. A separate receiver thread plus queued bridge isolates network stalls.
- **Synthetic key events:** rejected for the same focus, payment, and wedge reasons described in §3.

## 11. ADRs to record

1. **Desktop-only business authority:** Android sends raw scan events; only RawPOS resolves and applies them.
2. **Explicit LAN HTTPS receiver:** inbound-only, disabled by default, no cloud, discovery, or outbound network.
3. **Network/GUI seam:** receiver has no business imports; commands cross through a queued GUI-thread bridge.
4. **Exact mobile barcode semantics:** mobile lookup must not fall back to product-name search; an additive service method requires approval.
5. **Admission gate:** signed-in + full mode + open shift + active Sales + current checkout context.
6. **Process-epoch idempotency:** stable event IDs dedupe same-process retries; old-epoch requests are not automatically replayed.
7. **No scanner DB schema:** existing cart draft remains in memory; persistent device secrets use an approved OS-protected store, not config or DB.
8. **Dependency policy:** stdlib/current desktop dependencies and Android platform networking; no general web framework or cloud decoder.
9. **Network-test contract:** preserve “no outbound Internet”; test the explicitly enabled inbound LAN receiver separately.

## 12. Dependencies on other agents

- **Android:** agree on event ID/epoch/context fields, acknowledgement states, secure token storage, no offline scan queue, and recovery from an uncertain event.
- **Barcode:** confirm supported symbologies, leading-zero preservation, valid payload rules, decoder dependency and offline behavior, and camera duplicate/re-arm semantics.
- **Networking:** settle TLS identity/pinning, bind interface and port, pairing handshake, request limits, rate limiting, and inbound-only test strategy.
- **UI/UX:** decide scanner enable/pair/revoke surfaces; make active Sales/shift requirements clear; design Arabic/English RTL messages and whether remote scans should preserve cashier focus.
- **QA:** preserve current wedge tests; cover exact-vs-name lookup, Qt-thread enforcement, duplicate requests, stale checkout context, TLS/auth failure, no-network invariants, remount, restore, and shutdown.
- **Independently confident:** the current checkout service is mutable and not a network-thread API; `resolve_scan()` is not exact-only; the shell remounts views; and a GUI-thread bridge is the smallest safe seam. These are evidenced in the cited source above.

## 13. Open questions requiring user approval

1. **Approve a narrow `CheckoutService.resolve_barcode()` addition** with exact lookup and an open-shift check? Without this, mobile scanning should not ship; reusing `resolve_scan()` is unsafe.
2. **Approve persistent paired-device identity using a DPAPI-protected desktop store** and Android Keystore, with no DB migration? Alternative: RAM-only credentials and re-pair every desktop launch.
3. **Is manual host + one-time code + certificate-fingerprint comparison acceptable**, or should v1 add a QR-generation dependency for pairing?
4. **Is “delivery uncertain; inspect the desktop before rescan” acceptable after a desktop restart?** Automatic exactly-once recovery across restarts would need a durable inbox coupled to cart state and likely a schema change.
5. **Should v1 support multiple paired phones, or one active scanner at a time?** The safer/simpler default is one active scanner, with owner-controlled replacement.
6. **Approve the active-Sales-only gate and per-checkout context token?** The trade-off is rejecting scans made during navigation or immediately after a sale rather than risking an invisible or next-invoice mutation.

## 14. Verified vs assumed

| Claim | Status and evidence |
|---|---|
| RawPOS is Python 3.12+, PyQt6, SQLCipher, and already declares `cryptography`. | Verified: `pyproject.toml:5-14`. |
| `app.py` is the composition root and owns `AppContext` and shutdown ordering. | Verified: `src/rawpos/app.py:78-104, 127-165, 284-321, 323-345`. |
| Checkout owns a mutable in-memory draft; `resolve_scan()` falls back to general search and can mutate it. | Verified: `src/rawpos/services/checkout_service.py:253-267, 441-467, 523-539`. |
| Product barcodes are ASCII digits; general search also matches product names. | Verified: `src/rawpos/migrations/versions/0004_products_inventory.py:40-48`, `src/rawpos/services/product_service.py:59-64`, `src/rawpos/repositories/product_repository.py:207-217`. |
| Checkout’s scan entry currently reads the UI field; wedge behavior spans the application and protects quantity/tender focus. | Verified: `src/rawpos/ui/sales/checkout_view.py:286-372, 441-457`; `tests/gui/test_checkout_ui.py:428-525`. |
| Sales widgets are lazy-mounted; language/digit changes remount the main window. | Verified: `src/rawpos/ui/main_window.py:213-226`; `src/rawpos/ui/app_shell.py:369-417`. |
| Recovery closes the database and nulls DB-dependent services; restore requires no open connection. | Verified: `src/rawpos/ui/app_shell.py:75-141`, `src/rawpos/services/startup_service.py:167-215`, `src/rawpos/services/backup_service.py:116-129`. |
| Existing tests enforce no outbound network calls. | Verified: `tests/failure/test_no_network.py:18-27`; `tests/integration/test_no_network.py:29-35`. |
| Config is intentionally non-secret. | Verified: `src/rawpos/infra/config.py:1-7`. |
| Android code, Android build configuration, and a network harness do not currently exist in the supplied project facts. | Verified in the fact pack: `shared-facts.md:168`. |
| Android Kotlin/CameraX, HTTPS endpoint shape, pairing workflow, DPAPI persistence, and event/context semantics. | **Proposal/assumptions**, not existing-repository facts. Validate with Android, Barcode, Networking, and UI/UX agents before implementation. |