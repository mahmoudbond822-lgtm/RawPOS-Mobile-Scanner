# Phase 2 Approval Packet — Desktop receiver and secure pairing

**Status:** prepared 2026-11-20 for owner signature. Nothing in this document is approved until §7 is
signed.
**Purpose:** give the owner one readable page per pending decision, so Phase 2 can be authorized or
refused in a single pass.
**Authority:** `risks-and-decisions.md` §6 remains the authoritative approval register. This packet
re-states the same items; it does not change any status.
**Related:** `implementation-roadmap.md` (Phase 2), `../architecture/networking-protocol.md` §4/§9/§10,
`../architecture/security-design.md` §7/§10, `../architecture/desktop-integration.md`.

Labels used throughout: **VERIFIED** (observed in a file or a measurement), **PROPOSED** (not yet
binding), **ASSUMED** (unverified, see `risks-and-decisions.md` §7).

---

## 1. Where the project stands

| Fact | Value | Evidence |
|---|---|---|
| Phase 1 | delivered, gates green | `../phase-1/build-evidence.md`, `../phase-1/scope-compliance.md` |
| This repository | `main` at `b1e332f`; tree clean when this packet was prepared. The §10 corrections are uncommitted | `git status --porcelain`, `git log` |
| RawPOS | clean tree, `HEAD 797167ac…`, unchanged by this project | `git status`, `git rev-parse HEAD` |
| RawPOS baseline suite | 1,450 passed / 134.97 s | `README.md` §Measured baseline (**VERIFIED**, not re-run for this packet) |
| Approvals signed | A2, A3, A4, A9, A13 (A15 recorded from the Phase 1 authorization) | `risks-and-decisions.md` §6 |
| Phase 2 blocking approvals | **A5, A6, A7, A8** | `implementation-roadmap.md` Phase 2 "Depends on" |
| Prerequisites now on the checklist | **A16** (RawPOS write authorization), **A17** (dependency locking or recorded deferral) | `risks-and-decisions.md` §6, §7 below |

Phase 2 is estimated at **2 person-weeks** and produces **no business behavior**: it authenticates a
phone and issues a token. It does not touch checkout, pricing, stock, shifts, invoices, or the DB schema.

### What Phase 2 will build (bounded)

Under RawPOS `src/rawpos/mobile/`: `protocol.py`, `pairing.py`, `devices.py`, `tls.py`, `server.py`,
`controller.py` (skeleton, no checkout); plus `src/rawpos/ui/mobile_scanner_dialog.py`, `app.py` /
`app_shell.py` wiring, and `segno` pinned in `pyproject.toml`.

### What Phase 2 will explicitly not do

No camera, no barcode logic, no `POST /v1/scans`, no `GET /v1/context`, no `CheckoutService` or
`CheckoutView` change, no `resolve_barcode()` implementation, no DB migration, no USB/keyboard change,
no Android permission, no push, no merge to `main`, no release artifact.

---

## 2. A5 — Pairing requires desktop approval and one active device

**Question:** may a phone become trusted only after a human on the desktop approves it, and may only one
phone be active per desktop?

**Recommendation:** yes to both. **PROPOSED** (`D3`, `D16`).

**What would be approved — exact scope**

1. The desktop opens a **120 s, single-use** pairing window by explicit user action.
2. The QR carries host, port, SPKI pin, desktop id, and an independent 256-bit pairing secret. It carries
   no token and no business data. **VERIFIED** in `networking-protocol.md` §4.2.
3. The phone submits the secret over pinned TLS. The desktop shows the **device name** and a **6-digit
   comparison code** derived by HMAC-SHA-256 over `pairing_secret | pairing_id | device_id |
   client_nonce`, reduced to 6 digits.
4. The owner/cashier taps **Approve**. Only then is a 256-bit token issued, returned once, and stored as a
   domain-separated SHA-256 verifier.
5. Deny, expiry, reuse, and attempt conflicts fail closed with `403`, `410`, `409`, `429` respectively.
   **VERIFIED** in `networking-protocol.md` §4.3–§4.4.
6. Exactly one active device. Pairing a second requires **Replace current device**; the old token is
   revoked immediately. Revoked devices keep a name/timestamp record only. **VERIFIED** §4.5.

**Why the approval tap is not optional.** The QR is visible on a screen in a shop; a photograph of it
plus the secret is a complete pairing credential. The tap and the human-compared code are the only
defence against a photographed QR. **ASSUMED** AS7 (the shop accepts one extra tap) is still unconfirmed.

**Alternatives and why they were rejected**

| Alternative | Rejected because |
|---|---|
| Auto-approve on a valid secret | A photographed QR grants a full scanner token with no human in the loop |
| Manual host + code entry, no QR | Violates the requested workflow; worse UX for a cashier |
| Multiple simultaneous phones | Ordering and "which phone is feeding the cart" become ambiguous; deferred |
| Skip the 6-digit code | The code is what makes the approval meaningful for a *named* device |

**Blast radius if refused:** Phase 2 cannot issue a token. Pairing degrades to a shared secret in a QR,
which is not shippable. The safe fallback per `D9` is to drop mobile scanning entirely.

**How it will be verified (unit + integration, no device needed)**

- Secret comparison is constant-time; expired, used, and conflicting attempts each return the typed error.
- SAS code matches a fixed test vector.
- A Python `http.client` + `ssl` client pairs over loopback TLS with pin verification and receives a token.
- Wrong pin, reused secret, denied approval, and a second concurrent phone each fail closed.
- Registry contains hashes only; logs contain no secret, token, or code.

**Rollback:** revoke the device; disable the receiver; delete `%LOCALAPPDATA%\RawPOS\mobile\`. No DB
state exists to unwind.

---

## 3. A6 — DPAPI-protected key and registry storage

**Question:** is Windows DPAPI acceptable as the storage protection for the TLS private key and the
device registry, with a user-only ACL fallback?

**Recommendation:** yes, with the two failure modes disambiguated (see the open point below).
**PROPOSED** (`D12`).

**What would be approved**

- TLS private key: `CryptProtectData` (current-user scope) through a small `ctypes` wrapper, at
  `%LOCALAPPDATA%\RawPOS\mobile\identity.dpapi`.
- Registry: `%LOCALAPPDATA%\RawPOS\mobile\devices.json`, atomic write, strict schema, **token hashes
  only**, same DPAPI blob or a user-only ACL.
- Pairing state in memory only. Token plaintext never persisted, never logged.
- A corrupt or unreadable registry fails **closed** — receiver off, re-pair required, never "trust all".
- `ConfigStore` stays non-secret (enabled flag, port, interface, desktop id only). **VERIFIED** in
  `security-design.md` §7.1.
- No new dependency: DPAPI is an OS facility. No schema change: state lives outside `rawpos.db`.

**Open point requiring an explicit yes/no** — the current text describes two different situations with
two different answers, and this is where a security bug would hide:

| Situation | Documented behavior |
|---|---|
| DPAPI **unavailable at first use** | Fall back to a user-only ACL file and show a warning (`security-design.md` §7.1) |
| DPAPI **unprotect fails later** | Receiver stays off; do not fall back to plaintext (`security-design.md` §11 table) |

Recommendation: keep both as written, and add that the fallback is a **one-way** choice recorded in
config — once a key is stored under ACL fallback, a later DPAPI availability does not silently migrate
it. Owner should confirm or reject this rule.

**Alternatives:** `config.json` (rejected — explicitly non-secret); plaintext key with ACL only
(rejected — weaker, and a copy of the folder plus another user account is enough); Windows Credential
Manager (rejected — awkward for a multi-KB key blob); a sealed third-party library (rejected — new
dependency for an OS facility).

**Blast radius if refused:** the key and registry need *some* protection. Refusing DPAPI means ACL-only,
which is acceptable but weaker, and must then be stated in the docs instead of presented as a fallback.

**How it will be verified:** round-trip protect/unprotect; simulated `CryptProtectData` failure;
corrupt registry file; registry directory not writable; assert the on-disk file contains no token and no
readable key material.

**Rollback:** delete the folder; the next enable regenerates an identity and requires re-pairing. This is
recorded in the docs as a known operational cost, because the registry is not covered by the RawPOS
backup.

---

## 4. A7 — Port 47653 and the Private/LocalSubnet firewall rule

**Question:** may the receiver open inbound TCP **47653** on the shop PC, Private profile, LocalSubnet
only?

**Recommendation:** yes. **PROPOSED** (`D6`). **This is the only item that changes the machine's actual
network exposure**, which is why it is separate from A3.

**What would be approved**

- One inbound TCP listener on port **47653**, configurable locally.
- Bound to a **selected private interface address** — never wildcard, public, VPN, link-local, or
  loopback (loopback is allowed in test mode only).
- Windows Firewall: inbound rule for that port, **Private profile only**, remote address **LocalSubnet**.
  The app **shows instructions**; it does not silently elevate and never creates a Public rule.
- No UPnP, no port forwarding, no public DNS, no mDNS, no cloud relay. **VERIFIED** in
  `networking-protocol.md` §9.
- Off by default; starts only by an explicit action in a signed-in session; stops on sign-out, recovery
  mode, and shutdown.

**Technical notes the owner should see**

- 47653 lies in the IANA **registered** range (1024–49151), not the ephemeral range. It is chosen to be
  memorable and unlikely to collide, but it is **not verified as free on the shop PC**. Phase 2 must add
  an explicit port-conflict path: bind failure shows the error and the configured alternative, and never
  silently picks a different port (a silent change would invalidate the paired endpoint).
- **ASSUMED** AS4 (the shop can open one inbound port on the Private profile) and AS2 (no client
  isolation on the shop Wi-Fi) remain unverified. Guest Wi-Fi with client isolation is documented as
  unsupported.
- The receiver is intended for the shop LAN, not a public network. A Windows machine on a network
  classified **Public** gets no rule by design; the UI must say so instead of failing mysteriously.

**Alternatives:** ephemeral/dynamic port (rejected — pairing and firewall instructions must be stable);
wildcard bind (rejected — exposes every interface including VPN); `netsh` auto-rule at first run
(rejected — silent elevation); mDNS discovery (rejected — new attack surface and a dependency).

**Blast radius if refused:** the phone cannot reach the desktop. The feature is not deliverable without
either this approval or a different transport authorization. There is no partial version.

**How it will be verified:** bind to a specific interface; bind failure on an occupied port; loopback
test mode; a handshake from a second machine on the same subnet; a connection attempt from the Public
profile is not answered because no rule exists; assert no outbound connection is ever made.

**Rollback:** disable the receiver, delete the firewall rule. Nothing else on the machine changed.

---

## 5. A8 — HTTP server hardening path

**Question:** may v1 start on stdlib `http.server.ThreadingHTTPServer` with a hardened handler, with
`waitress` approved in advance as the Phase 7 fallback if fuzzing fails?

**Recommendation:** yes. **PROPOSED** (`D19`).

**What would be approved**

- A custom `BaseHTTPRequestHandler` — never `SimpleHTTPRequestHandler`; an exact route table; 404
  otherwise; no directory listing.
- Strict method, content-type, content-length, header-count, and body-size checks; duplicate-key
  rejection; bounded JSON depth; socket timeouts; a generic `Server` header; redacted logging; every
  handler exception returns a minimal 500 with no traceback. **VERIFIED** in `security-design.md` §10.1.
- **Release gate:** either the Phase 7 fuzz suite passes, or `waitress` (pure Python, zero transitive
  dependencies) is adopted as one approved dependency. No silent swap; the decision is recorded.

**Two gaps in the current text that Phase 2 must close** (both are correctness issues, not scope):

1. `ThreadingHTTPServer` spawns **one unbounded thread per connection** by default. "Bounded threads" in
   the spec therefore requires an explicit `ThreadingMixIn` subclass with a fixed pool or a semaphore.
2. `POST /v1/pair` **blocks up to 60 s** waiting for the human decision. With unbounded threads that is a
   cheap thread-exhaustion vector; with a small bounded pool it can starve legitimate requests. Phase 2
   must pick a bound and prove the behaviour under a deliberate flood.

**Alternatives:** `waitress` now (rejected — a dependency before it is proven necessary, but pre-approved
as the fallback); a hand-written HTTP parser (rejected — more code and more risk); Qt `QTcpServer`
(rejected — manual HTTP); Flask/FastAPI (rejected — large transitive dependency trees).

**Blast radius if refused:** without an approved hardening path the receiver cannot be built at all, and
without the fallback clause a Phase 7 fuzz failure leaves no pre-approved remedy — which would stall the
project at the last gate.

**How it will be verified:** route inventory test (only `/v1/pair` and `/v1/health` exist in Phase 2, and
no route returns business data); oversize body; bad content-type; unknown method; malformed and
duplicate-key JSON; truncated request; header flood; slow client; concurrent flood against the thread
bound.

**Rollback:** the receiver modules are additive; deletion restores the pre-Phase-2 state exactly.

---

## 6. A14 — Owner-only pairing and revocation

**Question:** who may pair and revoke — owner only, or any signed-in cashier?

**Recommendation:** **owner only** for pairing and revocation; any signed-in user may enable the receiver
and connect an already-paired phone. **PROPOSED** (Q16).

**Verified feasibility (this matters for cost):** RawPOS already has exactly two roles and an admin
policy. `Role.OWNER` / `Role.CASHIER` exist in `src/rawpos/domain/accounts.py`, and
`AdminPolicy.is_admin(actor)` resolves administrator status from roles
(`src/rawpos/services/admin_policy.py`), already wired into the shell owner-only branches
(`src/rawpos/ui/app_shell.py`). **A14 therefore requires no new role model, no new permission system, and
no account schema change** — only that the pairing dialog is gated on `is_admin` and that `POST /v1/pair`
refuses when the current actor is not the owner.

If A14 is refused, the dialog and the endpoint must be reachable by any signed-in cashier, and the docs
must say so instead of implying otherwise.

**Blast radius:** a cashier could authorize an attacker-supplied QR into the till's cart. Low likelihood
in a one-cashier shop, high impact if a device is misused.

**Verification:** GUI test that a cashier sees the feature disabled or the dialog refuses; a direct API
pair attempt by a cashier session is refused; revocation is owner-only.

---

## 7. Two prerequisites that were missing from the checklist

These were found while preparing this packet. Both are now registered as items in
`risks-and-decisions.md` §6 and must be decided before Phase 2 can start, even with A5–A8 signed.

### A16 — Authorization to write inside the RawPOS repository

**Verified fact:** every Phase 2 deliverable listed in `implementation-roadmap.md` lives inside RawPOS —
`src/rawpos/mobile/*.py`, `src/rawpos/ui/mobile_scanner_dialog.py`, `app.py`, `app_shell.py`,
`pyproject.toml`. `AGENTS.md` §7 makes RawPOS **read-only** unless a phase explicitly authorizes a patch,
and no current approval does. `A15` covers Phase 1 only.

**Options**

| Option | Consequence |
|---|---|
| **A. Dedicated branch in RawPOS** (`feature/mobile-receiver`), no push, no merge without a separate approval | Tests run against the real repo, which Phase 2 requires; `main` stays clean and the branch is reversible by deletion. **Recommended.** |
| B. Patch series maintained in this repo under `desktop/`, applied later | Keeps RawPOS untouched, but Phase 2 tests cannot run in place, so the Phase 2 gates become unverifiable. |
| C. No write access | Phase 2 cannot be implemented. The project stops at Phase 3 (Android camera only). |

The same authorization must cover the **deliberate amendment of the no-network tests** already approved
in A3: `tests/integration/test_no_network.py` (T057) and `tests/failure/test_no_network.py` (T071)
monkeypatch `socket.socket`, `create_connection`, `getaddrinfo`. **VERIFIED** in the RawPOS repository.
Those tests must keep proving that core flows make no outbound call; the amendment is an explicit,
narrow inbound-listener exception, never deletion.

### A17 — Dependency locking, or an explicit deferral

Phase 1 recorded this as a deviation: *"No dependency locking. Recommended before Phase 2."*
(`../phase-1/scope-compliance.md` §5.5). **VERIFIED.**

Recommendation: enable Gradle dependency locking and verification for `android/` before Phase 2 adds any
dependency, and pin `segno` exactly in `pyproject.toml`. The Nuitka frozen-build test stays in Phase 8.
If the owner prefers, record an explicit deferral with the risk accepted instead of leaving it implicit.

---

## 8. Not decided here

These are Phase 3/4 gates and are intentionally excluded, so that signing §9 authorizes exactly Phase 2:

- **A10** exact-only UPC/EAN behavior and **A11** the enabled barcode format set — both need real shop
  labels (Q7–Q11); capture ~20 labels and audit a **copy** of the barcode DB in parallel, since that has
  lead time and does not require any approval.
- **A12** process-lifetime dedupe and uncertain delivery — Phase 4.
- **A15** Phase 1 authorization — delivered, and the register now records it (§10).

---

## 9. Signature block

Signing a row authorizes **only** that item, for Phase 2, as described above. Silence is not approval.

| # | Item | Decision | Owner signature | Date |
|---|---|---|---|---|
| A5 | Pairing approval tap + 6-digit code + 120 s single-use window + one active device | D3 / D16 | | |
| A6 | DPAPI-protected TLS key and registry; ACL fallback, including the one-way fallback rule in §3 | D12 | | |
| A7 | Inbound TCP 47653, Private profile, LocalSubnet only, with a port-conflict path | D6 | | |
| A8 | `http.server` + hardened handler, `waitress` pre-approved as the Phase 7 fallback | D19 | | |
| A14 | Owner-only pairing and revocation; any signed-in user may enable/connect | Q16 | | |
| A16 | RawPOS write authorization on a dedicated branch, including the narrow no-network test amendment | AGENTS §7 / A3 | | |
| A17 | Gradle dependency locking now — or explicit deferral with risk accepted | Phase 1 §5.5 | | |

**Phase 2 authorization itself:** ☐ authorized ☐ not authorized — owner: ______________ date: ______

---

## 10. Documentation corrections — applied 2026-11-20

Found while preparing this packet, and applied in the same change so the document set is internally
consistent.

1. **`../phase-1/scope-compliance.md`** claimed *"zero commits; HEAD has no revision"* while the repository
   has commit `b1e332f`. **Applied:** §3, §5.3, §6, and §7 now carry inline "superseded" pointers, and a new
   **§8** records the correction with the commit id, date, and what it does and does not establish. The
   body of the report was left as the point-in-time record rather than rewritten.
2. **`A15`** showed `☐` although Phase 1 was authorized and delivered. **Applied:** status is now
   **RECORDED — 2026-11-20**, sourced from the existing "Android Foundation Only" authorization and the
   Phase 1 evidence, with a note that it is not a new signature — so the register no longer contradicts
   the roadmap.
3. **`implementation-roadmap.md` Phase 2 "Depends on"** listed A3, A4, A5, A6, A7, A8 only. **Applied:** the
   line now separates approved (A3, A4) from pending (A5–A8) and adds **A14**, **A16**, and **A17** as
   prerequisites, pointing at this packet.

Two further edits were made for consistency: **A16** and **A17** were added to the approval checklist in
`risks-and-decisions.md` §6 (both unsigned, so no authority was granted), and this packet's §1 and §7 were
updated to match.

---

## 11. First five tasks after signature

Ordered so that the riskiest uncertainty is retired first and no business code is touched early.

1. **Amend the two no-network tests** (`T057`, `T071`) with the narrowest possible change, and prove the
   full suite still passes: `ruff check .`, `mypy src`, `py scripts/check_no_float.py`,
   `py -3 -m pytest -q` — expected **1,450 + new tests**, quoting the actual count.
2. **`protocol.py` + `devices.py`** with their unit tests: strict validation, typed errors, atomic registry
   write, hash-only storage. No listener yet.
3. **`tls.py`**: certificate generation, SANs, SPKI pin, DPAPI round-trip and fail-closed tests.
4. **`pairing.py` + `server.py`**: state machine, TTL, one-use, SAS vector, bounded threads, rate limits,
   `GET /v1/health`, `POST /v1/pair`. Prove a Python client pairs over loopback TLS; prove a wrong pin,
   expired secret, reused secret, and a denied approval all fail closed.
5. **`mobile_scanner_dialog.py` + wiring**: owner gating (A14), countdown, approve/deny, revoke, light/dark,
   RTL, focus return; receiver off by default, stopped on sign-out, recovery, and shutdown.

Phase 2's Definition of Done is unchanged: pairing works end-to-end with a test client, security tests
pass, **no checkout code is touched**, docs and both localization catalogs are updated, and evidence is
recorded in `../phase-2/` (to be created with the same structure as `../phase-1/`).
