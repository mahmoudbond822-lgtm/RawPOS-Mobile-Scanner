# Desktop Integration Architecture

**Status:** proposed — pending owner approval
**Risk class:** highest. This is the only place where a new input source touches the live checkout.
**Related:** `repository-audit.md`, `system-architecture.md`, `networking-protocol.md`,
`../planning/risks-and-decisions.md` (D7, D8, D11)

---

## 1. Objective

Deliver an authenticated barcode string from the Android app into the **existing** checkout pipeline
with the same observable behavior as a USB keyboard-wedge scan, without changing any business rule and
without making the checkout service thread-unsafe.

"Same observable behavior" means:

- the same `CheckoutService` resolution semantics through an exact-barcode-only path;
- the same `Matched` / `NotFound` / `Deactivated` handling;
- the same status message, beep, refresh, low-stock and negative-stock warnings;
- the same audit, discount, hold/resume, and finalize behavior;
- the same localization;
- the same focus rules for the cashier's keyboard.

Mobile is an **additional input source**, not a parallel checkout.

---

## 2. Verified constraints that drive the design

| Constraint | Evidence |
|---|---|
| `CheckoutService` is pure Python but owns the mutable draft in memory | `services/checkout_service.py` |
| It has no locks, queue, or thread-affinity guard | source inspection |
| It uses the single SQLCipher connection through repositories | `repositories/base.py` |
| `CheckoutView` owns the scan field, wedge filter, status, beep, refresh | `ui/sales/checkout_view.py:333-460` |
| The wedge path is protected by four GUI tests | `tests/gui/test_checkout_ui.py:416-520` |
| `CheckoutView` is created lazily and destroyed on shell remount | `ui/main_window.py:213`, `ui/app_shell.py:402` |
| The service is `None` in recovery mode | `ui/app_shell.py:75` |
| Restore/backup use a quiescence gate | `services/database_protection_service.py:207-237` |
| `finalize` already brackets itself with `begin_critical_operation` | `checkout_service.py:finalize` |
| The app has no network code; two tests forbid sockets | `tests/*/test_no_network.py` |
| Barcodes are ASCII digits only; `resolve_scan` falls back to name search | migration `0004`, `checkout_service.py:523` |

---

## 3. Options considered

### Option A — Receiver thread calls `CheckoutService.resolve_scan()` directly

**Rejected.** Unsynchronized mutation of the draft, concurrent repository use on one SQLCipher
connection, and GUI widgets refreshed from the wrong thread. It might appear to work in a demo and fail
under a real cashier's timing. It also bypasses the view's status/beep/low-stock behavior, so mobile and
USB would diverge.

### Option B — Receiver posts a queued Qt signal; a GUI-thread bridge calls an additive `CheckoutView.submit_scan()`

**Recommended.** The network thread only parses/authenticates/dedupes and hands off. All checkout work
happens on the GUI thread through the existing view. The view gains one narrow method that reuses its
own rendering logic. Blast radius is one widget plus one new bridge object.

### Option C — Inject synthetic `QKeyEvent`s to imitate a wedge

**Rejected.** It would be indistinguishable from a physical scanner to the wedge filter, would fight
focus and text-edit behavior, could leak characters into dialogs, and would make support impossible
("was that USB, mobile, or a human?"). It also cannot return a truthful outcome to the phone.

### Option D — Write events into a queue drained by a UI timer

**Viable but inferior.** It works, but it adds latency (timer interval), invents a second dispatch
mechanism, and is harder to test deterministically than a queued signal. Option B is the same idea with
Qt's own event loop and a built-in thread boundary.

### Option E — A separate local HTTP endpoint that the desktop polls as a keyboard

**Rejected.** Same problems as C plus an unnecessary process boundary.

**Decision: Option B.**

---

## 4. Component design

```
┌────────────────────────── network thread ──────────────────────────┐
│ MobileScanServer (stdlib ThreadingHTTPServer + ssl)               │
│  - TLS handshake (pinned cert)                                     │
│  - route: POST /v1/scans, POST /v1/pair, GET /v1/health            │
│  - body/size/rate validation                                       │
│  - token authentication (hash lookup)                              │
│  - digit-only payload validation                                   │
│  - idempotency lookup (lock-protected)                             │
│  - blocking handoff with timeout                                   │
└──────────────────────────────┬─────────────────────────────────────┘
                               │ Qt queued signal (event + future)
┌──────────────────────────────▼─────────────────────────────────────┐
│ MobileScanController (QObject, GUI thread)                         │
│  - owns server lifecycle, pairing session, device registry, dedupe  │
│  - resolves the live CheckoutView via a provider (never caches it)  │
│  - brackets dispatch with protection.begin/end_critical_operation   │
│  - maps ScanResult → protocol status                                │
└──────────────────────────────┬─────────────────────────────────────┘
                               │ direct call, GUI thread only
┌──────────────────────────────▼─────────────────────────────────────┐
│ CheckoutView.submit_scan(text) -> ScanResult | None                │
│  - calls CheckoutService.resolve_barcode (exact-only)               │
│  - renders via the same helper as scan()                           │
│  - does NOT touch scan_edit, does NOT steal focus                  │
└──────────────────────────────┬─────────────────────────────────────┘
                               │
                    CheckoutService (unchanged)
```

### 4.1 New desktop modules (proposed)

```
src/rawpos/mobile/__init__.py          public surface
src/rawpos/mobile/protocol.py          constants, enums, request/response validation
src/rawpos/mobile/pairing.py           pairing session state machine
src/rawpos/mobile/devices.py           device registry (JSON, token hashes only)
src/rawpos/mobile/tls.py               certificate generation/loading, SPKI pin
src/rawpos/mobile/server.py            HTTP/TLS server, routing, rate limiting
src/rawpos/mobile/controller.py        Qt QObject, GUI-thread bridge, dedupe
src/rawpos/ui/mobile_scanner_dialog.py pairing/connection/device UI
```

No `mobile` module may import `PyQt6` except `controller.py` and the dialog. `protocol.py`,
`pairing.py`, `devices.py`, and the server core must stay unit-testable without a Qt application.

### 4.2 Modified files (proposed, Phase 2/5)

| File | Change | Risk |
|---|---|---|
| `src/rawpos/services/checkout_service.py` | add `resolve_barcode()` (exact-only) by extracting the shared tail of `resolve_scan()`; existing behavior unchanged | **approval required** |
| `src/rawpos/ui/sales/checkout_view.py` | add `submit_scan()`; extract `_render_scan_result()`; add an optional status chip/button | medium |
| `src/rawpos/ui/app_shell.py` | construct the controller; pass a live-view provider; expose the dialog action; rebind on remount | medium |
| `src/rawpos/app.py` | start/stop the controller around the shell and DB lifecycle | medium |
| `src/rawpos/ui/main_window.py` | add a "Mobile scanner" menu action/status indicator | low |
| `src/rawpos/localization/__init__.py` | add `mobile.*` keys in Arabic and English | low |
| `tests/*/test_no_network.py` | amend the guarantee: no outbound internet; listener off by default | **approval required** |
| `pyproject.toml` | add one QR-encoding dependency (`segno`) | **approval required** |

No change to repositories, migrations, pricing, or any other business service is expected. The one
expected service change is the **additive** `resolve_barcode()` described below; it must be approved
before work starts, and it must not alter `resolve_scan()` behavior for existing callers. If any other
service change becomes necessary, work stops and returns for approval.

---

## 5. The additive `CheckoutView` change

Today `scan()` does four things in one method: read the field, call the service, clear the field, and
render. The mobile path must not read or clear the field (the cashier may be mid-typing) and must not
steal focus.

Proposed shape (signatures, not implementation):

```python
def submit_scan(self, text: str) -> ScanResult | None:
    """Mobile-input entry point. GUI thread only. Never touches scan_edit or focus."""

def _render_scan_result(self, result: ScanResult) -> None:
    """The existing status/beep/refresh/low-stock block, shared by scan() and submit_scan()."""

def scan(self) -> None:
    """Unchanged behavior: read field → resolve → clear → render."""
```

`submit_scan()` calls `CheckoutService.resolve_barcode()` — the exact-barcode-only method — not
`resolve_scan()`. See `barcode-scanning.md` §4. Using `resolve_scan()` here would be a defect even with
digit-only input, because its name-search fallback can match a numeric product name.

Ordering rule: the existing `scan()` must keep clearing the field **before** rendering, exactly as it
does now. `_render_scan_result` must not depend on the field's contents. The four wedge tests and the
unknown-barcode beep test are the acceptance gate for the refactor.

Why not expose the result from `scan()` itself? Because changing its return type is a wider API change
and the mobile path still must not touch the field. A separate method is clearer and easier to test.

### 5.1 What `submit_scan()` intentionally does not do

- No navigation to the Sales module. The controller decides whether the view is eligible.
- No focus change. A cashier typing a tender amount must not lose the caret.
- No direct `scan_edit` manipulation.
- No new business logic. It calls the same service method and the same renderer.
- No swallowing of `DomainError`; it uses the view's existing `_refuse()` and returns `None`, which the
  controller maps to a rejected/error status.

---

## 6. Threading model and handoff

### 6.1 Rules

1. `MobileScanController` is created in `ApplicationShell` (GUI thread) and lives there.
2. `MobileScanServer` runs on its own daemon thread, started only when the receiver is enabled.
3. The server never imports or calls `CheckoutService`, `CheckoutView`, repositories, or `QWidget`.
4. The only cross-thread object is a Qt signal emitted by the controller
   (`scan_requested = pyqtSignal(object)`), connected with `Qt.ConnectionType.QueuedConnection`.
   Because the controller lives in the GUI thread, the slot always runs there.
5. The server blocks on a `concurrent.futures.Future` (or `threading.Event` + result box) with a
   deadline (2.0 s). On timeout it returns an honest `unknown`/`in_progress` response and leaves the
   dedupe entry pending; a retry with the same event id can collect the outcome.
6. The GUI slot asserts `QThread.currentThread() is QApplication.instance().thread()` before dispatch.
   A test calls the slot from a worker and expects a logged refusal, not a cart mutation.
7. The slot wraps the call in `protection.begin_critical_operation()` / `end_critical_operation()` so a
   restore cannot begin while a scan is touching the database. If `begin_critical_operation()` raises
   because a transition is already underway, the scan is rejected with `service_unavailable`.
8. Shutdown order: stop accepting → stop server thread (join with timeout) → close DB. The controller is
   stopped in `app.main()` before `context.database.close()`.

### 6.2 Live view resolution

The controller holds a **provider**, not a widget:

```python
view_provider: Callable[[], CheckoutView | None]
```

`ApplicationShell` sets it to return the current `CheckoutView` from `MainWindow._mounted_widgets`
(weak reference) only if:

- the Sales module is the current page, and
- the view is visible, and
- the view is not being destroyed, and
- `context.checkout_service is not None`.

This survives the language-switch remount because the provider is re-evaluated per scan. A cached widget
reference would go stale and, worse, could mutate a deleted Qt object.

### 6.3 Dispatch sequence

```
1. phone POST /v1/scans (event_id, device_id, barcode, format, scanned_at, v)
2. server: TLS → token hash → rate limit → digits/length check → dedupe lookup
3. controller.scan_requested.emit(request)          [queued]
4. server waits (≤2 s) on the request future
5. GUI slot:
     view = provider()
     if view is None → NO_ACTIVE_CHECKOUT
     protection.begin_critical_operation()
     result = view.submit_scan(barcode)
     protection.end_critical_operation()
     map result → status; store outcome in dedupe; complete future
6. server returns 200 with {event_id, status, message_code, server_time}
7. phone shows the truthful state (added / merged / not found / inactive / rejected)
```

If the desktop is on Login/Recovery/Activation, there is no view → `NO_ACTIVE_CHECKOUT`. If the Sales
module is mounted but not the current page, the same. The phone displays "RawPOS is not on the Sales
screen" and does not retry indefinitely.

---

## 7. Outcome mapping (desktop → protocol)

| `submit_scan` result | Protocol status | Phone message class |
|---|---|---|
| `Matched(merged=False)` | `added` | success: added to cart |
| `Matched(merged=True)` | `merged` | success: quantity increased |
| `NotFound` | `not_found` | warning: unknown barcode |
| `Deactivated` | `inactive` | warning: product deactivated |
| no live view | `rejected_no_checkout` | warning: RawPOS not on Sales |
| no open shift (admission) | `rejected_no_shift` | warning: open a shift |
| read-only license (admission) | `rejected_read_only` | warning: use USB or activate |
| other `DomainError` (raised) | `rejected_domain` | warning: see RawPOS |
| modal dialog open | `rejected_busy` | retryable warning |
| transition in progress | `rejected_unavailable` | retryable warning |
| duplicate event id | `duplicate` + cached status | same as the cached status |
| timeout / unknown | `unknown` | "check the desktop" — never success |

The server must not invent a status the desktop did not produce. In particular, `added` requires a
`Matched` result from `resolve_barcode` in this dispatch, not merely a 200.

---

## 8. Edge cases and required behavior

| Situation | Required behavior |
|---|---|
| Cashier typing in the tender field | Mobile scan adds to the cart; focus and text are untouched |
| Cashier editing a quantity cell | Mobile scan does not close or commit the editor |
| Two phones paired, both scanning | Not supported in v1 (D16): one active device. A second must replace the first on the desktop. Events are serialized on the GUI thread if they overlap |
| Same phone double-taps | Client cooldown + server idempotency; one add |
| Response lost after add | Phone retries same event id; server returns cached `added`, no second add |
| Phone retries with a new event id | Treated as a new physical scan; this is intentional for legitimate doubles |
| Desktop restarted mid-request | `server_instance_id` changes; the phone does **not** auto-replay; it shows "delivery uncertain". The cashier checks the cart and may rescan |
| DB restore starts | Scan either completes under a critical-op guard or is rejected; never touches a closed/replaced DB |
| Discount/approval or hold/resume modal open | Admission rejects with `rejected_busy`; no mutation behind the dialog |
| Language switched while connected | Shell remounts; provider returns the new view; no stale reference; connection survives |
| Sales screen closed/another module | `rejected_no_checkout` |
| App signing out | Server keeps running? **Decision:** stop the receiver on sign-out, because there is no authorized cashier. Restart after sign-in. |
| Read-only license | Admission rejects with `rejected_read_only`; no dispatch. USB/keyboard behavior is unchanged |
| No open shift | Admission rejects with `rejected_no_shift`; no dispatch. This avoids a misleading "added" while the desktop shows the shift prompt |
| Barcode with letters/URL | Rejected before `resolve_barcode`; never reaches any search fallback |
| 0-byte or oversized body | 400/413, no dispatch |
| Unknown device token | 401, no dispatch, rate-limited |
| Revoked device | 403 `device_revoked`; phone clears credentials and returns to pairing |

Two of these are worth calling out for approval:

- **Sign-out stops the receiver.** Alternative: keep it and reject with `no_active_cashier`. Stopping is
  safer and simpler; it also avoids scans arriving while the app is at the login screen.
- **Admission is stricter than the USB path.** Mobile refuses when Sales is inactive, the license is
  read-only, or no shift is open. This is a mobile admission policy, not a change to checkout business
  rules or USB behavior. It exists so the phone never claims "added" while the desktop cannot display
  or complete the sale. Requires owner approval (D17).

---

## 9. Quiescence, backup, restore, and shutdown

`DatabaseProtectionService` exposes:

- `begin_critical_operation()` — raises if a transition is already in progress; otherwise increments the
  active count;
- `end_critical_operation()`;
- `begin_transition()` — succeeds only when the active count is zero and no transition is running;
- `is_quiescent()` / `is_transitioning()`.

The mobile dispatch is short (a single exact barcode lookup plus a UI refresh), so bracketing it is
cheap and prevents a restore from starting underneath it. The controller must **not** call
`begin_critical_operation()` for the whole HTTP request (that would hold the gate for network time).

Restore/backup behavior:

- If a transition is running, `begin_critical_operation()` raises → reject with
  `rejected_unavailable` and `Retry-After: 5`.
- If a scan is running, `begin_transition()` returns False and the backup/restore path already handles a
  non-quiescent database (it refuses or retries); this preserves the existing safety model.
- The server is stopped before `database.close()` in `app.main()`.
- In recovery mode (`checkout_service is None`), the controller returns `rejected_unavailable` without a
  dispatch.

---

## 10. Security boundary at the integration point

The receiver is the only network-facing component. It must:

- accept only exact routes and methods;
- enforce `Content-Length` (≤ 4 KiB) and `Content-Type: application/json`;
- never serve files, directories, errors with stack traces, or version banners;
- authenticate before parsing business fields (but parse enough to reject cheaply);
- validate the barcode as `^[0-9]{1,64}$` (tunable minimum; see barcode spec);
- resolve only through the exact-barcode path, never through name search;
- cap concurrent connections (e.g. 8) and per-device rate (e.g. 10/s burst 20);
- log event id, device id, status, latency — never the token, pairing secret, or private key;
- treat the barcode as data: no SQL, no shell, no path use, no format string.

The controller must:

- re-validate the event (defense in depth) before dispatch;
- reject if the device is revoked between auth and dispatch;
- never trust the phone's timestamp for ordering or validity (diagnostics only);
- use a single-use pairing session with a short TTL;
- keep the device registry outside the business database, storing token **hashes** only.

See `security-design.md` for the threat model.

---

## 11. Testability

The design is testable without a phone:

- `protocol.py` and `pairing.py` are pure Python → unit tests.
- `devices.py` uses a temp file → unit/integration tests.
- `server.py` can be started on `127.0.0.1:0` with a test certificate → protocol tests via `http.client`.
- `controller.py` can be tested with a fake view provider and `qtbot.waitSignal` → GUI tests.
- `CheckoutView.submit_scan()` is tested directly against the real `CheckoutService` and a real test DB
  for added/merged/not-found/inactive + focus preservation.
- The no-network tests are amended to assert: receiver disabled by default; no outbound connection; the
  test listener binds loopback only.

Required new tests are listed in `../quality/test-matrix.md` (DESK-* and INT-* rows).

---

## 12. Risks

| Risk | Mitigation |
|---|---|
| CheckoutView refactor changes wedge behavior | Shared renderer extracted with no behavior change; the four wedge tests + beep test must pass before merge |
| Cached view reference becomes stale on remount | Provider callback + weak reference + visibility check; test a language-switch remount while connected |
| Network thread blocks forever on a busy GUI | 2 s deadline, honest `unknown` response, pending dedupe entry, retry can collect |
| Restore races a scan | Critical-op bracket + transition check; test with an injected slow scan |
| Sign-out leaves an open receiver | Stop server on sign-out; test |
| Multi-device ambiguity | Single active checkout; per-device dedupe; document the limitation |
| Name-search fallback abused | Exact-barcode-only service path (approved additive change) **and** digit-only validation; test with a numeric value that matches a product name |
| New dependency for QR | One small pure-Python library, pinned, reviewed; alternative documented |
| No-network guarantee erosion | Amend tests explicitly; keep outbound prohibition and default-off assertion |

---

## 13. Definition of Done for this integration

1. `CheckoutView.submit_scan()` exists and is covered by tests for all four outcomes.
2. `scan()` behavior is byte-for-byte equivalent in observable terms; existing GUI tests pass.
3. Mobile dispatch is provably on the GUI thread, with a test that fails if called off-thread.
4. Receiver is off by default; enabling it is explicit.
5. Restore/backup/shutdown interactions are tested.
6. No change to `CheckoutService`, pricing, stock, shifts, invoices, or schema.
7. The no-network tests are amended, documented, and still meaningful.
8. The full RawPOS suite is green, with the command and output recorded.
