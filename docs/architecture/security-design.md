# Security Design

**Status:** proposed — several items require explicit approval (see §14)
**Scope:** pairing, transport, device identity, authorization, replay, exposure, secret storage
**Related:** `networking-protocol.md`, `desktop-integration.md`, `../planning/risks-and-decisions.md`

---

## 1. Security objectives

1. **Only a paired phone can submit a scan.** No anonymous barcode endpoint exists.
2. **Only the intended desktop is trusted.** The phone authenticates the desktop's key, not just its IP.
3. **A stolen phone can be revoked immediately** and cannot read business data.
4. **A replayed or delayed scan cannot add a second unit or land in the wrong invoice.**
5. **The receiver is not reachable from the internet** and is off by default.
6. **No custom cryptography.** Only vetted primitives and platform TLS.
7. **The shop can diagnose and recover without a cloud account.**

Non-goals: protecting against a compromised Windows administrator, a rooted phone with an unlocked
Keystore, physical coercion, or a malicious shop owner. Those are out of scope and stated as residual
risk.

---

## 2. Assets

| Asset | Sensitivity | Where it lives |
|---|---|---|
| Barcode stream | Medium (purchase intent, product mix) | In transit; phone memory; desktop memory |
| Cart / invoice / prices | High (business data) | Desktop only |
| Device token | High (scan capability) | Phone Keystore; desktop hash |
| Pairing secret | High (one-time pairing capability) | QR; desktop memory; phone memory |
| TLS private key | High (desktop identity) | Desktop protected storage |
| Device registry | Medium (names, hashes, timestamps) | Desktop file |
| Audit log | High (integrity) | Existing SQLCipher DB |
| Receiver availability | Medium | Desktop process |

The phone never holds a product, price, cart, invoice, customer, or stock record.

---

## 3. Trust boundaries

```
Z0 Phone sandbox ── untrusted device, trusted code after pairing
   │  TLS 1.2+ with pinned desktop key
Z1 Shop LAN ── untrusted network
   │  authenticated HTTP
Z2 Receiver ── untrusted input parser; no business access
   │  queued command
Z3 GUI/checkout ── trusted process; GUI thread only
   │  existing service calls
Z4 SQLCipher ── existing protection
```

The security-critical transition is **Z2 → Z3**. The receiver can only produce a validated
`ScanCommand`; the controller decides whether it may reach checkout.

---

## 4. Threat model

Likelihood/impact are relative to a small retail LAN. "Residual" is what remains after mitigation.

| # | Threat | L / I | Mitigation | Residual |
|---|---|---|---|---|
| T1 | Unauthenticated scan submission | M / H | Bearer token on every scan; no anonymous scan route; strict validation | LAN noise/DoS still possible |
| T2 | Replay of a captured request | M / H | TLS; UUID event id; reserve-before-dispatch; cached outcome; server instance id; checkout context | A replay after the 24 h dedupe TTL, in the same invoice, is theoretically possible; the attacker needs a valid token |
| T3 | Token theft from phone | L–M / H | Android Keystore AES-GCM; no backup; scan-only scope; local revoke; short-lived detection via last-seen | A stolen unlocked phone can scan until revoked |
| T4 | Token theft from desktop | L / H | Store only a domain-separated SHA-256 verifier; DPAPI/ACL; never log | Desktop admin can read memory |
| T5 | MITM / rogue AP | M / H | TLS 1.2+; SPKI pin from QR; no trust-anyway; no cleartext fallback | User scans an attacker's QR (T6) |
| T6 | QR shoulder-surf / race | M / M | 120 s TTL; one attempt; desktop approval; device name + 6-digit code; one active device | Observer can race before the legitimate phone; approval is the control |
| T7 | Spoofed desktop | L–M / H | Pin + desktop id + name; verification code; fail closed on mismatch | Social engineering remains |
| T8 | Malicious paired phone | M / H | Digit-only exact barcode; active checkout + context; rate limit; revoke; no data endpoints | Can add matched products to the open cart |
| T9 | DoS flood | M / M | Bounded workers/connections; token buckets; timeouts; private firewall; supervised thread | LAN bandwidth/CPU can still be consumed |
| T10 | Malformed JSON / parser abuse | M / M | Strict allowlist; duplicate-key rejection; size/depth limits; fixed routes | Parser/runtime defects; fuzz before release |
| T11 | Path traversal / file read | L / H | No file routes, no static serving, no user-supplied paths | Implementation mistake |
| T12 | SSRF | L / H | No client-supplied URLs; no server-side fetch or DNS | Future endpoints |
| T13 | Log leakage | M / M | Never log tokens, secrets, barcodes, bodies; redaction by field name; log review test | Timing/device metadata remains |
| T14 | Phone theft / loss | M / H | Device lock; Keystore; revoke; no business data on phone | Unlocked device with a live token |
| T15 | Desktop compromise | L / C | OS protections; least privilege; private-only listener; no cloud | Full access to DB/checkout; out of scope |
| T16 | Insider misuse | M / H | Local pairing approval; device labels; event logs; cart visible to cashier; revoke | A paired staff device can scan |
| T17 | Downgrade to HTTP | L / H | No HTTP listener; cleartext denied in Android network security config; no fallback | — |
| T18 | Pairing brute force | L / M | 256-bit secret; 120 s; one attempt; 3/min; constant-time compare; approval | — |
| T19 | Device registry tampering | L / H | Atomic writes; strict schema; fail closed on parse/decrypt error; DPAPI integrity | Local admin can rewrite |
| T20 | Stale scan into next invoice | M / H | Checkout context token; reject on mismatch; no auto-apply | One lost scan after context change |
| T21 | Wrong product via name search | H / H | Exact-barcode-only service path; **not** `resolve_scan()`; test with a numeric name collision | If the service change is refused, do not ship |
| T22 | Certificate expiry / clock skew | L / M | 5-year cert; notBefore −1 day; Android auto-time; clear error; reissue | A phone with a badly wrong clock fails closed |

T21 is the highest-severity product-integrity issue and is not a network attack. It is included here
because the mobile path is the new attack surface that makes it reachable at scale.

---

## 5. Pairing security

### 5.1 Bootstrap

The QR is the trust anchor. It contains:

- endpoint (host, port);
- desktop install id and display name;
- **SPKI SHA-256 pin** of the TLS certificate;
- 256-bit one-time pairing id and secret;
- expiry (120 s).

It contains no token. The phone must verify the pin **before** sending the secret. A pin mismatch aborts
with no credential exposure.

### 5.2 Window

- Opened only by an explicit desktop action in a signed-in, full-mode session.
- 120 s TTL; one successful use; closed on dialog close, cancel, approval, or app shutdown.
- Only one attempt may be pending.
- 3 attempts/min per IP; 256-bit secret makes guessing infeasible.
- The secret is compared in constant time.
- The QR is rendered locally; no cloud QR service.

### 5.3 Approval and verification code

The desktop shows the requesting device name and a six-digit code derived from
`HMAC-SHA-256(pairing_secret, pairing_id | device_id | client_nonce)`. The phone shows the same code.
The cashier compares and approves. This defends against a QR photographed by a bystander: the
bystander's device name/code would be visible, and the cashier can deny it.

The code is a **human comparison aid**, not an authentication factor. The secret and TLS pin do the
cryptographic work.

### 5.4 Credentials issued

- Desktop generates a 256-bit token (`rps1_` + base64url).
- Token is returned once over pinned TLS.
- Desktop stores `SHA-256("rawpos-device-v1" || token_bytes)` only.
- Phone stores the token in Keystore-backed storage with cloud backup disabled.
- Token scope is scan submission + context read. Nothing else.

### 5.5 Replacement and revocation

- One active device in v1. Pairing another requires **Replace current device** and immediately revokes
  the old token.
- Revocation is local and immediate; the next request gets `403 device_revoked`.
- The phone clears its credentials and returns to Connection.
- A lost phone is revoked on the desktop; no network round trip is required.

### 5.6 What pairing does not prove

It does not prove a human identity or that the phone is in the cashier's hand. It binds a device to a
desktop with an explicit local approval. Device-level accountability is the audit trail plus the
revocation control.

---

## 6. TLS design

### 6.1 Certificate

- ECDSA P-256 key, generated with `cryptography`.
- Self-signed X.509, `BasicConstraints(ca=False)`, `KeyUsage(digital_signature)`,
  `ExtendedKeyUsage(serverAuth)`.
- SANs: every current private IPv4/IPv6 address, the Windows hostname, and `localhost` (test only).
- Validity: 5 years; `notBefore` one day in the past for clock tolerance.
- Random 128-bit serial.
- TLS 1.2 minimum; TLS 1.3 preferred; no client certificates; no renegotiation; no compression.

### 6.2 Pin

- Pin = SHA-256 of the DER-encoded SubjectPublicKeyInfo, base64url.
- Delivered in the QR; stored on the phone with the pairing.
- Android uses a **pin-only trust manager**: `checkServerTrusted` accepts the leaf only when its SPKI
  SHA-256 equals the stored pin, and it checks certificate validity. This is **not** a trust-all
  manager; it trusts exactly one key. Hostname verification remains enabled and checks the SAN.
- `OkHttp`'s `CertificatePinner` may be added as a second, independent check on the same pin. It is
  not sufficient on its own, because pinning does not make a self-signed certificate trusted.
- A one-certificate `KeyStore` is not used as the anchor: same-key reissue (IP change) changes the
  certificate DER, and the phone would reject it even though the key and pin are unchanged.
- Never disable hostname verification, install a trust-all manager, or add a "continue anyway" button.
- The certificate SAN must match the host the phone connects to; on IP change, reissue the certificate
  with the new SAN using the **same key**, so the pin remains valid.

### 6.3 Rotation and failure

| Event | Behavior |
|---|---|
| IP/hostname change | Reissue cert with new SAN, same key; pin stable; user updates endpoint (or a new QR carries the endpoint; no secret needed) |
| Certificate expiry | Reissue with same key before expiry; pin stable |
| Key rotation | New pin; every phone must re-pair; fail closed until then |
| Pin mismatch | Abort before sending the secret; show "Desktop identity changed" with instructions |
| Expired/not-yet-valid cert | Abort; show a clock/certificate error; do not proceed |
| Key file lost/corrupt | Generate a new key; all devices re-pair; log a security event |

No trust-on-first-use. No silent fallback to HTTP.

### 6.4 Why this is not custom cryptography

The protocol uses standard TLS, standard X.509, SHA-256, and HMAC-SHA-256. The only "custom" part is
the pin comparison, which is a hash equality check. No cipher, MAC, KDF, or key exchange is invented.

---

## 7. Credential storage

### 7.1 Desktop

- **TLS private key:** DPAPI (`CryptProtectData`, current-user scope) via a small `ctypes` wrapper,
  stored at `%LOCALAPPDATA%\RawPOS\mobile\identity.dpapi`. If DPAPI is unavailable, fall back to a
  user-only ACL file and show a warning; never store the key in `config.json`.
- **Device registry:** `%LOCALAPPDATA%\RawPOS\mobile\devices.json`, atomic write, strict schema, token
  hashes only. Protected by the same DPAPI blob or user-only ACL. A corrupt/unreadable registry fails
  closed (receiver off, re-pair required), never "trust all".
- **Pairing session:** memory only; cleared on use/expiry/shutdown.
- **Token plaintext:** never persisted, never logged.
- `ConfigStore` remains non-secret: only the enabled flag, port, interface, and desktop id.

DPAPI is an OS facility, not a new dependency. It is recommended but approval-gated because it adds
Windows-specific code and a fail-closed path.

### 7.2 Android

- Generate an AES-256-GCM key in `AndroidKeyStore` with randomized encryption; no user-auth requirement
  (a cashier should not need a fingerprint for every scan).
- Encrypt the token + endpoint + desktop id + pin; store ciphertext and IV in DataStore.
- TLS uses the pin-only trust manager (§6.2); the pin is stored, not a certificate anchor.
- `android:allowBackup="false"`; exclude the credential store from backup/transfer rules.
- Do not use the deprecated `androidx.security:security-crypto` library; use the platform Keystore
  directly.
- Clear credentials on revoke, pin mismatch, or explicit "Forget desktop".
- Do not store barcode history by default.

### 7.3 What is never stored

- Pairing secret after pairing.
- Token plaintext on the desktop.
- Product, price, cart, or customer data on the phone.
- Full barcodes in logs.

---

## 8. Authorization

The token grants exactly two operations:

- `POST /v1/scans`
- `GET /v1/context`

It grants no product lookup, no price, no cart read, no invoice, no shift, no customer, no device
management, no file access. There is no role model on the phone; all paired devices are equal.

The desktop-local UI (owner/cashier) controls pairing and revocation. A cashier who is signed in can
enable the receiver; whether that should be owner-only is an open question. Recommendation: owner-only
for pairing/revocation, any signed-in user for connecting an already-paired device.

### 8.1 Mobile admission policy

A valid token is necessary but not sufficient. The bridge additionally requires:

- the Sales screen is mounted and active;
- the license is in full mode;
- the signed-in cashier has an open shift;
- no database transition (restore/backup) is in progress;
- no modal dialog is open (a discount approval, hold/resume prompt, or settings dialog);
- the checkout context matches (or the device has no context yet).

If any condition fails, the scan is refused with a typed status and **no** dispatch. This is a mobile
admission policy, not a new checkout business rule: it prevents the phone from reporting "added" while
the desktop cannot display or complete the sale. USB and keyboard behavior are unchanged. The owner
must approve this policy explicitly (D17).

---

## 9. Replay and stale-context defense

| Layer | Mechanism | Failure mode |
|---|---|---|
| Transport | TLS 1.2+ | Downgrade refused |
| Request identity | UUIDv4 `event_id` | Missing/duplicate → 400/409 |
| Desktop idempotency | `(device_id, event_id)` reserve-before-dispatch, 24 h, 50k | Full → 503, fail closed |
| Process identity | `server_instance_id` changes on restart | Phone does not auto-replay across restart |
| Invoice identity | opaque `checkout_context` | Mismatch → `rejected_stale_context`, no mutation |
| Rate limit | token/IP buckets | 429, no lockout |

The desktop never dedupes by barcode value. Two intentional scans are two units.

---

## 10. Network exposure

- Off by default. No listener at app start.
- Enabled only by an explicit action in a signed-in session.
- Binds to a selected private interface; no wildcard, public, VPN, link-local, or loopback (test mode
  excepted).
- Default port 47653; configurable.
- Windows Firewall: Private profile, LocalSubnet only. Documented instructions; no silent elevation.
- No UPnP, port forwarding, public DNS, mDNS, or cloud relay.
- The receiver thread is supervised: any unhandled exception stops accepting and surfaces a local
  error; it must not crash the app or corrupt the GUI.
- Shutdown: stop accepting, cancel pending, join, then close the DB.

### 10.1 HTTP server hardening

`http.server.ThreadingHTTPServer` is stdlib and convenient but is **not** a hardened production web
server. v1 mitigations:

- custom `BaseHTTPRequestHandler`, not `SimpleHTTPRequestHandler`;
- exact route table; 404 otherwise; no directory listing;
- strict method, content-type, content-length, header-count, and body-size checks;
- socket timeouts; bounded threads; no keep-alive beyond 100 requests/10 s idle;
- override logging to a redacted logger; generic `Server` header;
- catch every handler exception and return a minimal 500 without a traceback;
- fuzz the parser in Phase 7.

Release gate: either the fuzz suite passes, or adopt a production WSGI server (`waitress`, pure Python,
zero transitive dependencies) as one approved dependency. This is an explicit decision, not an
assumption.

---

## 11. Logging, privacy, and diagnostics

**Logged:** timestamp, event id, device id, status, HTTP code, latency, receiver state changes,
pairing/revocation events (device id and name, never the secret), certificate fingerprint suffix.

**Never logged:** bearer tokens, pairing secrets, verification codes, request bodies, full barcodes,
product names/prices, customer data, stack traces to the network, raw headers.

Barcodes are purchase intent. Even though they are not personal data in the strict sense, logging them
creates a browsing history of the shop's sales. Do not log them by default. If a support mode needs
them, it must be explicit, time-boxed, and warned.

Diagnostics view (desktop): receiver on/off, interface/IP/port, pin suffix, pairing countdown, paired
device names with revoke, last 20 events (id/status/latency), recent errors. No secrets, no barcodes.

Android diagnostics: connection state, last status, endpoint, pin suffix, app version, event ids of
recent failures. No barcode history.

Retention: structured logs 30 days, rotated, size-capped. No upload.

---

## 12. Failure handling (fail closed)

| Condition | Behavior |
|---|---|
| Registry unreadable/corrupt | Receiver off; all tokens refused; UI says re-pair |
| DPAPI unprotect fails | Receiver off; do not fall back to plaintext |
| Certificate key unreadable | Generate new identity; all devices re-pair; log event |
| Pin mismatch | Abort before credentials |
| Token invalid/revoked | 401/403; phone clears credentials |
| Dedupe full | 503; no eviction of live entries |
| DB unavailable/restoring | 503; no dispatch |
| Sales inactive | `rejected_no_checkout`; no dispatch |
| Shutdown in progress | Stop accepting; cancel pending |
| Unexpected exception | Minimal 500; log locally; receiver stays alive unless the listener is compromised |

No "ignore certificate", "trust all devices", "skip auth in debug", or default password exists.

---

## 13. Security testing

- Pairing: expiry, reuse, wrong secret, two phones, approval denial, replacement, revocation.
- TLS: pin match/mismatch, expired/not-yet-valid, wrong SAN, rotated key, IP change, cleartext attempt.
- Auth: no token, malformed token, revoked token, device-id mismatch, token from another device.
- Replay: same event twice, concurrent same event, same id/different payload, after restart, after
  context change.
- Input: oversized body/headers, duplicate JSON keys, wrong content type, path traversal, encoded
  slashes, control characters, Unicode digits, numeric name collision (T21).
- DoS: connection flood, slowloris, pairing flood, then a legitimate scan.
- Storage: registry tamper, DPAPI failure, log scan for secrets/barcodes.
- Static: route inventory, no file APIs, no shell, no SQL in the receiver, no key-event APIs.

See `../quality/test-matrix.md` for IDs.

---

## 14. Decisions requiring approval

1. **Inbound LAN listener**, off by default, with an intentional amendment of the no-network guarantee.
2. **QR library** (`segno`, pure Python, no transitive dependencies) — required by the QR workflow.
3. **Exact-barcode-only** `CheckoutService.resolve_barcode()` — required to prevent T21.
4. **Desktop approval + six-digit code** during pairing (one tap).
5. **One active device** per desktop.
6. **DPAPI-protected key/registry** (or ACL-only fallback) — Windows-specific code, no dependency.
7. **HTTP server hardening path** — fuzz-prove `http.server` or adopt `waitress`.
8. **Owner-only pairing/revocation**, or any signed-in cashier.
9. **Mobile admission policy**: require active Sales + full mode + open shift + no transition; refuse
   otherwise rather than reporting a misleading success. USB/keyboard behavior unchanged.
10. **Process-lifetime dedupe**: accept that exactly-once is not guaranteed across a desktop crash.

---

## 15. Residual risks accepted by this design

- A paired phone with a valid token can add any matching product to the active cart until revoked.
- A desktop crash after a cart mutation but before a response can leave the phone uncertain; the cashier
  must check the desktop. There is no durable exactly-once guarantee.
- A compromised Windows account can read the key/registry while the app runs and can control the POS.
- A compromised or rooted phone with an unlocked Keystore can act as the paired device.
- `http.server` parsing defects may cause availability loss; fuzzing or `waitress` is a release gate.
- The six-digit code is a human check, not a cryptographic authentication of the cashier.
