"""Export the barcode column from a RawPOS database **copy** for the A10/A11 audit.

Read-only, and deliberately narrow:

- It refuses to open the path the application's config points at, because that is the live
  database. Audits run against a copy.
- It copies the file to a temporary sibling, opens *that*, reads one column, then deletes it. The
  file you point at is never opened, so it cannot be modified even by a SQLCipher WAL checkpoint.
- It prints a count and a length histogram, never barcode values.
- It uses the application's own licence-verified key path. It does not read, derive, or print any
  key, and it will refuse if the machine's licence is not valid for this database.

Run it from a machine where the licence that protects this database is valid. A copy of a database
from another machine cannot be opened, by design.

    py -3 tools/export_barcodes.py --db "$env:USERPROFILE\rawpos-copy.db" --out "$env:USERPROFILE\rawpos-barcode-audit\barcodes.csv"
"""

from __future__ import annotations

import argparse
import csv
import shutil
import sys
from collections import Counter
from pathlib import Path

BARCODE_QUERY = "SELECT barcode FROM product_barcode ORDER BY barcode"


def _live_database_path(config_path: Path) -> Path | None:
    from rawpos.infra.config import ConfigStore

    configured = ConfigStore(config_path).database_path
    return Path(configured) if configured else None


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db", type=Path, required=True, help="path to the database COPY")
    parser.add_argument("--out", type=Path, required=True, help="CSV to write (one column)")
    parser.add_argument(
        "--config",
        type=Path,
        help="app config.json holding the licence path; defaults to the standard location",
    )
    options = parser.parse_args(argv)

    if not options.db.is_file():
        print(f"not a file: {options.db}", file=sys.stderr)
        return 2

    try:
        from rawpos.app import build_context
        from rawpos.infra.paths import default_config_path
    except ImportError as error:
        print(f"cannot import rawpos: {error}", file=sys.stderr)
        print(
            "Run this from an environment where the RawPOS application is installed.",
            file=sys.stderr,
        )
        return 2

    config_path = options.config or default_config_path()
    live = _live_database_path(config_path)
    if live is not None and options.db.resolve() == live.resolve():
        print(
            "refusing: that is the live database. Copy it first and audit the copy.",
            file=sys.stderr,
        )
        return 3

    working = options.db.with_name(options.db.name + ".audit-tmp")
    for suffix in ("", "-wal", "-shm"):
        Path(str(working) + suffix).unlink(missing_ok=True)
    shutil.copy2(options.db, working)

    values: list[str] = []
    try:
        context = build_context(config_path)
        database = context.protection.open_database(path=working)
        try:
            rows = database.connection.execute(BARCODE_QUERY).fetchall()
        finally:
            database.close()
    except Exception as error:  # noqa: BLE001 - reported, not hidden
        print(f"could not read the copy: {error}", file=sys.stderr)
        print(
            "This usually means the licence on this machine is not valid for this database.",
            file=sys.stderr,
        )
        return 4
    finally:
        for suffix in ("", "-wal", "-shm"):
            Path(str(working) + suffix).unlink(missing_ok=True)

    values = [str(row[0]) for row in rows]
    histogram = Counter(len(value) for value in values)

    options.out.parent.mkdir(parents=True, exist_ok=True)
    with options.out.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["barcode"])
        for value in values:
            writer.writerow([value])

    non_digits = sum(1 for value in values if not value.isdigit())
    print(f"rows exported     : {len(values)}")
    print(f"length histogram  : {dict(sorted(histogram.items()))}")
    print(f"non-digit values  : {non_digits}")
    print(f"written to        : {options.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
