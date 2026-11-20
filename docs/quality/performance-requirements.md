# Performance Requirements

**Status:** proposed targets. **No performance measurement has been performed.** Every number in this
document is a target or a measurement method, not a result.
**Related:** `../architecture/barcode-scanning.md` §9, `../architecture/networking-protocol.md` §5.6,
`qa-strategy.md`, `test-matrix.md`

---

## 1. Why these targets

A cashier judges a scanner by whether the line appears before they look back at the screen. The
dominant costs are camera exposure, ML Kit inference, the two-frame stabilizer, and the LAN round
trip. The desktop lookup is small. Targets are set so the p95 case still feels instant on a mid-range
phone and a normal shop Wi-Fi network, not on a flagship in a lab.

Hard requirements are separated from soft targets:

- **Hard:** zero duplicate cart mutations; zero success feedback before the desktop confirms; no ANR;
  no crash over a 30-minute session.
- **Soft (release gate unless waived):** latency, memory, battery, reconnect, CPU.

---

## 2. Proposed targets

### 2.1 Scan path

| ID | Metric | Definition | Target | Method |
|---|---|---|---|---|
| PERF-01 | Camera detection | Barcode fully in frame → analyzer emits a stable candidate | p50 ≤ 150 ms; p95 ≤ 300 ms | 240 fps video vs app log timestamps; 100 presentations per device |
| PERF-02 | Analyzer cost | `ImageAnalysis` callback start → ML Kit result | p95 ≤ 40 ms on a mid-range phone at 1280×720 | Instrumented benchmark, 500 frames |
| PERF-03 | Stabilization | First matching frame → second matching frame | p50 ≤ 100 ms; p95 ≤ 200 ms at 10 fps | App timestamps |
| PERF-04 | Validation + event | Candidate accepted → HTTP request ready | p95 ≤ 5 ms | App timestamps |
| PERF-05 | LAN RTT | Warm TLS request → response | p50 ≤ 30 ms; p95 ≤ 80 ms | 1,000 health requests, same subnet |
| PERF-06 | Cold TLS | TCP connect + handshake | p95 ≤ 200 ms | 100 cold connections |
| PERF-07 | Desktop dispatch | Receiver accepted → GUI slot entry | p95 ≤ 40 ms | Desktop monotonic timestamps |
| PERF-08 | Desktop resolve | `resolve_barcode()` entry → result | p95 ≤ 100 ms | Desktop timestamps; test DB |
| PERF-09 | Desktop render | Result → checkout refreshed → response ready | p95 ≤ 50 ms | Desktop timestamps |
| PERF-10 | End-to-end | Photon → cart line visible on desktop | p50 ≤ 400 ms; p95 ≤ 800 ms | 240 fps video with both screens; no clock sync; 100 scans |
| PERF-11 | Feedback | Desktop confirms → phone success feedback | p95 ≤ 100 ms | App timestamps after response |

**Stretch:** end-to-end p95 ≤ 600 ms with a 15 fps analyzer and a warm connection.

Do not compute end-to-end by subtracting phone and PC wall clocks. Measure each side with a monotonic
clock and correlate by `event_id`; or use a single 240 fps video containing both screens.

### 2.2 Reliability

| ID | Metric | Target | Method |
|---|---|---|---|
| REL-01 | Duplicate cart mutations | **0** in 10,000 scans (hard) | Fault-injection suite + replay tests |
| REL-02 | Duplicate event rate from held labels | ≤ 0.5% of presentations produce >1 event | 1,000 presentations, label held 1.5 s |
| REL-03 | Reconnect after Wi-Fi toggle | p95 ≤ 5 s to first accepted scan | 20 cycles |
| REL-04 | Uncertain-delivery notice | ≤ 3 s after a failed send | Fault injection |
| REL-05 | Receiver availability | No unhandled exception escapes the server thread in 8 h soak | Soak test |
| REL-06 | Pairing time | ≤ 30 s from QR scan to connected | Manual, 10 trials |
| REL-07 | Desktop restart recovery | Phone detects new `server_instance_id` ≤ 5 s after health poll | Manual/fault |

### 2.3 Resource use

| ID | Metric | Target | Method |
|---|---|---|---|
| RES-01 | Android PSS, scanning | ≤ 200 MB | `dumpsys meminfo`, 30 min |
| RES-02 | Android memory growth | ≤ 10% from minute 5 to minute 30 | Same |
| RES-03 | Android battery, scanning | ≤ 15% per hour | `dumpsys battery`/batterystats, 60 min |
| RES-04 | Android battery, idle background | ≤ 2% per hour | 60 min with camera off, connection kept |
| RES-05 | Camera stability | 0 camera errors; ≥ 95% of 10 s windows have frames; no crash/ANR | 30 min continuous |
| RES-06 | Desktop CPU, receiver idle | ≤ 1% of one core | `typeperf`, 10 min |
| RES-07 | Desktop CPU at 10 scans/s | ≤ 10% of one core; GUI stall ≤ 100 ms | 3,000 scans over 5 min from 2 simulated clients |
| RES-08 | Desktop memory | ≤ 50 MB additional over baseline | Process working set |
| RES-09 | Android APK size | ≤ 30 MB with bundled ML Kit | Release build |
| RES-10 | Android cold start | ≤ 1 s to Connection; ≤ 2 s to live preview on mid-range | `am start -W` + screen recording |

### 2.4 Desktop non-regression

| ID | Metric | Target |
|---|---|---|
| REG-P1 | Existing suite runtime | ≤ 180 s (baseline measured 134.97 s) |
| REG-P2 | Existing suite result | 1,450 passed (measured baseline; must not regress) |
| REG-P3 | GUI stall during a scan | ≤ 100 ms p95 |
| REG-P4 | Receiver idle wakeups | ≤ 5/s |

---

## 3. Measurement protocol

Every benchmark run records:

- RawPOS commit SHA and mobile app version/commit;
- Windows build, CPU, RAM, disk;
- Android model, API level, build fingerprint, battery level, thermal state;
- router model, Wi-Fi band, channel, signal strength, AP isolation setting;
- date/time, tester, script version;
- n, p50, p95, max, failure count;
- whether the phone was charging.

Rules:

- Nearest-rank percentiles. p95 requires n ≥ 100; otherwise report "insufficient sample".
- Warm and cold connections are reported separately.
- Three light levels for camera tests: bright shop, normal indoor, dim.
- At least one budget and one mid-range phone; a flagship is informative, not sufficient.
- A threshold becomes release-blocking after two clean baseline runs; before that it is a target.
- Results are stored as CSV + a short report under `docs/quality/results/<date>/` (created only when
  measurements exist). Do not fabricate a results file.

---

## 4. Budget allocation (why the targets are achievable)

At 10 fps, a frame arrives every ~100 ms. Two-frame stabilization costs ~100 ms. Camera + inference
~100 ms. Network ~30 ms warm. Desktop ~50 ms. Total ~380 ms p50. The p95 case adds a slow frame,
a slower inference, and a retransmit: ~800 ms. If the analyzer is raised to 15 fps, stabilization drops
to ~133 ms but battery rises; tune in Phase 6.

If the measured p95 exceeds 800 ms, the first levers are:

1. analyzer rate 12 → 15 fps;
2. crop to a center ROI (reduces pixels, risks clipping long codes);
3. lower analysis resolution to 960×540 on low-end devices;
4. two-frame stabilization with a stronger per-frame validity check;
5. warm connection pooling and TLS session reuse.

Each lever trades accuracy, battery, or compatibility; none is enabled by default without measurement.

---

## 5. What is not measured yet

Everything. Specifically:

- no Android device or emulator has been used;
- no camera/ML Kit benchmark exists;
- no receiver exists;
- no LAN latency has been measured in the shop;
- the 1,450-test baseline is a functional result, not a performance result;
- the shop's Wi-Fi, phone models, and lighting are unknown.

The first phase that can produce real numbers is Phase 3 (Android camera) and Phase 4 (protocol), using
the scripts defined in `qa-strategy.md`.
