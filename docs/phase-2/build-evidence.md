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

## Open items in this phase

1. **Socket-absence guard test** — to be added with `server.py` (increment 4).
2. **Android SAS vector** — the derivation from increment 2 must be reproduced by the Android client
   and checked against the pinned vector before Phase 4 contract tests.
3. **Android pin verification** — the client must compute the same SPKI pin and fail closed on mismatch.
4. **Per-IP rate limiting** — absent until `server.py` (increment 4).
5. **`waitress` decision** — pre-approved as the Phase 7 remedy if fuzzing fails; not adopted now.
6. **Temporary key file** — inherent to Python's TLS API; documented in `tls.py` and above.

Registry protection is no longer an open item: `tls.py` provides the DPAPI path, and `devices.py` will
be wired to it in increment 4. Until that wiring exists the registry file is still stored as plain
bytes and **must not ship** in this state.
