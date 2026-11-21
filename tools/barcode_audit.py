"""Offline audit of shop barcode labels against a RawPOS barcode export.

Read-only and data-safe by design:

- It never writes to a database. It reads two CSV files and prints a summary.
- It prints **aggregates by default**. Raw barcode values appear only with ``--show-values``,
  because a barcode list is a sales history once it is joined to anything else.
- ``--json`` writes aggregate counts only, so the output can be committed to a public repository
  without publishing the shop's data.

Inputs
------
``labels.csv``
    As captured from the shop, one row per physical label::

        barcode,symbology
        6291041500213,EAN-13
        012345678905,UPC-A

    ``symbology`` is what the label artwork or the scanner reports. It is optional but it is the
    only way to answer "which formats does the shop use" — digits alone cannot distinguish a
    12-digit UPC-A from a 12-digit Code 128.

``--export`` (optional)
    A CSV exported from a copy of the shop database: one barcode per row, no other columns needed.
    Export it on the machine where the license is valid; the database key is licence-derived.

Usage
-----
    py -3 tools/barcode_audit.py labels.csv
    py -3 tools/barcode_audit.py labels.csv --export barcodes.csv --json audit.json
"""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

GTIN_LENGTHS = {8: "EAN-8", 12: "UPC-A", 13: "EAN-13", 14: "GTIN-14"}


@dataclass
class Label:
    barcode: str
    symbology: str = ""


@dataclass
class Summary:
    total: int = 0
    non_digits: list[str] = field(default_factory=list)
    lengths: Counter[int] = field(default_factory=Counter)
    invalid_check_digits: list[str] = field(default_factory=list)
    duplicates: list[str] = field(default_factory=list)
    symbologies: Counter[str] = field(default_factory=Counter)

    def as_json(self) -> dict[str, object]:
        return {
            "total": self.total,
            "non_digit_count": len(self.non_digits),
            "length_histogram": {str(k): v for k, v in sorted(self.lengths.items())},
            "invalid_check_digit_count": len(self.invalid_check_digits),
            "duplicate_count": len(self.duplicates),
            "symbologies": dict(sorted(self.symbologies.items())),
        }


def gs1_check_digit_ok(value: str) -> bool:
    """Validate the trailing GTIN check digit (EAN-8/12/13, GTIN-14, UPC-A)."""
    if not value.isdigit() or len(value) < 2:
        return False
    digits = [int(char) for char in value]
    check = digits[-1]
    body = list(reversed(digits[:-1]))
    total = sum(digit * (3 if index % 2 == 0 else 1) for index, digit in enumerate(body))
    return (10 - total % 10) % 10 == check


def upc_a_to_ean13(value: str) -> str:
    """Prepend the zero that turns a 12-digit UPC-A into its 13-digit GTIN form."""
    return "0" + value


def strip_one_leading_zero(value: str) -> str:
    return value[1:] if value.startswith("0") and len(value) > 1 else value


def read_rows(path: Path) -> list[list[str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        rows = [row for row in csv.reader(handle) if any(cell.strip() for cell in row)]
    if not rows:
        return []
    header = [cell.strip().lower() for cell in rows[0]]
    if header and header[0] in {"barcode", "code", "value", "gtin"}:
        rows = rows[1:]
    return rows


def load_labels(path: Path) -> list[Label]:
    labels: list[Label] = []
    for row in read_rows(path):
        barcode = row[0].strip()
        if not barcode:
            continue
        symbology = row[1].strip() if len(row) > 1 else ""
        labels.append(Label(barcode=barcode, symbology=symbology))
    return labels


def load_export(path: Path) -> list[str]:
    values: list[str] = []
    for row in read_rows(path):
        value = row[0].strip()
        if value:
            values.append(value)
    return values


def summarise(values: list[str], symbologies: list[str] | None = None) -> Summary:
    summary = Summary(total=len(values))
    seen: Counter[str] = Counter(values)
    for value in values:
        summary.lengths[len(value)] += 1
        if not value.isdigit():
            summary.non_digits.append(value)
            continue
        if len(value) in GTIN_LENGTHS and not gs1_check_digit_ok(value):
            summary.invalid_check_digits.append(value)
    summary.duplicates = sorted(value for value, count in seen.items() if count > 1)
    if symbologies:
        summary.symbologies.update(item or "(unrecorded)" for item in symbologies)
    return summary


def compare(labels: list[Label], export: list[str]) -> dict[str, int]:
    """Count how each label resolves against the database export.

    The 12/13-digit question is the point: a UPC-A stored as 12 digits and emitted by a scanner as
    13 (or the reverse) fails an exact lookup but is the same product.
    """
    exact_set = set(export)
    ean_set = {upc_a_to_ean13(value) for value in export if len(value) == 12}
    stripped_set = {strip_one_leading_zero(value) for value in export}

    result = {"exact": 0, "leading_zero_variant": 0, "missing": 0}
    for label in labels:
        value = label.barcode
        if value in exact_set:
            result["exact"] += 1
        elif value in ean_set or value in stripped_set:
            result["leading_zero_variant"] += 1
        else:
            result["missing"] += 1
    return result


def print_summary(title: str, summary: Summary, *, show: bool) -> None:
    print(f"== {title} ==")
    print(f"rows                 : {summary.total}")
    print(f"non-digit values     : {len(summary.non_digits)}")
    print(f"length histogram     : {dict(sorted(summary.lengths.items()))}")
    print(f"invalid check digits : {len(summary.invalid_check_digits)}")
    print(f"duplicate values     : {len(summary.duplicates)}")
    if summary.symbologies:
        print(f"symbologies          : {dict(sorted(summary.symbologies.items()))}")
    if show:
        for label, values in (
            ("non-digit", summary.non_digits),
            ("bad check digit", summary.invalid_check_digits),
            ("duplicate", summary.duplicates),
        ):
            if values:
                print(f"  {label}: {values}")
    print()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("labels", type=Path, help="CSV of physical labels: barcode[,symbology]")
    parser.add_argument("--export", type=Path, help="CSV of barcodes exported from the shop DB copy")
    parser.add_argument("--json", type=Path, help="write aggregate counts only to this file")
    parser.add_argument(
        "--show-values",
        action="store_true",
        help="print raw barcodes; off by default so nothing leaks into a log",
    )
    options = parser.parse_args(argv)

    labels = load_labels(options.labels)
    label_summary = summarise(
        [label.barcode for label in labels], [label.symbology for label in labels]
    )
    print_summary("labels", label_summary, show=options.show_values)

    payload: dict[str, object] = {"labels": label_summary.as_json()}

    if options.export is not None:
        exported = load_export(options.export)
        export_summary = summarise(exported)
        print_summary("database export", export_summary, show=options.show_values)
        resolution = compare(labels, exported)
        print("== resolution ==")
        for key, value in resolution.items():
            print(f"{key:21}: {value}")
        print()
        payload["export"] = export_summary.as_json()
        payload["resolution"] = resolution

    if options.json is not None:
        options.json.write_text(
            json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        print(f"aggregates written to {options.json}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
