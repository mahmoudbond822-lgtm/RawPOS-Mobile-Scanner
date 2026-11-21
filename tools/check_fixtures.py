"""Verify (or regenerate) the SHA-256 manifest for the shared protocol fixtures.

The fixtures are the contract between the Android client and the desktop receiver. If the two sides
build against different bytes, the mismatch shows up in a shop instead of in a build, so both sides
verify this manifest.

    py -3 tools/check_fixtures.py            # verify, non-zero exit on any mismatch
    py -3 tools/check_fixtures.py --write    # regenerate the manifest after an intended change

The check is deliberately dumb: every `.json` file except the manifest itself must be present,
parseable, declare `"v": 1`, and hash to the recorded value. No schema knowledge lives here — that
belongs to each side's contract tests.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "protocol" / "fixtures"
MANIFEST = FIXTURES / "manifest.json"


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fixture_files() -> list[Path]:
    return sorted(path for path in FIXTURES.glob("*.json") if path.name != MANIFEST.name)


def build_manifest() -> dict[str, object]:
    return {
        "version": 1,
        "files": {path.name: digest(path) for path in fixture_files()},
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="regenerate the manifest")
    options = parser.parse_args(argv)

    if not FIXTURES.is_dir():
        print(f"no fixtures directory at {FIXTURES}", file=sys.stderr)
        return 2

    problems: list[str] = []
    for path in fixture_files():
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            problems.append(f"{path.name}: not valid JSON ({error})")
            continue
        if not isinstance(payload, dict):
            problems.append(f"{path.name}: top level is not an object")
            continue
        if payload.get("v") != 1:
            problems.append(f"{path.name}: missing or wrong protocol version")

    if problems:
        for problem in problems:
            print(f"FAIL {problem}", file=sys.stderr)
        return 1

    expected = build_manifest()
    if options.write:
        MANIFEST.write_text(
            json.dumps(expected, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        print(f"manifest written: {len(expected['files'])} files (v1)")  # type: ignore[arg-type]
        return 0

    if not MANIFEST.is_file():
        print("FAIL manifest.json is missing; run with --write", file=sys.stderr)
        return 1
    recorded = json.loads(MANIFEST.read_text(encoding="utf-8"))
    recorded_files = recorded.get("files", {})

    for name, value in expected["files"].items():  # type: ignore[union-attr]
        if recorded_files.get(name) != value:
            problems.append(f"{name}: manifest hash does not match the file")
    for name in recorded_files:
        if name not in expected["files"]:  # type: ignore[operator]
            problems.append(f"{name}: listed in the manifest but not present")

    if problems:
        for problem in problems:
            print(f"FAIL {problem}", file=sys.stderr)
        return 1

    print(f"fixtures OK: {len(expected['files'])} files, manifest matches")  # type: ignore[arg-type]
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
