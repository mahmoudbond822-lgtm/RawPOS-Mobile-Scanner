# Networking Protocol v1

**Status:** proposed — pending approval of the inbound listener, QR library, and exact-barcode service path
**Transport:** HTTP/1.1 over TLS on the shop LAN
**Server:** RawPOS Desktop
**Client:** RawPOS Mobile Scanner (Android)
**Related:** `security-design.md`, `desktop-integration.md`, `barcode-scanning.md`

---

## 1. Design rules

1. **One request, one business outcome.** A scan request does not return until the desktop has either
   produced a business result or explicitly says the outcome is not yet known.
2. **No business data on the wire.** Responses contain an opaque status and a message code, never a
   product name, price, stock level, cart line, invoice, customer, or total.
3. **Idempotent by event id.** A retry of the same physical scan cannot mutate the cart twice.
4. **Context-aware.** A delayed scan cannot land in an invoice other than the one it was captured for.
5. **Authenticated, rate-limited, bounded.** Every scan is authenticated; every field has a limit.
6. **Truthful.** `added` and `merged` mean the desktop confirmed the cart mutation. Nothing else does.
7. **Versioned.** The path and body carry `v: 1`; an unknown version is refused, not guessed.

---

## 2. Transport

### 2.1 Why HTTP/1.1 over TLS (not WebSocket)

| Criterion | HTTP request/response | WebSocket |
|---|---|---|
| Semantics | Natural fit: one scan → one result | Requires application-level request ids, acks, and timeouts anyway |
| Ack clarity | Response body is the result | Must define message correlation and failure semantics |
| Reconnect | Stateless retry with the same event id | Reconnect/resubscribe state machine |
| Testing | curl/Python client, deterministic | Custom client, harder to inspect |
| Firewall | One TCP port | One TCP port |
| Push | Not needed in v1 | Useful only for server→phone events |
| Dependency | stdlib `http.server` + `ssl` | Additional library or custom framing |

**Decision:** HTTP/1.1 with keep-alive, one JSON document per request. Reconsider WebSocket only if a
future requirement needs desktop→phone push, multi-till broadcast, or remote control. Scan-only does
not.

### 2.2 Connection behavior

- TLS 1.2 minimum; TLS 1.3 preferred.
- HTTP/1.1 keep-alive with a 10 s idle timeout; rotate a connection after 100 requests.
- `Content-Length` required. Chunked transfer encoding, compression, and `Expect: 100-continue` are
  rejected.
- No CORS, no cookies, no redirects, no caching.
- Every response has `Cache-Control: no-store`.

### 2.3 Endpoint map

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/v1/pair` | pairing secret | Pair a phone; blocks for local approval |
| POST | `/v1/scans` | bearer token | Submit one scan |
| GET | `/v1/context` | bearer token | Read the current opaque checkout context |
| GET | `/v1/health` | none | Liveness and server instance id |

No other route exists. Unknown paths return `404` with an empty body. No file, directory, or
administration endpoint is exposed.

---

## 3. Common rules

### 3.1 Headers

Request:

```
Content-Type: application/json; charset=utf-8
Accept: application/json
Authorization: Bearer rps1_<43-char-base64url>     (scans, context)
X-RawPOS-Protocol: 1
User-Agent: RawPOS-Mobile/1.0 (Android <api>)
```

Response:

```
Content-Type: application/json; charset=utf-8
Cache-Control: no-store
X-RawPOS-Protocol: 1
```

### 3.2 Limits

| Item | Limit |
|---|---|
| Scan body | 4 KiB |
| Pair body | 2 KiB |
| Header block | 16 KiB, 50 fields |
| Barcode | 1–64 ASCII digits |
| Concurrent connections | 16 total, 4 per source IP |
| Application workers | 8 |
| TLS handshake timeout | 3 s |
| Request read timeout | 5 s |
| GUI dispatch deadline | 2 s |

Violations return the appropriate 4xx without dispatching.

### 3.3 JSON rules

- UTF-8 only, no BOM.
- One top-level object; strict field allowlist; unknown fields are rejected.
- Duplicate keys are rejected.
- Strings are length-limited and validated before use.
- No nested objects deeper than two levels.
- No numbers outside their declared range.

### 3.4 Versioning

`v` is required and must be `1`. An unsupported version returns `400 unsupported_version` with
`{"supported":[1]}`. Future versions use a new path (`/v2/...`) and may keep `/v1/` for compatibility.

---

## 4. Pairing

### 4.1 Preconditions

- RawPOS is signed in, full mode, and the owner/cashier has opened **Mobile Scanner → Pair device**.
- The receiver is enabled and bound to a private LAN address.
- No active device is paired, or the dialog offers an explicit **Replace current device** action.

### 4.2 QR payload

```
rawpos://pair?v=1
  &host=192.168.1.20
  &port=47653
  &sid=<base64url 32-byte pairing id>
  &secret=<base64url 32-byte secret>
  &pin=<base64url SHA-256 SPKI>
  &did=<desktop install id, UUIDv4>
  &name=<URL-encoded desktop display name>
  &exp=<unix-seconds>
```

- `sid` and `secret` are independent 256-bit values.
- `pin` is the SHA-256 of the DER-encoded SubjectPublicKeyInfo of the TLS certificate.
- `did` is stable per desktop installation and non-secret.
- `exp` is 120 s after the dialog opens.
- The QR contains **no device token** and no business data.

### 4.3 Pair request

```http
POST /v1/pair
```

```json
{
  "v": 1,
  "pairing_id": "<base64url>",
  "secret": "<base64url>",
  "device_id": "5f0c2f7e-6a1e-4a6e-9a2b-2f6f5f2b9c11",
  "device_name": "Cashier phone",
  "client_nonce": "<base64url 32 bytes>",
  "app_version": "1.0.0",
  "android_api": 34
}
```

Validation:

- `pairing_id` matches the active session;
- `secret` matches in constant time;
- the session is unexpired and unused;
- `device_id` is a valid UUIDv4;
- `device_name` is 1–64 printable characters (control characters rejected);
- `client_nonce` is 32 bytes;
- the request is from the LAN (enforced by binding, not by trusting a header).

The desktop reserves one attempt for the session, then shows:

```
Device "Cashier phone" wants to connect.
Verification code: 042781
[Approve] [Deny]
```

The code is `HMAC-SHA-256(pairing_secret, pairing_id | device_id | client_nonce)` reduced to six
decimal digits. The phone computes the same code from the QR secret and its own values and displays it.
This is a human comparison, not an authentication factor.

The request **blocks for up to 60 s** waiting for the desktop decision, then returns:

```json
{
  "v": 1,
  "device_id": "5f0c2f7e-6a1e-4a6e-9a2b-2f6f5f2b9c11",
  "desktop_id": "0e7f...",
  "desktop_name": "RawPOS — Front Counter",
  "token": "rps1_<base64url 32 bytes>",
  "server_instance_id": "6a1f...",
  "paired_at": "2026-11-20T12:34:56Z"
}
```

The desktop generates the token with `secrets.token_bytes(32)` and returns it once over pinned TLS.
It stores only a domain-separated SHA-256 verifier. The phone stores the token in Keystore-backed
storage with cloud backup disabled.

If the cashier denies: `403 pairing_denied`; the session is consumed.
If the request times out on the phone while the desktop is still deciding: retry with the same
`pairing_id` and body. The desktop returns the current state (waiting, approved, denied) or repeats the
wait. A different body for the same attempt is `409 pairing_attempt_conflict`.
If the session expired: `410 pairing_expired`.
If the session was already used: `409 pairing_used`.
Too many attempts: `429 pairing_rate_limited`.

### 4.4 Pairing state machine

```
IDLE → WINDOW_OPEN (sid, secret, exp)
WINDOW_OPEN → ATTEMPT_PENDING (device_id, nonce, code)
ATTEMPT_PENDING → APPROVED (token issued, session consumed)
ATTEMPT_PENDING → DENIED (session consumed)
WINDOW_OPEN / ATTEMPT_PENDING → EXPIRED (TTL or dialog closed)
APPROVED → IDLE
```

Only one attempt may be pending. A second phone scanning the same QR while an attempt is pending gets
`409 pairing_in_progress`. The desktop dialog shows the pending device name so the cashier can deny an
unexpected one.

### 4.5 Device limit and replacement

- v1 supports **one active device** per desktop.
- Pairing a second device requires **Replace current device** on the desktop; the previous token is
  revoked immediately.
- A revoked phone receives `403 device_revoked`, clears its credentials, and returns to Connection.
- The desktop keeps a small history of revoked devices (name, revoked at) for diagnostics, not secrets.

### 4.6 Reconnect and endpoint changes

- The phone keeps `desktop_id`, `pin`, `host`, `port`, and `token`.
- During every handshake the phone uses a pin-only trust manager: the leaf's SPKI hash must match the
  stored pin, the certificate must be valid, and hostname verification must pass. A pin mismatch fails
  closed; there is no trust-anyway path.
- On connect it calls `GET /v1/health` (unauthenticated) and `GET /v1/context` (authenticated).
- If the desktop IP changes, the phone can edit the host/port in Settings. The pin still authenticates
  the desktop; a changed pin fails closed and requires re-pairing.
- The desktop reissues its certificate with the same key when its LAN address changes, keeping the pin
  stable. The receiver UI shows the current endpoint. A new pairing QR is only needed if the key
  changed or the token was revoked.
- A desktop reinstall with a new key requires re-pairing. There is no trust-on-first-use fallback.

---

## 5. Scan submission

### 5.1 Request

```http
POST /v1/scans
Authorization: Bearer rps1_...
```

```json
{
  "v": 1,
  "event_id": "ce5c8b7d-0d36-4fd7-b4e2-054f6a1604b1",
  "device_id": "5f0c2f7e-6a1e-4a6e-9a2b-2f6f5f2b9c11",
  "sequence": 184,
  "barcode": "0036000291452",
  "format": "ean_13",
  "scanned_at": "2026-11-20T12:34:56.123Z",
  "context": "9f2c1a7b4e8d0c31"
}
```

| Field | Required | Rules |
|---|---|---|
| `v` | yes | exactly `1` |
| `event_id` | yes | UUIDv4; one per accepted physical scan; reused on retry |
| `device_id` | yes | must match the token's bound device |
| `sequence` | yes | uint64, monotonic per installation; diagnostics only |
| `barcode` | yes | `^[0-9]{1,64}$`; exact string, no trimming |
| `format` | yes | `ean_13`, `ean_8`, `upc_a`, `upc_e`, `code_128`, `code_39`, `itf`, `qr`, `unknown` |
| `scanned_at` | yes | RFC3339 UTC with milliseconds; diagnostics only |
| `context` | no | opaque token from the last response; `null` on first use |

`format` is diagnostic only. The desktop never uses it to resolve a product.

`scanned_at` is never used to reject a scan for clock skew. A difference over five minutes is recorded
in diagnostics. `sequence` is never used as a replay defence; event id is.

### 5.2 Success response

```json
{
  "v": 1,
  "event_id": "ce5c8b7d-0d36-4fd7-b4e2-054f6a1604b1",
  "status": "added",
  "context": "9f2c1a7b4e8d0c31",
  "message_code": "mobile.scan.added",
  "server_instance_id": "6a1f...",
  "server_time": "2026-11-20T12:34:56.180Z",
  "processing_ms": 42
}
```

`status` is one of:

| Status | HTTP | Meaning | Phone presentation |
|---|---|---|---|
| `added` | 200 | Product added as a new line | Success |
| `merged` | 200 | Existing line quantity increased | Success |
| `not_found` | 200 | Exact barcode not in the catalogue | Warning |
| `inactive` | 200 | Product exists but is deactivated | Warning |
| `rejected_no_checkout` | 200 | Sales screen not active or no checkout service | Warning |
| `rejected_no_shift` | 200 | No open shift for the signed-in cashier | Warning; open a shift |
| `rejected_read_only` | 200 | License is not in full mode | Warning; use USB or activate |
| `rejected_stale_context` | 200 | Event belongs to a previous invoice context | Warning; refresh context, rescan |
| `rejected_domain` | 200 | A checkout domain rule refused the scan (e.g. no base currency) | Warning; show `reason_code` if known |
| `rejected_busy` | 200 | A modal dialog or lifecycle transition is open | Retryable warning; finish the dialog |
| `rejected_unavailable` | 200 | DB closed, restoring, or service unavailable | Retryable warning |
| `duplicate` | 200 | Same event id already processed; no new mutation | Neutral; show `original_status` |
| `unknown` | 202 | Dispatch started but no final result within the deadline | "Check desktop"; retry same id |
| `error` | 500 | Unexpected desktop error | Error; retry same id |

`duplicate` carries:

```json
{
  "status": "duplicate",
  "original_status": "added",
  "context": "9f2c1a7b4e8d0c31"
}
```

`rejected_domain` carries only a stable class name, never the raw exception text:

```json
{
  "status": "rejected_domain",
  "reason_code": "NoBaseCurrency",
  "message_code": "mobile.till_error.generic"
}
```

The phone maps known `reason_code` values (`ProductInactive`, `NoOpenShift`, `LicenseReadOnly`,
`NoBaseCurrency`) to localized strings; anything else shows the generic refusal.

Error responses (4xx/5xx) contain:

```json
{
  "v": 1,
  "status": "unauthorized",
  "error": "invalid_token",
  "message_code": "mobile.error.unauthorized"
}
```

No stack traces, product data, SQL, filenames, or version banners.

### 5.3 HTTP error codes

| Code | `error` | Meaning |
|---|---|---|
| 400 | `invalid_request` | Malformed JSON, missing field, bad type, oversized field |
| 400 | `unsupported_version` | `v` is not 1 |
| 400 | `invalid_barcode` | Not 1–64 ASCII digits |
| 401 | `unauthorized` | Missing/invalid token; do not reveal which |
| 403 | `device_revoked` | Token was revoked |
| 409 | `event_id_conflict` | Same event id with a different payload |
| 409 | `pairing_used` / `pairing_attempt_conflict` | Pairing state conflict |
| 410 | `pairing_expired` | Pairing window expired |
| 413 | `too_large` | Body over the limit |
| 415 | `unsupported_media_type` | Not JSON |
| 429 | `rate_limited` | Token or IP bucket empty; `Retry-After: 1` |
| 503 | `service_unavailable` | Receiver paused; `Retry-After: 5` |
| 503 | `dedupe_capacity` | Dedupe table full; fail closed |

### 5.4 Idempotency semantics

The desktop keys an in-memory table by `(device_id, event_id)`:

1. **Reserve before dispatch.** Insert `in_flight` under a lock.
2. If a concurrent request arrives for the same key:
   - same payload → `202 unknown` (or wait briefly for the original);
   - different payload → `409 event_id_conflict`.
3. Dispatch once on the GUI thread.
4. Store `completed(outcome)` and return it.
5. A retry after completion → `status: duplicate`, `original_status: <outcome>`.
6. TTL 24 h, maximum 50,000 entries. When full, reject new scans with `503 dedupe_capacity`; never
   evict a live entry.

The desktop **never** dedupes by barcode value. Two deliberate scans of the same product are two units;
`CheckoutService._add_or_merge` merges them into one line with quantity 2.

### 5.5 Uncertain delivery

The desktop returns `server_instance_id` on health, pairing, and every scan. It changes when the
desktop process restarts.

If the phone has an unacknowledged event and the `server_instance_id` changes:

- do **not** automatically replay the event;
- mark it **Delivery uncertain — check the desktop**;
- require the cashier to inspect the checkout and decide whether to rescan.

If the process is the same, retry the same event id. The dedupe table returns the original outcome.

This is deliberately not exactly-once across a desktop crash. The cart is in memory; a crashed process
loses it. A durable inbox coupled to cart persistence would require a schema and lifecycle redesign and
is out of scope (approval item).

### 5.6 Timeouts and retries (phone)

| Stage | Timeout |
|---|---|
| TCP connect | 2 s |
| TLS handshake | 3 s |
| Response | 4 s |
| Total per attempt | 6 s |
| Automatic retry budget | 15 s |

Retries: 250 ms, 500 ms, 1 s, 2 s, 4 s, with ±20% jitter. Retry only on transport errors, 429, 500,
503, and `unknown`. Never retry a 4xx business refusal with a new event id.

The phone keeps at most **one** scan in flight. While waiting, the scanner shows **Waiting for
desktop** and does not accept another item into a hidden queue.

---

## 6. Checkout context

### 6.1 Why

A scan captured in invoice A can be delayed (network, retry, phone sleep) and arrive after the cashier
finalized A and started B. Applying it to B silently adds a product to the wrong sale.

### 6.2 What it is

An opaque 16-character token derived on the GUI thread from the current invoice identity:

```
context = base64url(sha256(f"{invoice_id or 0}|{created_at or 'new'}|{status}" ))[:16]
```

`InvoiceView` exposes `invoice_id`, `status`, and `created_at`; `created_at` changes when a draft is
started, held, resumed, cancelled, or finalized. The token is **not** a credential and contains no
business data. It survives a language/digit-style remount because the invoice state does, and it
changes when the invoice changes.

### 6.3 Rules

- The phone sends the last `context` it received.
- First scan after pairing: `context` may be `null`; the desktop accepts and returns the current token,
  and remembers it per device.
- A later `null` from a device that already has a context → `rejected_stale_context`.
- Context mismatch → `rejected_stale_context`, **no mutation**, response includes the current context.
  The phone updates its token but does **not** auto-apply the rejected scan; the cashier rescans.
- Context match → normal dispatch.
- `GET /v1/context` lets the phone refresh after reconnect or rejection:
  ```json
  {"v":1,"checkout_active":true,"context":"9f2c1a7b4e8d0c31","server_instance_id":"..."}
  ```
  If no Sales screen is active, `checkout_active:false, context:null`.

### 6.4 What it does not solve

It does not make a scan durable across a desktop crash, and it does not identify which cashier is
holding the phone. It prevents cross-invoice leakage, not misuse by a paired device.

---

## 7. Health

```http
GET /v1/health
```

```json
{
  "v": 1,
  "status": "ready",
  "receiver": "on",
  "server_instance_id": "6a1f...",
  "server_time": "2026-11-20T12:34:56Z"
}
```

- Unauthenticated by design, rate-limited to 30 requests/min per IP.
- Reveals no desktop name, user, shift, database, product, or version details.
- `status` is `ready` or `paused` (receiver intentionally quiesced). It does not explain why.
- Used for reachability and restart detection, not authorization.

---

## 8. Rate limiting

| Scope | Sustained | Burst |
|---|---:|---:|
| Per valid token | 5 scans/s | 10 |
| Per source IP (scans) | 25/s | 50 |
| Pairing | 3 attempts/min | 2 |
| Health | 30/min | 10 |
| Context | 30/min | 10 |

Token-bucket per key, no permanent lockout. `429` includes `Retry-After: 1` and does not revoke the
token. Invalid-token traffic is dropped without creating state that could lock out a legitimate device.

---

## 9. Network binding and firewall

- Default port **47653**, configurable locally.
- Bind to a selected private IPv4/IPv6 address, not wildcard, public, link-local, VPN, or loopback.
  Loopback is allowed only in test mode.
- The receiver UI shows the exact interface, address, and port before enabling.
- Windows Firewall: inbound rule for the port, **Private profile only**, remote address
  **LocalSubnet**. The app shows instructions; it does not silently elevate or create a Public rule.
- No UPnP, no port forwarding, no public DNS, no cloud relay.
- Receiver is **off by default** and starts only after an explicit action by a signed-in user.
- Guest Wi-Fi with client isolation is unsupported; the phone shows a connection failure.

---

## 10. Lifecycle

### 10.1 Admission

A scan is dispatched only when all of the following are true on the GUI thread at execution time:

- the Sales screen is mounted and active;
- the license is in full mode;
- the signed-in cashier has an open shift;
- no restore/backup transition is in progress;
- no modal dialog is open (`QApplication.activeModalWidget() is None`);
- the device token is valid and not revoked;
- the checkout context matches, or the device has no context yet.

Otherwise the receiver returns a typed rejection (`rejected_no_checkout`, `rejected_read_only`,
`rejected_no_shift`, `rejected_unavailable`, `rejected_stale_context`) and does not touch the cart.
USB and keyboard behavior are unchanged.

### 10.2 Receiver lifecycle

| Event | Receiver |
|---|---|
| App start | off; never auto-starts |
| User enables | bind, generate/load certificate, start server |
| Sign-out | stop accepting; cancel pending; stop |
| Sign-in | do not auto-start; user enables again |
| Enter recovery mode | stop before the DB closes |
| Backup/restore transition | return `503 service_unavailable`; do not dispatch |
| Language/digit remount | keep running; the bridge re-resolves the new view |
| App shutdown | stop accepting → cancel pending → join → close DB |

A scan that has not begun GUI dispatch when shutdown starts is cancelled. A scan already inside the
short GUI call is allowed to finish and its outcome cached; the app then closes.

---

## 11. Testability

- A Python `http.client` + `ssl` client using a test certificate and pin.
- JSON fixtures for every request and status, checked in and shared with Android tests.
- A fake dispatcher for the receiver tests; a fake transport for Android tests.
- Fault injection: drop response after mutation, duplicate concurrent requests, same id/different
  payload, GUI delay past deadline, shutdown mid-request, DB transition, revoked token, rate limit.
- Contract tests assert the exact field set and that no business data appears in any response.

---

## 12. Open approvals

1. Inbound LAN listener, off by default, and the intentional amendment of the no-network tests.
2. QR generation dependency `segno` (pure Python, no transitive dependencies).
3. Exact-barcode-only `CheckoutService.resolve_barcode()`.
4. Desktop approval step during pairing (one tap) and six-digit verification code.
5. One active device per desktop.
6. Port 47653 and the Private/LocalSubnet firewall rule.
7. DPAPI-protected TLS key (or ACL-only fallback).
8. Process-lifetime dedupe (no durable exactly-once across a desktop crash).
9. Mobile admission policy: active Sales + full mode + open shift + no transition (refuse otherwise).
