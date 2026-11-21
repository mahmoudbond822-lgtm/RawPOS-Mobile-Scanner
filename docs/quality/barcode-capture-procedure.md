# Barcode Capture Procedure — A10 / A11 input

**Purpose:** collect the facts that A10 (exact-only UPC/EAN behaviour) and A11 (enabled format set)
cannot be answered without. Phase 3 must not start its camera work before this exists.
**Produces:** ~20 label rows and one barcode export, plus an aggregate report safe to publish.
**Never:** commits raw barcodes, modifies the shop database, or reads the live database in place.

---

## 1. Capture the labels

Take **20 physical labels** from the shop — not products from memory — chosen as follows:

| Group | How many | Why |
|---|---|---|
| Everyday retail items | 8 | The formats actually scanned most |
| Items with short internal codes | 3 | Answers Q11: are 1–2 digit codes real? |
| Items you suspect are UPC-A | 4 | Answers Q8: 12 or 13 digits in RawPOS |
| The longest barcode you can find | 1 | Answers Q10: is the 64-digit cap needed? |
| Weighed / variable-measure items | 2 | In-store codes behave differently |
| Alphanumeric or QR labels, if any | 2 | Determines whether non-digit payloads exist |

For each label record **two** things:

1. **`barcode`** — the string the **USB scanner emits**, not what your eye reads. Scan into Notepad with
   the RawPOS app closed. This is the number that must match the database, so it is the authoritative one.
2. **`symbology`** — printed on the label artwork or reported by the scanner: `EAN-13`, `EAN-8`,
   `UPC-A`, `UPC-E`, `ITF-14`, `Code 39`, `Code 128`, or `QR`.

Digits alone cannot distinguish a 12-digit UPC-A from a 12-digit Code 128, so a row without a
symbology answers A11 only partially. Write `unknown` rather than guessing.

Save as CSV, UTF-8:

```csv
barcode,symbology
6291041500213,EAN-13
012345678905,UPC-A
96385074,EAN-8
```

---

## 2. Export the database barcodes

The SQLCipher key is **derived from the license** (`derive_database_key(license.db_key, serial)`), and a
license is only accepted on the machine it was issued for. Therefore:

- A copied `rawpos.db` is readable **only** on the machine whose license is valid for it.
- Export the barcode column **there**, then bring the CSV over. Do not copy the license file to another
  machine and do not attempt to open the database elsewhere; both are licensing-integrity issues.

Run on the shop PC, with the RawPOS application **closed**, and only against a copy:

```powershell
# 1. copy the database (app closed)
Copy-Item "$env:LOCALAPPDATA\RawPOS\data\rawpos.db" "$env:USERPROFILE\rawpos-copy.db"

# 2. export one column, barcode only
python -c "import sqlite3,sys; from rawpos.app import build_context; ..."
```

The exact export command depends on how the shop install is deployed; the requirement is only that it
produces:

```csv
barcode
6291041500213
012345678905
```

One column, every product row, including inactive products (marked later, not filtered here). If the
export tool cannot be run, stop and say so — a guessed format set is worse than a missing one.

---

## 3. Analyse

The audit is offline and read-only:

```powershell
py -3 tools/barcode_audit.py labels.csv
py -3 tools/barcode_audit.py labels.csv --export barcodes.csv --json audit.json
```

It reports, without printing any barcode unless `--show-values` is passed:

- length histogram, non-digit count, duplicate values;
- **invalid check digits** — a label whose check digit fails is either a truncated scan or a
  non-GTIN internal code, and either way it must not drive a format decision;
- **resolution** against the export: `exact`, `leading_zero_variant`, `missing`.

`leading_zero_variant` is the A10 answer. If it is non-zero, the shop has the 12/13-digit
representation problem, and exact-only lookup without normalisation will report "not found" for
products it actually sells.

---

## 4. Record the result

Fill in `../planning/risks-and-decisions.md`:

| Question | Answered by |
|---|---|
| Q7 — which symbologies | `symbologies` in the label report |
| Q8 — UPC-A 12 or 13 digits | `resolution.leading_zero_variant` and the export length histogram |
| Q9 — duplicate GTINs for one product | `duplicate_count` in the export, then a spot check |
| Q10 — longest barcode | `length_histogram` maximum |
| Q11 — 1–2 digit internal codes | minimum length in the histogram |

Then sign A10 and A11. Until then, Phase 3 stays blocked — and the aggregate JSON is the only artifact
that may be committed.
