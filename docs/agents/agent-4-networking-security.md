I’ll read the shared facts and current feature plan, then deliver the specification here only. I’ll keep this read-only and won’t create or modify files.# Networking and Security Specification — Agent 4

**Scope:** Android sends barcode scans to a locally running RawPOS desktop over the shop LAN. RawPOS remains the sole authority for product resolution and cart changes. Normal operation requires no cloud service or internet access.

## 1. Decision summary

| Concern | V1 decision |
|---|---|
| Transport and framing | HTTP/1.1 over TLS, JSON with `Content-Length`; one `POST /v1/scans` per scan and a response only after checkout processing or a clearly non-final timeout. |
| Authentication | A 256-bit per-device bearer token. Desktop stores only its SHA-256 verifier; token scope is scan submission only. |
| Pairing | A desktop-opened, 120-second, one-phone pairing window. A QR supplies the LAN endpoint, TLS pin, and one-time 256-bit pairing secret. Desktop approval and a matching short verification code are required. |
| TLS trust | Self-signed ECDSA P-256 certificate, with its SHA-256 SPKI fingerprint delivered in the QR. Validate the pin, certificate dates, and endpoint SAN; never offer “trust anyway.” |
| Dedupe authority | Desktop, in memory for the lifetime of its process. Reserve `(device_id, event_id)` before GUI dispatch; cache the completed result. Retries use the same event ID. |
| Deployment | Receiver is **off by default**, opt-in, bound only to a selected private LAN interface, and allowed by Windows Firewall on the Private profile and local subnet only. |
| Device management | Pairing, listing, and revocation are desktop-local. No LAN endpoint returns or manages products, prices, sales, customers, or inventory. |

## 2. Transport decision

**Recommend HTTPS with one request/response per scan.** JSON gives explicit message boundaries, and the response can reflect the desktop’s actual checkout result rather than merely confirming network receipt.

| Option | Latency | Complexity and acknowledgement | Reconnect and firewall | Testability, dependencies, multi-device |
|---|---|---|---|---|
| HTTP/1.1 over TLS | One LAN request/response; first connection pays a TLS handshake. Keep-alive avoids repeated handshakes. | Familiar framing, headers, timeouts, and status codes. Response can mean “cart mutation confirmed,” not just “bytes received.” | Retry the request with the same event ID. Standard TCP firewall rule; no special proxy configuration. | Easy to exercise with curl/Python. Uses Python TLS/HTTP facilities and existing `cryptography`. Multiple phones make independent requests. |
| WebSocket over TLS | Slightly less per-message overhead after setup, but no material benefit for a barcode-rate workload. | Requires application message IDs, acknowledgement, flow control, connection state, and replay logic anyway. | Must handle reconnect/resubscribe and stale connections. Still needs an inbound TCP firewall rule. | More custom client/server state and likely an added library. Multiplexing is useful for push/broadcast, not required for independent scans. |
| Raw TCP over TLS | Potentially lowest framing overhead. | Requires a custom framing protocol, parser, error model, and acknowledgement semantics. | Reconnection and partial-write handling are custom. Firewall remains a TCP rule. | Harder to inspect and test; no v1 benefit justifies the protocol surface. |
| HTTP long-poll | Poll interval adds latency and needless requests; long-poll holds server resources. | More state and another status retrieval path. | HTTP firewall behavior is good, but each client must manage poll timeouts and reconnects. | Testable, but unnecessary while scans originate from the phone and the desktop need only return a result. |

HTTP/1.1 keep-alive may be used opportunistically, with a 10-second idle timeout and connection rotation after 100 requests. A **single mandatory persistent application connection** is not simpler: it introduces session state, reconnect races, and ambiguous partial delivery without eliminating the need for event IDs and acknowledgements.

Reconsider WebSocket only if a later product requirement needs **event push**, **sub-100 ms feedback at scale**, **multi-till broadcast**, or **remote control**. Remote control would require a separate authorization and threat review; it is not an extension of scan-only access.

## 3. Protocol v1

### Framing and common rules

- Use HTTPS on the configured LAN address and port. `Content-Type: application/json; charset=utf-8`; `Accept: application/json`.
- JSON is UTF-8, uncompressed, and has a strict field allowlist. Reject duplicate JSON keys, unknown fields, malformed JSON, invalid UTF-8, and invalid `Content-Length`.
- Support `Content-Length` only. Reject chunked transfer encoding, conflicting/duplicate lengths, `Expect: 100-continue`, and oversized headers.
- API version is in the path and body as `protocol_version: 1`. No CORS; this API is not for browser clients.
- Do not accept a client-supplied URL, filename, product identifier, or other business data.

### `POST /v1/pair` — bootstrap

No bearer token. TLS pin verification is required before sending the pairing secret. Maximum body: **2 KiB**.

Request:

```json
{
  "protocol_version": 1,
  "pairing_attempt_id": "8f00e46e-275c-4caf-b32a-d2190c8863d7",
  "pairing_secret": "<base64url-encoded-256-bit-secret>",
  "device_id": "be59fdbd-cab4-43e6-892f-22c42db29262",
  "device_name": "Cashier phone",
  "client_nonce": "<base64url-encoded-256-bit-nonce>",
  "device_token": "rps1_<base64url-encoded-256-bit-token>"
}
```

The phone creates `device_id` and `pairing_attempt_id` as UUIDv4 values and generates the token and nonce with the Android CSPRNG. The desktop validates the pairing secret and opens one pending request for desktop approval. It returns `202 Accepted`:

```json
{
  "protocol_version": 1,
  "pairing_request_id": "<opaque-random-256-bit-value>",
  "status": "awaiting_approval",
  "expires_in_seconds": 60,
  "verification_code": "042781"
}
```

The desktop dialog shows the same code and requested device name; the phone shows the code for the cashier to compare. A separate, desktop-local approval action commits the device and token hash. The phone uses `GET /v1/pair/status` with `X-Pair-Request-ID: …` to learn whether it is pending, approved, or rejected. The request ID is a short-lived capability, not a device token; never put it in a URL or logs. The phone already holds its token, so it is never returned by the desktop.

Responses: `202` pending; `200` approved/rejected status; `400` malformed request; `401` invalid/expired pairing secret; `409` pairing already claimed, attempt conflict, or device limit reached; `413` body too large; `429` pairing rate limit. Retrying the same `pairing_attempt_id` and identical body is idempotent while that pairing window is active; a different payload for the same attempt is rejected.

### `POST /v1/scans` — authenticated scan

Headers: `Authorization: Bearer rps1_<token>`, `Content-Type: application/json`, `Accept: application/json`. Maximum body: **4 KiB**.

```json
{
  "protocol_version": 1,
  "event_id": "ce5c8b7d-0d36-4fd7-b4e2-054f6a1604b1",
  "device_id": "be59fdbd-cab4-43e6-892f-22c42db29262",
  "barcode": "0123456789012",
  "format": "digits",
  "scanned_at": "2026-11-20T12:34:56.123Z"
}
```

`barcode` must be 1–128 ASCII digits; `format` is exactly `"digits"`. Reject non-ASCII numerals, whitespace, and alphanumeric values. This is deliberately stricter than general text matching: the current product schema allows only ASCII-digit barcodes, and mobile scans must not fall through to product-name search.

Success and business outcomes return `200` after checkout has handled the event. A duplicate still in flight may return `202` with `status: "processing"`. Other mappings: `400` invalid schema/barcode; `401` unknown token or device mismatch; `403` revoked device; `409` event ID reused with a different payload; `413` too large; `415` wrong content type; `429` rate limited; `503` paused, unavailable, or dedupe capacity exhausted. Error bodies contain only protocol version, event ID if safely parsed, `status`, and optional localization `message_code`; never include product data or stack traces.

### `GET /v1/health` — unauthenticated liveness

Unauthenticated by design, rate limited, and identical in substance regardless of checkout contents. It returns **only** protocol version, receiver state, and a random `server_instance_id` generated on process start:

```json
{
  "protocol_version": 1,
  "status": "ready",
  "server_instance_id": "1bf957b1-1acf-4ef4-a170-b2b5af79401d"
}
```

It reveals no desktop user, product, shift, database, or version details. `503` with `status: "paused"` indicates quiescence; do not use health to disclose why.

### Device management

There are **no network device-list, revocation, or administration endpoints**. Device listing, token revocation, and removal are desktop-local operations.

## 4. Status model and phone acknowledgement

Use one `status` enum across response results and internal lifecycle diagnostics:

`transport_received`, `authenticated`, `validated`, `deduplicated`, `dispatched_to_checkout`, `barcode_resolved`, `added`, `merged`, `product_not_found`, `product_inactive`, `rejected_unauthorized`, `rejected_invalid`, `rejected_no_active_checkout`, `rejected_read_only`, `rejected_paused`, `rate_limited`, `processing`, `duplicate_replay`, `outcome_unknown`, `server_error`.

The first six values are **internal stages only**, not successful acknowledgements. Returning `transport_received` or `dispatched_to_checkout` as if they meant “added” would be unsafe. If GUI processing has not completed by the request deadline, return `202 processing`; the phone retries the **same** event ID.

Examples of internal diagnostics:

```json
{"event_id":"…","status":"transport_received"}
{"event_id":"…","status":"authenticated"}
{"event_id":"…","status":"validated"}
{"event_id":"…","status":"deduplicated"}
{"event_id":"…","status":"dispatched_to_checkout"}
{"event_id":"…","status":"barcode_resolved"}
```

Final response examples:

```json
{"protocol_version":1,"event_id":"…","status":"added","message_code":"scan.added","server_instance_id":"…"}
{"protocol_version":1,"event_id":"…","status":"merged","message_code":"scan.merged","server_instance_id":"…"}
{"protocol_version":1,"event_id":"…","status":"product_not_found","message_code":"scan.not_found","server_instance_id":"…"}
{"protocol_version":1,"event_id":"…","status":"product_inactive","message_code":"scan.inactive","server_instance_id":"…"}
{"protocol_version":1,"event_id":"…","status":"rejected_no_active_checkout","message_code":"scan.no_active_checkout"}
{"protocol_version":1,"event_id":"…","status":"rejected_read_only","message_code":"scan.read_only"}
{"protocol_version":1,"event_id":"…","status":"rejected_paused","message_code":"scan.desktop_paused"}
{"protocol_version":1,"event_id":"…","status":"rate_limited","message_code":"scan.retry_shortly"}
{"protocol_version":1,"event_id":"…","status":"duplicate_replay","original_status":"added","message_code":"scan.already_confirmed"}
{"protocol_version":1,"event_id":"…","status":"processing","message_code":"scan.processing"}
{"protocol_version":1,"event_id":"…","status":"rejected_unauthorized","message_code":"scan.not_authorized"}
{"protocol_version":1,"event_id":"…","status":"rejected_invalid","message_code":"scan.invalid"}
{"protocol_version":1,"event_id":"…","status":"outcome_unknown","message_code":"scan.check_desktop"}
```

The phone must **never display “added” or “merged” until the desktop returns that result after confirming the cart mutation**. `duplicate_replay` means no second mutation occurred; if the cached original was `added`, the phone may say “Previously confirmed as added,” not imply a new mutation.

## 5. Pairing flow

```text
Cashier             RawPOS desktop              Phone
   |                       |                       |
   |-- Open pairing ------>|                       |
   |                       | Generate 256-bit      |
   |                       | secret; 120-s window  |
   |<-- QR + endpoint -----|                       |
   |                       |<------ Scan QR --------|
   |                       |<-- POST /v1/pair ------|
   |                       | Verify secret; reserve|
   |                       | one pending attempt    |
   |                       |-- 202 + request ID --->|
   |                       | Show device + code     |
   |                       |<-- Compare code ------>|
   |-- Approve locally --->|                       |
   |                       | Store token hash;      |
   |                       | consume pairing secret |
   |                       |<-- GET pair/status ----|
   |                       |-- approved ----------->|
   |                       |                       | Save token securely
```

The desktop opens the window only after an explicit local action; it expires after **120 seconds**, on dialog close, cancellation, or successful approval. One valid attempt claims the window. A denial or timeout consumes the QR; generate a new one rather than reusing it. The QR contains `protocol_version`, numeric LAN host, port, desktop display name and install ID, pin algorithm/fingerprint, expiry, and the one-time pairing secret. It contains no device token.

The desktop generates the pairing secret with `secrets.token_bytes(32)`. For the six-digit verification code, both sides derive a short comparison value from HMAC-SHA-256 over the pairing secret, pairing attempt ID, device ID, and phone nonce. It is a **human comparison**, not an authentication substitute. The phone computes the code and the desktop displays its own; approval requires the cashier to compare them.

`device_id` is generated by the phone as UUIDv4; it identifies the installation but is not a credential. The 256-bit device token is generated on the phone and shown once only to the pairing request. The desktop stores its hash only after approval. Limit the installation to **10 paired devices**.

If two phones scan one QR, the first valid request claims it. The other receives `409 pairing_in_progress` or `pairing_window_consumed`; it cannot replace the pending device. For a lost phone, revoke its device locally; re-pairing issues a new token and revokes the old one. A factory reset or reinstall that loses the phone token requires re-pairing. A desktop reinstall or lost TLS private key creates a new pin and requires explicit re-trust/re-pairing. On an IP change, existing tokens need not be revoked: show an endpoint-update QR that has the new host and same pin; the phone accepts it only for its already-known desktop ID and pin. A changed pin requires local confirmation and renewed pairing.

Desktop stores: TLS private key protected by Windows DPAPI and current-user/SYSTEM-only file ACL; certificate; device IDs/names and token hashes; revoked state and timestamps. Pairing secret is memory-only and cleared after use/expiry. Phone stores: device ID, token, desktop ID, endpoint, and pin in Android Keystore-backed app storage; disable cloud backup for these credentials. Neither side logs secrets.

## 6. TLS design

At first enablement, generate an ECDSA P-256 key using `cryptography` (`ec.generate_private_key(ec.SECP256R1())`) and a self-signed X.509 server certificate. Include `serverAuth`, `BasicConstraints(ca=False)`, and SAN entries for the selected private IPv4/IPv6 addresses and any supported local DNS name. Do not depend on CN hostname matching. Require TLS 1.2 or newer; prefer TLS 1.3. Renew the certificate before expiry, retaining the same key so the SPKI pin remains stable. A key rotation changes the pin and requires explicit re-trust; never silently trust a new key.

The desktop computes the SHA-256 digest of the DER-encoded **SubjectPublicKeyInfo** and places its base64url value in the QR. The phone’s TLS trust manager accepts only the pinned public key, a currently valid certificate, and a SAN matching the requested IP/hostname. A self-signed certificate is not system-CA trusted: the QR pin is the trust anchor. The QR must be scanned from the desktop’s local pairing screen, and the phone and desktop verification codes must match. On IP change, issue a certificate with updated IP SANs using the same key; the pin remains valid.

This is standard TLS/X.509, SHA-256 pinning, and HMAC use—not custom cryptography. Use platform TLS for handshakes and certificate parsing; use `cryptography` for certificate/key creation and SPKI serialization; use stdlib `hashlib`, `hmac`, and `secrets` for digests, comparisons, and random values. Never implement cipher, signature, or key-exchange primitives.

Trust alternatives:

- **Plain HTTP + HMAC:** reject. It exposes bearer/pairing secrets and barcode traffic to LAN observers; HMAC alone does not authenticate the desktop or prevent disclosure.
- **TLS with system CA:** not practical for a private IP and offline installation with a self-signed certificate.
- **TLS with user-installed CA:** workable but requires installing and managing a CA on every phone, with significant operational risk.
- **TLS plus QR-delivered pin:** recommended; authenticates the expected desktop without internet or user-installed CA.

On pin mismatch, certificate expiry, or SAN mismatch, abort before sending credentials. Show a localized message such as **“Desktop identity changed. Do not continue; verify RawPOS and scan a new pairing QR.”** Never provide a trust-anyway button or silently fall back to HTTP.

## 7. Authentication and authorization

Use `Authorization: Bearer rps1_<base64url-encoded-32-random-bytes>`. Tokens contain 256 bits of entropy, have no username/password semantics, and are scoped only to `scan:write`. Desktop computes a domain-separated SHA-256 verifier from the decoded token bytes and stores **only that hash**. Look up the candidate device record and compare the verifier with `hmac.compare_digest`; do not reveal whether a device ID or token was wrong. Require body `device_id` to match the token’s bound device.

The phone keeps the plaintext token only in protected app storage and memory while sending. Desktop presents it only once during pairing and never persists plaintext. Re-pairing rotates the token; revocation takes effect immediately on desktop and invalidates any in-memory token index.

A stolen token can submit numeric barcode attempts and potentially add a matched product to an active checkout. It cannot retrieve products, prices, sales, customers, inventory, receipts, or shift data; alter products; finalize sales; or manage devices. There must be **no endpoint** that returns products, prices, sales, or customer data.

## 8. Replay, dedupe, and idempotency

The phone generates one UUIDv4 `event_id` per physical scan and persists it with that event before sending. Every retry—including an app restart retry—uses the same ID and identical payload. `scanned_at` is RFC3339 UTC for diagnostics only; reject malformed timestamps but do not reject a scan because of clock skew. Flag a clock difference above five minutes in local diagnostics.

The desktop keys its in-memory dedupe table by `(device_id, event_id)` and stores a payload digest, state, and final response for 24 hours. Reserve the key **before** GUI dispatch. Same ID and same payload while in flight returns `202 processing`; after completion, return `duplicate_replay` with the cached `original_status`. Same ID with a different barcode or other payload returns `409 event_id_conflict` and is never dispatched. Do not evict an unexpired entry to make room: if the bounded cache reaches 50,000 entries, reject new scans with retryable `503 dedupe_capacity`.

This prevents two cart mutations across retries while the process is alive:

1. Phone reuses the same ID.
2. Desktop reserves it before dispatch.
3. Concurrent/retried requests cannot dispatch that reservation again.
4. The GUI-thread operation runs once; its result is cached.
5. A retry returns the cached outcome, not another checkout call.

The checkout draft and dedupe table are both in memory. If RawPOS crashes after mutating the cart but before replying, the process restart loses both the cart and cache. The phone compares the health `server_instance_id`; after it changes, the phone marks any unconfirmed event **uncertain** and does not auto-replay it. The cashier checks the current checkout before manually retrying. This avoids duplicate automatic additions but cannot guarantee durable exactly-once behavior across a crash. Achieving that would require durable idempotency state coupled to cart persistence, which is outside the verified in-memory checkout model and needs Agent 1/project approval.

If an exception occurs after dispatch begins and the desktop cannot determine whether the cart changed, mark the event `outcome_unknown` and never redispatch that ID automatically. Prefer a possible missed scan requiring cashier review over a silent duplicate.

## 9. Rate limiting and denial of service

Proposed concrete limits:

- Per valid token: **5 new scans/second**, burst **10**.
- Per source IP aggregate: **25 scans/second**, burst **50**. Per-IP limiting is token-bucket based, not a timed lockout.
- Pairing: **3 attempts/minute/IP**, burst 2; only one pending attempt per pairing window.
- Health: **30 requests/minute/IP**.
- Maximum 16 concurrent connections, 4 per source IP, and 8 worker slots for application requests. Excess work gets `503`/connection refusal without unbounded thread creation.
- Request bodies: 4 KiB scans, 2 KiB pairing. Headers at most 16 KiB and 50 fields. Barcode at most 128 digits.
- Header/body read timeout: 2 seconds each, 5-second total request deadline; TLS handshake timeout 3 seconds; keep-alive idle timeout 10 seconds. No compression or chunked bodies.

Exceeding a token/IP bucket returns `429` with `Retry-After: 1`; do not revoke or lock the token. Invalid-token traffic may be slowed or dropped but must not create persistent lockout state that a nearby attacker can use to block a cashier. Limits are high relative to normal cashier scanning and are configurable only with an explicit owner action. The remaining risk is that a LAN attacker can still consume bandwidth or exhaust local CPU; the receiver is never a general-purpose web server.

## 10. Network binding and deployment

Use **TCP port 8765 by default**, with a local configuration option if it conflicts. Do not silently choose a random port: the QR and firewall rule must show the actual port. Bind only to a selected RFC1918 IPv4 or ULA IPv6 address, not wildcard addresses, loopback, link-local, public, VPN, or unrelated interfaces. Show the exact interface and address before enabling.

Install/enable a Windows Firewall inbound rule for TCP 8765, **Private profile only**, remote address `LocalSubnet`; do not create router port forwarding, UPnP mappings, or a Public-profile rule. If the firewall rule cannot be applied, show a clear local setup error rather than weakening firewall settings. Listener is off by default and starts only after explicit enablement in a usable, full-mode checkout session.

A LAN/subnet change invalidates the old endpoint. Stop accepting on the old interface, select the new private interface, refresh SANs, and show an endpoint-update QR. Do not publish a VPN address. CGNAT does not affect peers on the same LAN; hotspot client isolation, VPN routing, or multiple NICs may prevent reachability and should produce an ordinary connection failure, not a relay/cloud fallback.

## 11. Reliability and recovery

Use a 2-second connect timeout, 3-second TLS handshake timeout, and 4-second response timeout. The phone allows at most five attempts with exponential backoff of approximately 250 ms, 500 ms, 1 second, 2 seconds, and 4 seconds plus ±20% jitter, capped at a 15-second total automatic retry budget. Reuse HTTP connections where viable. On sleep, Wi-Fi roam, or connection reset, discard the stale socket, re-read health, then retry only the same event ID.

A retry is safe only when the desktop `server_instance_id` remains the same. If it changed, show **“Delivery uncertain—check the desktop checkout”**; do not create a new event ID or automatically re-send. If the desktop is unavailable before accepting a scan, keep it as **Not sent** and require a cashier action to retry after checking the active checkout. Do not silently queue old scans for automatic delivery into a possibly different checkout.

User-visible states: **Sending**, **Confirmed added/merged**, **Not found**, **Inactive**, **Rejected—checkout unavailable/read-only**, **Waiting to retry**, and **Delivery uncertain—check desktop**. “Sending” and “processing” never imply a cart mutation.

Example lost-response sequence:

```text
Phone                 Desktop receiver              GUI checkout
  | POST event E ------------>|                           |
  |                           | reserve E; queue once     |
  |                           |-------------------------->|
  |                           |                           | mutate cart
  |                           |<--------------------------| confirmed result
  |        response lost      | cache result for E        |
  | retry same E ------------>|                           |
  |<-- duplicate_replay, original_status=added -----------|
```

## 12. Desktop integration constraints

The network receiver runs off the GUI thread. It may parse, authenticate, validate, rate-limit, reserve dedupe IDs, and enqueue work, but **must not call `CheckoutService` or touch a repository/SQLCipher connection from its server thread**.

Use a GUI-owned queued Qt signal/controller seam. The receiver waits on a bounded future/event for the GUI result; the GUI slot rechecks application state at execution time, invokes the approved scan entry point, refreshes checkout UI, and returns a structured result. Do not retain `CheckoutView` references: language/digit changes destroy and remount the window/widgets, so resolve the active controller when the queued action runs.

The scan is accepted only when the desktop confirms the intended active checkout and the app is not read-only, quiescing, or in recovery. Agent 1 must define the precise “active checkout” check and result seam. The existing `CheckoutService.resolve_scan()` performs a product-name search fallback, so the mobile path must use an exact barcode-only operation; it must never inherit the text-search behavior. A needed service change must be escalated, not assumed.

Before backup, restore, database replacement, or another critical operation, pause new dispatch and drain or fail queued work before the database is closed. Do not hold the SQLCipher connection in the listener or across network waits. During quiescence, return `rejected_paused`/retryable `503` without claiming a cart mutation. On shutdown, stop accepting first, finish or mark in-flight requests uncertain, detach queued callbacks, then let the existing database shutdown sequence proceed.

If `checkout_service` is `None` in recovery mode, stop the listener and reject any already queued action as unavailable; never attempt checkout or database access. The health endpoint must not expose recovery details.

## 13. Threat model

Assets: checkout integrity, active shift context, device tokens, TLS private key, local availability, and confidentiality of store data. Trust boundaries: phone app, untrusted LAN/Wi-Fi, physical QR display, desktop network listener, GUI thread/services, and Windows account/storage. Likelihood and impact are relative for a small-shop LAN.

| Threat | Likelihood / impact | Mitigation | Residual risk |
|---|---|---|---|
| Unauthenticated scan submission | Medium / High | TLS plus per-device bearer; no anonymous scan endpoint; strict schema and rate limits. | LAN attackers can still cause connection noise. |
| Replay or duplicate delivery | Medium / High | UUID event ID; reserve before dispatch; payload conflict check; cached outcome; same-ID retries. | Dedupe is not durable across process restart; uncertain events require cashier review. |
| Token theft | Low–Medium / High | Android Keystore, DPAPI/ACL-protected desktop state, TLS pin, no token logs, local revocation, scan-only scope. | A stolen valid token can add barcodes until revoked. |
| Rogue AP/MITM | Medium / High | TLS with QR SPKI pin and verification-code comparison; abort on mismatch. | User may approve a deliberately spoofed QR or compromised endpoint. |
| QR shoulder-surf | Medium / Medium | Secret expires in 120 seconds, one attempt, desktop approval, visible pending device, local cancellation. | Observer may race to claim the window; visible attempt should alert the cashier. |
| Spoofed desktop/QR | Low–Medium / High | Display desktop name/ID and compare phone/desktop codes; pin comes from local desktop screen. | Social engineering can still make a user scan an attacker-controlled QR. |
| Malicious paired phone | Medium / High | Exact numeric barcode only; active-checkout requirement; throttling; desktop-local revocation; no data-read scope. | Can still add matched products to an open checkout. |
| DoS flood | Medium / Medium | Bounded workers/connections/body sizes, token buckets, timeouts, private-profile firewall. | A local attacker can consume shared Wi-Fi or desktop resources. |
| Malformed JSON / parser abuse | Medium / Medium | Strict field allowlist, duplicate-key rejection, size/depth limits, fixed routes, no chunked bodies. | Parser/runtime defects remain possible. |
| Path traversal | Low / Medium | Exact fixed routes; no path-to-filesystem mapping or file-serving endpoint. | Implementation mistakes must be caught by tests. |
| SSRF | Low / High | No client-supplied URL and no server-side fetching or DNS lookup. | Added future endpoints could reintroduce risk. |
| Local file disclosure | Low / High | No file endpoints; key/state files have restrictive ACL; no directory listing. | Desktop-account compromise exposes local files. |
| Log leakage | Medium / Medium | Do not log tokens, pairing secrets, request bodies, or barcodes; redact diagnostics. | Event timing/device metadata still reveals usage patterns. |
| Physical phone theft | Medium / High | Device lock, Keystore-backed token, app-private storage, no backup; revoke locally. | An unlocked/rooted phone may expose its active credential. |
| Desktop compromise | Low / Critical | Standard OS protections, least privilege, private-only listener, no cloud exposure. | Malware/admin compromise can access the app, key, database, and checkout; out of protocol scope. |
| Insider misuse | Medium / High | Local pairing approval, device labels, event audit trail, cashier-visible cart confirmation, local revocation. | Paired staff can intentionally submit scans; device ID is not proof of a human identity. |

## 14. Logging and diagnostics

Log locally: event ID, device ID, status, HTTP result, latency, timestamp, and a coarse receiver error code. Do **not** log full/partial/hashed barcodes, bearer tokens, pairing secrets, verification codes, request bodies, product names/prices, customer information, or raw authorization headers. Keep structured logs for 30 days with rotation and a size cap; no cloud upload or telemetry.

Provide a desktop diagnostics view showing receiver on/off, selected interface/IP/port, pin suffix, pairing-window countdown, paired device names and revocation controls, last 20 event IDs/statuses/latencies, and recent connection/rate-limit errors. It must not show tokens or barcodes. Include a copy-diagnostics action that redacts local addresses if the user chooses to share it.

## 15. Testability

Contract tests should use checked-in JSON request/response fixtures for pairing, scans, every status, invalid fields, conflicts, and error mappings. Exercise locally with curl using a test certificate and pin, or a small Python `http.client`/`ssl` client that validates the exported test certificate, SAN, and SPKI pin. A raw `curl -k`-only test is insufficient because it omits normal certificate validation; use the test certificate as a trust anchor and pin it.

Fault-injection points: response loss after checkout mutation, disconnect during body/response, GUI queue delay, concurrent same-ID requests, duplicate ID with changed payload, process restart, Wi-Fi/IP change, backup/restore quiescence, and shutdown with pending work.

Security cases: expired/reused QR; simultaneous phones on one QR; pin/SAN/expiry mismatch; revoked or wrong-device token; oversized/slow headers and body; duplicate JSON keys; Unicode digits; name-search payloads; unsupported transfer encoding; request floods; no-data health response; no product/customer routes; and verification that tokens, secrets, and barcodes never appear in logs. Extend existing no-network tests deliberately: the guarantee becomes “no outbound internet; no inbound LAN listener unless explicitly enabled,” not an unqualified ban on all sockets.

## 16. Verified facts, assumptions, and dependencies

**Verified from the shared fact pack:** RawPOS is Python 3.12+, PyQt6, SQLCipher, and already includes `cryptography`; it currently makes no network calls and has tests enforcing that. `CheckoutService` owns an in-memory mutable draft, is not thread-safe, and uses repositories bound to a single SQLCipher connection. `resolve_scan()` falls back from barcode lookup to product-name search. `CheckoutView.scan()` has no public result-returning non-UI entry point. Database protection can close/replace the database, and recovery mode nulls DB-dependent services. Relevant source references include `src/rawpos/services/checkout_service.py`, `src/rawpos/ui/sales/checkout_view.py`, `src/rawpos/services/database_protection_service.py`, `src/rawpos/ui/app_shell.py`, and the no-network tests identified in the fact pack.

**Proposed, not verified:** Port 8765; limits, timeouts, ten-device cap, 120-second pairing window, DPAPI state-file layout, Android Keystore/backup policy, and the proposed endpoint-update QR are design decisions. There is no existing mobile implementation, receiver, or pairing-state store in the inspected facts.

**Agent dependencies:**

- **Agent 1:** Define an exact-barcode checkout entry point, active-checkout eligibility, GUI-thread dispatch/result acknowledgement, and quiescence/shutdown coordination. Do not call `resolve_scan()` blindly or from the network thread.
- **Agent 2:** Confirm the phone-side schema, Keystore storage, pin validation, pairing-code comparison, and uncertain-delivery UX. The supplied facts do not define Agent 2’s role, so these are interface dependencies rather than assigned implementation tasks.
- **Agent 6:** Confirm cross-agent integration, packaging/firewall behavior, and test ownership. Their exact remit was not included in the shared facts.

**Open approval items:** Approve the opt-in inbound LAN receiver and its effect on no-network tests; the Windows Private/LocalSubnet firewall rule and default port; separate DPAPI-protected scanner state; desktop approval/SAS pairing and ten-device limit; whether scans require the checkout screen to be active; barcode-free logs; endpoint-update QR behavior; and acceptance of process-lifetime dedupe. If durable exactly-once behavior across a crash is required, the in-memory cart model is insufficient and must be redesigned with Agent 1 before implementation.