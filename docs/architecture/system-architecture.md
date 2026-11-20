# System Architecture — RawPOS Mobile Scanner v1.0

**Status:** proposed — pending owner approval
**Scope:** Android scanner application + desktop receiver/integration in RawPOS Desktop
**Related:** `repository-audit.md`, `desktop-integration.md`, `networking-protocol.md`,
`security-design.md`, `barcode-scanning.md`, `../planning/risks-and-decisions.md`

---

## 1. Purpose

Add a wireless camera scanner to RawPOS without turning the phone into a second POS and without
destabilising the checkout that already works.

The architecture is deliberately asymmetric:

- the **phone** is a sensor with a network client, credentials, and preferences;
- the **desktop** is the authority, the server, and the only place a cart exists;
- the **protocol** is a small, versioned, authenticated, idempotent request/response contract.

Everything else follows from those three sentences.

---

## 2. Quality attributes, in priority order

| Priority | Attribute | What it means here |
|---|---|---|
| 1 | **Business integrity** | One scan never creates two cart lines; no non-digit payload can reach name search; no business rule is duplicated |
| 2 | **Non-regression** | USB scanner, keyboard checkout, focus, localization, money, discounts, stock, shifts, invoices unchanged |
| 3 | **Security** | No unauthenticated submission, no internet exposure, replay-resistant, revocable, no custom crypto |
| 4 | **Truthfulness** | The phone never says "added" before the desktop confirms the cart mutation |
| 5 | **Latency** | Scan-to-cart p95 ≤ 800 ms on a normal shop LAN |
| 6 | **Operability** | A shop owner can pair, diagnose, and revoke without a developer or a cloud account |
| 7 | **Simplicity** | Stdlib-first desktop, one Android app, no broker, no database schema change |

When these conflict, the lower number wins. For example, a faster optimistic acknowledgment loses to
truthfulness; a richer phone UI loses to business integrity.

---

## 3. System context

```
        ┌──────────────────────────────┐
        │        Shop LAN (private)     │
        │                              │
        │  ┌────────────┐   TLS   ┌────┴─────────────┐
        │  │ Android    │◄───────►│ RawPOS Desktop   │
        │  │ scanner    │  HTTPS  │ (Windows, PyQt6) │
        │  └─────┬──────┘         └────┬─────────────┘
        │        │ camera               │
        │  ┌─────▼──────┐         ┌────▼─────────────┐
        │  │ Barcode    │         │ Existing checkout │
        │  │ on product │         │ cart + services   │
        │  └────────────┘         └────┬─────────────┘
        │                               │ SQLCipher
        │                          ┌────▼─────────────┐
        │                          │ rawpos.db (local) │
        │                          └──────────────────┘
        └──────────────────────────────┘
                    ✕ no internet, no cloud
```

External entities: the cashier (both screens), the shop owner (pairing/revocation), the product label
(the only source of truth for a barcode). There is no server outside the shop.

### 3.1 What the phone knows

- its own device identity and token;
- the desktop's host, port, certificate pin, and display name;
- the user's preferences (sound, vibration, torch, formats);
- the last barcode it scanned (in memory by default; optional short history, off by default).

It does **not** know products, names, prices, stock, customers, sales, totals, invoices, or shifts.

### 3.2 What the desktop knows

- all business state (unchanged);
- the device registry (device id, display name, token hash, created/last-seen, revoked);
- the one-time pairing session (memory only);
- the event dedupe cache (memory + periodic snapshot);
- the receiver's enabled/disabled state and bound interface.

---

## 4. Containers and components

### 4.1 Android application

```
┌─────────────────────────── app (single Activity, Compose) ──────────────────────────┐
│  ConnectionScreen     ScannerScreen     SettingsScreen     DiagnosticsScreen        │
│        │                   │                  │                  │                 │
│  ConnectionViewModel  ScannerViewModel  SettingsViewModel   DiagnosticsViewModel    │
│        │                   │                  │                  │                 │
│  ┌─────▼───────────────────▼──────────────────▼──────────────────▼──────────────┐  │
│  │ Domain: ScanEvent, ScanStatus, BarcodeFormat, ValidationResult, ScanGate      │  │
│  │         (pure Kotlin, JVM-testable, no Android imports)                       │  │
│  └─────┬───────────────────────┬───────────────────────┬────────────────────────┘  │
│        │                       │                       │                           │
│  ┌─────▼────────┐      ┌───────▼────────┐      ┌───────▼─────────┐                 │
│  │ Camera       │      │ Transport      │      │ Secure storage  │                 │
│  │ CameraX +    │      │ OkHttp + TLS   │      │ Keystore +      │                 │
│  │ ML Kit       │      │ pinning + retry│      │ DataStore       │                 │
│  └──────────────┘      └────────────────┘      └─────────────────┘                 │
└────────────────────────────────────────────────────────────────────────────────────┘
```

Rules:

- The domain layer has no Android dependencies; validator, dedupe state machine, and status mapping are
  JVM unit-tested.
- The camera layer emits **candidate** barcodes; it does not decide delivery.
- The transport layer owns retries and idempotent event ids; it does not decide scan success.
- The UI renders a status; it does not compute it.

### 4.2 Desktop receiver and bridge

```
┌──────────────────────────── RawPOS process ─────────────────────────────┐
│                                                                         │
│  ┌─────────────── GUI thread ───────────────┐   ┌──── receiver thread ──┐│
│  │ ApplicationShell                        │   │ MobileScanServer      ││
│  │  └─ MainWindow                          │   │  HTTP/TLS, routes,    ││
│  │      └─ Sales → CheckoutView            │   │  auth, rate limit     ││
│  │            ▲                            │   └──────────┬────────────┘│
│  │            │ submit_scan()                          │ queued signal  │
│  │  MobileScanController (QObject) ◄───────────────────┘                │
│  │   - dedupe, device auth, pairing, view provider                      │
│  │   - protection.begin/end_critical_operation                          │
│  └────────────┬────────────────────────────────────────────────────────┘│
│               │ existing calls, GUI thread                               │
│  ┌────────────▼────────────────────────────────────────────────────────┐│
│  │ CheckoutService (unchanged) → repositories → SQLCipher             ││
│  └─────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────┘
```

The receiver is a **transport and admission-control** component. It has no business logic and no SQL.
The controller is the **thread boundary and policy** component. It owns pairing/dedupe state and the
only call into the checkout view. `CheckoutService` and every repository remain GUI-thread-only.

### 4.3 Dependency direction

```
ui/mobile_scanner_dialog ──► mobile/controller ──► mobile/pairing, devices, protocol
                                     │
                                     └──────────► ui/sales/checkout_view ──► services/checkout_service
mobile/server ──► mobile/protocol, pairing, devices, tls
```

- `mobile/protocol`, `pairing`, `devices`, `tls`, and `server` must not import PyQt6.
- `controller` is the only mobile module that imports Qt and the only one that touches the view.
- Nothing in `mobile/` imports `repositories` or `domain.pricing`.

---

## 5. Trust zones

| Zone | Contents | Trust rule |
|---|---|---|
| Z0 — Phone app sandbox | token, prefs, camera frames | Assume the device can be lost or compromised; token is revocable and scan-only |
| Z1 — Shop LAN | TLS traffic | Untrusted network: every request authenticated, every connection pinned |
| Z2 — Desktop receiver | HTTP handler, device registry | Authenticates, validates, rate-limits; cannot mutate the cart |
| Z3 — Desktop GUI/checkout | controller, view, services | Trusted process boundary; GUI thread only |
| Z4 — Database | SQLCipher | Existing protection unchanged; receiver has no direct access |
| Z5 — Internet | — | Not used; outbound is forbidden by design and by test |

The critical boundary is **Z2 → Z3**: a network request becomes a checkout action only through the
controller's explicit, audited dispatch.

---

## 6. Core flows

### 6.1 Pairing

```
Owner opens Mobile Scanner on desktop
  → controller creates pairing session (id, 256-bit secret, 120 s TTL, one use)
  → desktop renders QR: rawpos://pair?v=1&host=…&port=…&sid=…&secret=…&pin=…&name=…
Cashier opens app → Connection → Scan QR
  → phone parses QR, connects over TLS, verifies the certificate pin
  → POST /v1/pair {sid, secret, device metadata}
  → desktop checks session (exists, unexpired, unused), binds device id + token hash
  → response {device_id, token, desktop_name}
  → phone stores token in Keystore-backed storage; desktop stores only the hash
  → session is consumed; QR can no longer pair
```

Failure cases: expired session → re-open dialog; used session → re-open; wrong secret → 401 and the
session is not consumed (or is consumed after N failures — see security design); pin mismatch → abort
before sending the secret.

### 6.2 Scan

```
phone camera → ML Kit → validator (digits/length) → dedupe gate → event_id
  → POST /v1/scans (TLS, bearer token)
  → receiver: auth → rate limit → validate → idempotency check
  → controller (GUI thread): admission check (Sales active, full mode, open shift, context)
  → protection guard → live CheckoutView → submit_scan
  → CheckoutView → CheckoutService.resolve_barcode (exact-only)
  → Matched/NotFound/Deactivated → status
  → response → phone shows truthful state
```

Latency budget (proposed): camera+ML Kit p95 ≤ 300 ms, network p95 ≤ 150 ms, desktop p95 ≤ 100 ms,
total p95 ≤ 800 ms (p50 ≤ 400 ms). See `../quality/performance-requirements.md`.

### 6.3 Reconnect

The phone keeps its token and pin. On resume or network change it calls the authenticated health
endpoint. If reachable, scanning resumes. If the certificate changed, it fails closed and requires
re-pairing. If the token was revoked, it clears credentials and returns to Connection.

The desktop does not need to be restarted for a phone to reconnect. The receiver keeps running while
enabled, independent of which module is open; only the *dispatch* depends on the Sales screen.

---

## 7. State ownership

| State | Owner | Lifetime | Persisted? |
|---|---|---|---|
| Cart draft, discounts, held invoice | `CheckoutService` (GUI thread) | Until finalize/hold/cancel or app exit | Only held invoices, as today |
| Device registry (id, name, token hash, last seen, revoked) | `MobileScanController` / `devices.json` | Until revoked | Yes, outside `rawpos.db` |
| Pairing session (id, secret, TTL) | `MobileScanController` | ≤ 120 s, one use | No |
| Event dedupe cache | `MobileScanController` | 24 h (bounded) | Periodic snapshot, optional |
| Receiver enabled/bind/port | Desktop config | Until changed | Yes (`config.json`) |
| TLS private key + certificate | `tls.py` | Until rotation | Yes, app data, restrictive ACL |
| Phone token | Android Keystore | Until revoke/reinstall | Yes, encrypted |
| Phone preferences | DataStore | Until changed | Yes |
| Last barcode on phone | Scanner ViewModel | Session | No (default) |

No business state is duplicated on the phone. No transport state enters the business database.

---

## 8. Threading model

| Thread | May touch | Must never touch |
|---|---|---|
| GUI (Qt main) | widgets, services, repositories, controller, cart | blocking network I/O |
| Receiver | sockets, TLS, JSON, token hashes, dedupe, pairing session | widgets, services, repositories, cart |
| Android main | Compose state, ViewModels | camera analysis, network I/O |
| Android analysis | ML Kit, frame processing, candidate emission | UI state mutation off-main (use StateFlow/confined dispatcher) |
| Android IO | OkHttp, DataStore, Keystore | UI |

The desktop bridge is the only crossing, and it is a queued Qt signal with a bounded wait. A test must
prove that calling the controller's dispatch slot from a worker thread does not mutate the cart.

---

## 9. Failure model

| Failure | Detected by | Phone sees | Desktop state |
|---|---|---|---|
| Phone offline | client | "no connection" | unchanged |
| Receiver off | client | connect failure | unchanged |
| TLS/pin mismatch | client | "desktop identity changed; re-pair" | unchanged |
| Bad/revoked token | server | 401, clear credentials | unchanged |
| Malformed/oversized request | server | 400/413 | unchanged |
| Rate limited | server | 429 + retry-after | unchanged |
| Sales screen absent | controller | `rejected_no_checkout` | unchanged |
| No open shift | controller | `rejected_no_shift` | unchanged |
| Read-only license | controller | `rejected_read_only` | unchanged |
| Modal dialog open | controller | `rejected_busy` | unchanged |
| DB closed/restoring | controller | `rejected_unavailable` | safe; no dispatch |
| Unknown barcode | `CheckoutService` | `not_found` | cart unchanged |
| Inactive product | `CheckoutService` | `inactive` | cart unchanged |
| Duplicate event | controller | original outcome + `duplicate` | cart unchanged |
| Desktop crash after add | — | timeout/unknown; retry after restart adds once to a new cart | cart lost with process (as today) |
| Phone killed mid-request | client timeout | — | request either completed or not; retry is idempotent |

The system never compensates a business action by guessing. If delivery is uncertain, it says so.

---

## 10. Deployment topology

- One Windows PC per till, running RawPOS as today.
- One Android phone per till (recommended), paired to that PC.
- The receiver binds to the PC's private LAN address on a configurable port (default 47653).
- Windows Firewall: inbound rule for that port on the **private** profile only, created by the shop
  owner from documented instructions (no silent elevation).
- No inbound NAT, no port forwarding, no public DNS, no cloud.
- The phone and PC must be on the same L2/L3 private network; guest Wi-Fi with client isolation is
  unsupported and detected as a connection failure.

---

## 11. Key decisions

| ID | Decision | See |
|---|---|---|
| D1 | HTTP/1.1 + JSON over TLS, not WebSocket | `networking-protocol.md` §3 |
| D2 | Desktop server, phone client | above |
| D3 | QR bootstrap with one-time secret + certificate pin | `security-design.md` §5 |
| D4 | Self-signed local certificate, SPKI pin | `security-design.md` §6 |
| D5 | 256-bit device token, hash-at-rest on desktop, Keystore on phone | `security-design.md` §7 |
| D6 | Receiver off by default, LAN-only | `security-design.md` §10 |
| D7 | Queued Qt signal; no service call from network thread | `desktop-integration.md` §6 |
| D8 | Additive `CheckoutView.submit_scan()` | `desktop-integration.md` §5 |
| D9 | Digit-only validation before dispatch | `barcode-scanning.md` §4 |
| D10 | Three-layer dedupe, desktop authoritative | `barcode-scanning.md` §6 |
| D11 | No schema change; registry outside `rawpos.db` | `desktop-integration.md` §4 |
| D12 | Truthful status ladder | `networking-protocol.md` §4 |
| D13 | Mobile admission policy: active Sales + full mode + open shift | `desktop-integration.md` §8 |

---

## 12. Non-goals for v1

- Product information or prices on the phone.
- Offline queueing of scans for later submission.
- Multi-store, multi-tenant, cloud relay, remote support.
- iOS, Bluetooth HID, USB tethering, NFC.
- Windows service or headless receiver.
- Multiple active scanners on one checkout.
- Phone-initiated finalize, hold, discount, or return.
- Barcode generation or label printing.
- Migrating the device registry into SQLCipher.

Each is a deliberate exclusion, not an oversight. Reintroducing one requires a new phase and an
amendment to the constitution's scope rules.

---

## 13. Evolution path (only if justified later)

1. **WebSocket / server push** — if the desktop needs to notify the phone about cart changes, or if
   multiple scanners must coordinate. The protocol version field and status model are designed to allow
   this without changing the event schema.
2. **Multiple active scanners** — add a desktop-side "active device" selector and per-device ordering;
   the dedupe key already includes `device_id`.
3. **Product name feedback** — explicitly leaks business data; requires a product decision and a
   privacy review, not a technical convenience.
4. **Bluetooth HID fallback** — would reuse the existing wedge path but lose the truthful status ladder;
   a separate mode, never the default.
5. **mDNS discovery** — removes the host/port from the QR but adds a responder dependency and firewall
   surface; not worth it for one desktop per shop.
