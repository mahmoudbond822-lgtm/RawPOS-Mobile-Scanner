# Barcode Audit — 2026-11-21 (demo data only)

**Purpose:** attempt to answer A10 and A11 with real shop data.
**Result:** **inconclusive — no real data exists yet.** A10 and A11 remain unsigned.

---

## 1. What was audited

| Item | Value |
|---|---|
| Source | `%LOCALAPPDATA%\RawPOS-demo\backups\rawpos-20261121-054048.db` |
| Size, timestamp | 344 KB, 2026-11-21 05:40 |
| Config | `%LOCALAPPDATA%\RawPOS-demo\config.json` |
| Verification key | development key from `scripts/run_demo.ps1` |
| Method | `tools/export_barcodes.py` → `tools/barcode_audit.py` |
| BASE data | `RawPOS-demo\demo_products.json` |

This is the **demo installation**, not a shop deployment.

## 2. Measured result

```
rows exported     : 7
length histogram  : {10: 6, 11: 1}
non-digit values  : 0
```

The demo catalogue is synthetic and sequential: five products named in Arabic with barcodes
`1234567890`–`1234567894`, ten digits each. Two further rows come from the same seeding.

## 3. Why this answers nothing

| Question | Needs | Present? |
|---|---|---|
| Q7 / A11 — which symbologies | Physical labels with printed symbology | **No** — the sample labels are synthetic |
| Q8 / A10 — UPC-A 12 vs 13 digits | A product that exists as 12 digits in the DB and 13 on the scanner | **No** — no GTIN-shaped data at all |
| Q9 — duplicate GTINs per product | A real catalogue | **No** |
| Q10 — longest barcode | A real catalogue | **No** — max length here is 11 |
| Q11 — 1–2 digit internal codes | A real catalogue | **No** — minimum length here is 10 |

A 10-digit code is **shorter than any GTIN** (8, 12, 13, 14). The demo data therefore does not even
exercise the format logic, let alone describe a shop. No conclusion about the real shop may be drawn
from it — including "their codes are short", which would be an artefact of the seed script.

## 4. What the attempt did prove

The pipeline is real and works:

- the licence-verified key path opens an encrypted copy (development key supplied the way the app
  supplies it);
- `export_barcodes.py` refuses the configured live database and works on a temporary sibling;
- `barcode_audit.py` produces aggregates with no barcode values in the output.

When a real database exists, the export is one command. That uncertainty is now retired.

## 5. What would make A10/A11 answerable

1. **20 real labels**, scanned with the USB scanner, symbology recorded — see
   `barcode-capture-procedure.md`.
2. **One real database copy**, exported on a machine where its licence is valid.
3. `resolution.leading_zero_variant` non-zero, or a manual spot check of one UPC-A product, decides A10.

Until then, the honest position is: **A10 and A11 unanswerable, Phase 3 format work blocked.**

## 6. Consequence for the roadmap

Phase 3 depends on A11. With no shop data, only two outcomes are legitimate:

- **hold** Phase 3 until a pilot exists, or
- **approve the D14 defaults as defaults** — EAN-13, EAN-8, UPC-A, numeric Code 128 enabled;
  everything else disabled — with a mandatory pre-pilot verification against real labels.

The second is a decision about a default, not a claim about the shop. It must be recorded as such.
