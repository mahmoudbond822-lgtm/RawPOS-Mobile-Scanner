# Phase 2 — Build Evidence

**Phase:** 2 — desktop receiver and secure pairing (authorized 2026-11-20).
**Branch:** `feature/mobile-receiver` in RawPOS, created from `797167acbcb14d96b476891a573a7e31638acf12`.
**Scope boundary:** `../planning/approval-packet-phase-2.md`. No business code, no checkout, no schema.

Every result below was produced in this session on this machine. Commands and outcomes are quoted as
run, not reconstructed.

---

## Increment 1 — protocol and registry, no listener

### What was added

| File | Purpose |
|---|---|
| `src/rawpos/mobile/__init__.py` | Package docstring and module map |
| `src/rawpos/mobile/protocol.py` | Wire constants, strict JSON, typed errors, pair/health payloads |
| `src/rawpos/mobile/devices.py` | Device registry: token verifiers only, one active device, atomic writes |
| `tests/unit/test_mobile_protocol.py` | 22 tests |
| `tests/unit/test_mobile_devices.py` | 35 tests |

No socket is created, no port is bound, and no route exists yet. `POST /v1/pair`, `GET /v1/health`, TLS,
and the pairing state machine remain undone.

### Baseline, measured before any change

```
py -3 -m pytest -q
1450 passed in 85.89s (0:01:25)
```

The count matches the pinned baseline in `README.md` (1,450). The duration is faster than the documented
134.97 s; timing varies with machine load and is not a gate.

### Task 1 — the no-network tests: no amendment was required

`approval-packet-phase-2.md` §11 task 1 expected an amendment to `tests/integration/test_no_network.py`
(T057) and `tests/failure/test_no_network.py` (T071). Measured first, before editing:

```
py -3 -m pytest tests/integration/test_no_network.py tests/failure/test_no_network.py -q
2 passed in 2.05s
```

Both pass **unmodified**, and the reasoning holds:

- The receiver is off by default (D6), and neither test enables it, so nothing binds a socket.
- The fixtures block `socket.socket`, `create_connection`, `getaddrinfo`, and `gethostbyname` only for
  the duration of their own test, so a future receiver test in another module is unaffected.
- If Phase 2 wiring ever constructs a listener or resolves a hostname during a core flow, T057 fails —
  which is exactly the guard the project wants.

**Decision:** the A3 amendment stays available and unused. Adding an "inbound listener exception" today
would either be a no-op or would weaken a passing safeguard. The outbound guarantee remains intact and
unamended.

The guard that is genuinely missing — "no socket exists while the receiver is disabled" — cannot be
written against `server.py` before `server.py` exists. It belongs to increment 4 and is recorded as an
open item rather than skipped silently.

### Gates after the change

| Gate | Command | Result |
|---|---|---|
| Lint | `py -3 -m ruff check .` | `All checks passed!` |
| Types | `py -3 -m mypy src` | `Success: no issues found in 152 source files` |
| Money-path guard | `py -3 scripts/check_no_float.py` | `no-float gate: PASS` |
| Full suite | `py -3 -m pytest -q` | **1507 passed in 83.94s** |

1507 = 1450 existing + 57 new. No existing test was modified, weakened, or deleted.

### Deliberate non-implementations

Recorded so they are not mistaken for oversights:

- `check-digit` and barcode validation — Phase 3, and RawPOS treats barcodes as opaque.
- `status`/`outcome` enums for scans — Phase 4.
- Rate limiting, thread bounds, TLS, and the pairing state machine — later increments of this phase.
- Any checkout or `CheckoutService` reference — Phase 5, and forbidden here by D9's approval boundary.

---

## Increment 2 — pairing state machine

### What was added

| File | Purpose |
|---|---|
| `src/rawpos/mobile/pairing.py` | 120 s single-use window, one pending attempt, constant-time secret check, typed rejections, SAS code |
| `tests/unit/test_mobile_pairing.py` | 24 tests |

Still no socket, no port, no route, and no TLS. The state machine is exercised entirely through an
injected clock, so no test sleeps and every expiry rule is deterministic.

### Contract decisions the Android client must match

The protocol text left four points under-specified. Each was resolved explicitly here, and each is a
cross-platform contract that will break silently if the phone disagrees:

| Point | Decision | Why |
|---|---|---|
| SAS derivation key | HMAC key is the **decoded 32 secret bytes**, not the base64url text | The text form is an encoding detail; the key material is the bytes |
| SAS message | `f"{pairing_id}\|{device_id}\|{client_nonce}"` in ASCII, separators literal | All three fields are fixed-shape and cannot contain `\|`, so the encoding is unambiguous |
| SAS reduction | `int.from_bytes(digest, "big") % 1_000_000`, zero-padded to six digits | Simple, testable, one obvious implementation on both sides |
| Wrong secret or wrong `pairing_id` | `401 unauthorized`, identical response | Revealing which one failed tells an attacker whether the QR was current |

Pinned vector (must fail on Android if the derivation drifts):

```
secret      = AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8
pairing_id  = "P" * 43
device_id   = 5f0c2f7e-6a1e-4a6e-9a2b-2f6f5f2b9c11
client_nonce= "N" * 43
code        = 363186
```

Two more behaviours were chosen rather than inferred:

- **Second phone while an attempt is pending** returns `409 pairing_in_progress`; the **same** phone
  returning with a changed nonce or name returns `409 pairing_attempt_conflict`. A byte-identical retry
  returns the same code and does not consume the rate budget, because a phone waiting on a slow cashier
  must be able to poll.
- **Reopening the dialog** supersedes the previous window instead of refusing. The old QR stops working
  immediately, which is the safe direction.

### Gates after the change

| Gate | Command | Result |
|---|---|---|
| Lint | `py -3 -m ruff check .` | `All checks passed!` |
| Types | `py -3 -m mypy src` | `Success: no issues found in 153 source files` |
| Money-path guard | `py -3 scripts/check_no_float.py` | `no-float gate: PASS` |
| Full suite | `py -3 -m pytest -q` | **1531 passed in 129.97s** |

1531 = 1450 existing + 57 increment 1 + 24 increment 2. No existing test was modified.

### Known limitation recorded, not hidden

Pairing rate limiting (3 attempts/minute) is enforced here, per window. **Per-IP** limiting is a server
concern and does not exist yet, so a caller that opens a new window can reset the pairing budget. That is
acceptable only because opening a window requires a signed-in owner at the desktop (A14); it must be
revisited when `server.py` lands.

---

## Increment 3 — local TLS identity, and a guard conflict it exposed

### What was added

| File | Purpose |
|---|---|
| `src/rawpos/mobile/tls.py` | Self-signed ECDSA P-256 leaf, SPKI pin, reissue with the same key, DPAPI-protected key storage, TLS 1.2+ context |
| `tests/unit/test_mobile_tls.py` | 17 tests, including a real loopback TLS handshake |
| `tests/unit/test_packaging_secrets.py` | **Amended** — see below |

### The guard conflict

The first full run failed:

```
FAILED tests/unit/test_packaging_secrets.py::test_app_package_has_no_signing_code_or_secrets
1 failed, 1547 passed
```

T072 (FR-011) scans every file under `src/rawpos/` and forbids `private_bytes` ("key serialization")
and `generate_private_key` ("key generation"). Generating and serialising a per-install TLS key
requires both, and `cryptography` offers no alternative API.

The two obvious workarounds were rejected deliberately: obfuscating the call
(`getattr(key, "private_" + "bytes")`) and moving the module outside the scanned tree would both
defeat the guard **silently**, which is the exact failure it exists to prevent.

**Resolution (owner-approved, 2026-11-21):** a narrow exemption in `_EXEMPT`, pinned to
`src/rawpos/mobile/tls.py` and to those two labels only. Two new tests hold the pin:

- `test_tls_exemption_is_pinned_to_one_file_and_two_labels` — fails if the map gains a file or a label.
- `test_tls_exemption_cannot_hide_licence_signing` — asserts the exemption is disjoint from the
  licence-signing labels (`derive_ddk`, `sign_payload`, `MASTER_KEY`, `Ed25519PrivateKey`, PEM
  literals, secret file IO).

The guard was then mutation-probed, not merely assumed. Appending `derive_ddk` to `devices.py`:

```
E       assert not {'src\\rawpos\\mobile\\devices.py': ['vendor DDK derivation']}
1 failed, 4 passed
```

File restored afterwards. The exemption is narrow in practice, not only in intent.

### Design decisions recorded

- **TLS 1.2 minimum**, compression disabled, no client certificates (authorisation is the token).
- **SANs** cover every selected LAN address plus `127.0.0.1` and `localhost`; `ensure_identity`
  reissues when an address changes, which is what keeps the phone's stored endpoint usable.
- **Pin = SHA-256 of the SPKI**, base64url, 43 characters — the value the QR will carry.
- **Reissue keeps the private key**, so the pin survives an address change or expiry (D4).
- **One-way fallback** (A6): `mode=acl` is written into the envelope and never migrated; a failed
  DPAPI unprotect raises `KeyProtectionError` with no plaintext path.

### Known limitation, stated plainly

Python's TLS stack loads key material only from files, so `server_context` decrypts the key to a
short-lived `0600` file inside the identity directory, loads it, and unlinks it in a `finally`.
The plaintext window is the duration of `load_cert_chain`; `sweep_temp_files` clears anything a crash
leaves behind. On Windows the `0600` mode is advisory and the protection is the directory ACL.

### Gates after the change

| Gate | Command | Result |
|---|---|---|
| Lint | `py -3 -m ruff check .` | `All checks passed!` |
| Types | `py -3 -m mypy src` | `Success: no issues found in 154 source files` |
| Money-path guard | `py -3 scripts/check_no_float.py` | `no-float gate: PASS` |
| Full suite | `py -3 -m pytest -q` | **1550 passed in 93.44s** |

1550 = 1450 existing + 57 + 24 + 17 + 2 new guard tests.

---

## Increment 4 — the listener, with bounded threads

### What was added or changed

| File | Change |
|---|---|
| `src/rawpos/mobile/server.py` | New: TLS listener, exact routes, bounded concurrency, per-IP rate limiter, pair flow |
| `src/rawpos/mobile/pairing.py` | `wait_for_decision`, `replace_allowed`, and a condition variable so the request can block on the cashier's decision |
| `src/rawpos/mobile/devices.py` | Optional `RegistryCodec`, so the registry can be opaque at rest |
| `src/rawpos/mobile/tls.py` | `DpapiCodec` / `PlainCodec` for that seam |
| `tests/integration/test_mobile_server.py` | New: 15 tests, all over a real TLS connection |

This is the first code in RawPOS that opens a socket — and only inside `start()`, which no caller
invokes yet. The receiver is inert in the application; the dialog and wiring are increment 5.

### Route table

| Method | Path | Behaviour |
|---|---|---|
| `GET` | `/v1/health` | 200 with `v`, `status`, `receiver`, `server_instance_id`, `server_time` only |
| `POST` | `/v1/pair` | 200 token, 202 waiting, 403 denied, 410 expired, 429 limited |
| anything else | — | 404 `not_found`; wrong method → 405 |

### Bounds, and how each is proven

| Bound | Value | Test |
|---|---|---|
| Body | 2 KiB | oversize → 413 `too_large` |
| Header block / fields | 16 KiB / 50 | enforced in `_enforce_header_limits` |
| Concurrency | 16 total, 4 per IP | `_ConnectionGate`, try-acquire (no unbounded thread spawn) |
| Requests per IP | 30/min | third request with a limit of 2 → 429 |
| TLS handshake | 3 s | socket timeout before `wrap_socket` |
| Request read | 5 s | socket timeout after handshake |

`ThreadingHTTPServer`'s unbounded thread-per-connection is **not** used; `process_request` refuses a
connection when the gate is full instead of spawning a thread that waits.

### The no-network guarantee, re-checked now that a listener exists

```
py -3 -m pytest tests/integration/test_no_network.py tests/failure/test_no_network.py -q
```
Still green, and the full suite passes with the listener module present. A new test pins the stronger
property at the object level:

- `test_constructing_the_receiver_opens_no_socket` — monkeypatches `socket.socket`, builds a
  `MobileReceiver`, and asserts **zero** sockets were created and `running is False`.

That closes the open item from increment 1: "no socket exists while the receiver is disabled" is now a
tested claim rather than an intention. Whether the *application* auto-starts it remains increment 5's
responsibility, and will be tested there.

### Pairing flow across the wire

`submit` → `wait_for_decision(60 s)` → issue token → `finish`. Verified end to end:

1. Approved → 200 with a `rps1_` token, the device registered, and the token authenticating against
   the registry.
2. Denied → 403 `pairing_denied`, registry unchanged.
3. No window → 410 `pairing_expired`.
4. Undecided after the wait → 202 `{"state": "waiting"}`, which the phone retries with the same body.

### Known limitation, recorded

When a device is already paired and the window was not opened with "Replace current device", the
registry raises `DeviceAlreadyPaired`. The current mapping is `409 pairing_used`, which is
**semantically imprecise**. It is unreachable through the dialog (which owns that choice), and it is
listed as an open item for Phase 4, where the contract fixtures must give it its own typed code. This
was deliberately not invented here, because adding a protocol error code is an Article XV change.

### Gates after the change

| Gate | Command | Result |
|---|---|---|
| Lint | `py -3 -m ruff check .` | `All checks passed!` |
| Types | `py -3 -m mypy src` | `Success: no issues found in 155 source files` |
| Money-path guard | `py -3 scripts/check_no_float.py` | `no-float gate: PASS` |
| Full suite | `py -3 -m pytest -q` | **1574 passed in 95.12s** |

1574 = 1450 existing + 57 + 24 + 17 + 2 + 15 + 9 seam tests.

---

## Increment 5a — controller, dialog, and localization

### What was added

| File | Purpose |
|---|---|
| `src/rawpos/mobile/controller.py` | Owner-gated lifecycle: enable, disable, pair, approve, deny, revoke |
| `src/rawpos/mobile/protocol.py` | `build_pairing_uri` — the QR payload from §4.2 |
| `src/rawpos/ui/mobile_scanner_dialog.py` | Receiver toggle, QR, countdown, code, approve/deny, revoke |
| `src/rawpos/localization/__init__.py` | 30 `mobile.*` keys in **both** catalogs |
| `pyproject.toml` | `segno==1.6.6` pinned (A4, now actually used) |
| `tests/integration/test_mobile_controller.py` | 11 tests |
| `tests/gui/test_mobile_scanner_dialog.py` | 9 tests |

### Where the owner rule lives

A14 is enforced in the controller, not only by disabling buttons:

- `enable`, `begin_pairing`, `approve`, `deny`, `revoke` call `require_owner()` and raise
  `NotAdministrator` (the existing domain exception) for a cashier.
- `disable`/`stop` are **ungated**, so sign-out, recovery, and shutdown can always quiesce the
  listener (D22). A test flips the owner flag mid-session and proves both directions.

The dialog disables the same controls, but that is convenience; hiding a button is not the boundary.

### Two increment-4 gaps closed

1. **Codec selection is no longer a convention.** `enable()` reads the identity's recorded protection
   mode and builds the registry with `DpapiCodec` or `PlainCodec` accordingly.
2. **Pairing has a caller.** `begin_pairing` → QR → phone submits → `approve` issues the token.

### QR contract

`build_pairing_uri` emits `rawpos://pair?v=1&host=…&port=…&sid=…&secret=…&pin=…&did=…&name=…&exp=…`,
URL-encoded, with **no token and no business data**. A test asserts each field is present and that the
name is encoded.

### Localization

All 30 keys exist in `ar` and `en`. The Arabic copy is written but **not reviewed by a native
speaker** — recorded as an open item against Q22 rather than presented as final.

### Gates after the change

| Gate | Command | Result |
|---|---|---|
| Lint | `py -3 -m ruff check .` | `All checks passed!` |
| Types | `py -3 -m mypy src` | `Success: no issues found in 157 source files` |
| Money-path guard | `py -3 scripts/check_no_float.py` | `no-float gate: PASS` |
| Full suite | `py -3 -m pytest -q` | **1594 passed in 103.49s** |

1594 = 1574 + 11 controller + 9 dialog.

### Still not reachable

The dialog has **no caller**: there is no menu item, no startup hook, and no stop-on-sign-out wiring.
The receiver therefore cannot be started by a user, which is the correct state until increment 5b.

---

## Open items in this phase

1. **Android SAS vector** — the derivation from increment 2 must be reproduced by the Android client
   and checked against the pinned vector before Phase 4 contract tests.
2. **Android pin verification** — the client must compute the same SPKI pin and fail closed on mismatch.
3. **`device_already_paired`** — needs its own typed code in Phase 4 contract fixtures; currently mapped
   to `409 pairing_used`, which is imprecise but unreachable through the dialog.
4. **Application wiring (5b)** — menu entry, explicit enable, and stop on sign-out, recovery, and
   shutdown. Until then the dialog has no caller.
5. **Arabic review** — the 30 new keys are written but not reviewed by a native speaker (Q22).
6. **`waitress` decision** — pre-approved as the Phase 7 remedy if fuzzing fails; not adopted now.
7. **Temporary key file** — inherent to Python's TLS API; documented in `tls.py` and above.

Codec selection is no longer an open item: `MobileLinkController.enable` derives it from the
identity's recorded mode.
