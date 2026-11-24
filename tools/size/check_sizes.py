#!/usr/bin/env python3
"""First-party source size ratchet.

Existing files may only shrink. New Kotlin/Dart source files must be at most
800 physical lines. The baseline is deliberately plain text for stable diffs.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import sys

MAX_NEW_LINES = 800
SOURCE_ROOTS = ("core/src", "app/src", "flutter/lib", "flutter/test")
SOURCE_SUFFIXES = {".kt", ".dart"}
BASELINE_HEADER = "# physical_lines\tpath"


def source_files(root: Path) -> dict[str, int]:
    counts: dict[str, int] = {}
    for source_root in SOURCE_ROOTS:
        base = root / source_root
        if not base.is_dir():
            continue
        for path in base.rglob("*"):
            if path.is_file() and path.suffix in SOURCE_SUFFIXES:
                relative = path.relative_to(root).as_posix()
                counts[relative] = len(path.read_text(encoding="utf-8").splitlines())
    return dict(sorted(counts.items()))


def load_baseline(path: Path) -> dict[str, int]:
    if not path.exists():
        return {}
    baseline: dict[str, int] = {}
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw or raw.startswith("#"):
            continue
        fields = raw.split("\t")
        if len(fields) != 2:
            raise ValueError(f"{path}:{number}: expected '<lines><TAB><path>'")
        count, name = int(fields[0]), fields[1]
        if count < 0 or not name or name in baseline:
            raise ValueError(f"{path}:{number}: invalid or duplicate baseline entry")
        baseline[name] = count
    return baseline


def violations(root: Path, baseline: dict[str, int]) -> list[str]:
    errors: list[str] = []
    for name, count in source_files(root).items():
        if name in baseline:
            cap = baseline[name]
            kind = "historical file grew"
        else:
            cap = MAX_NEW_LINES
            kind = "new file exceeds 800 lines"
        if count > cap:
            errors.append(f"{name}: {count} lines > {cap} ({kind})")
    return errors


def write_baseline(root: Path, path: Path, previous: dict[str, int]) -> list[str]:
    current = source_files(root)
    errors: list[str] = []
    if previous:
        errors = violations(root, previous)
        if errors:
            return errors
        # Keep deleted entries so recreating the same path cannot evade its cap.
        merged = dict(previous)
        for name, count in current.items():
            merged[name] = min(previous[name], count) if name in previous else count
        current = dict(sorted(merged.items()))
    lines = [BASELINE_HEADER] + [f"{count}\t{name}" for name, count in sorted(current.items())]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return []


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--baseline", type=Path, default=Path(__file__).with_name("baseline.txt"))
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--write", action="store_true", help="create or ratchet the baseline downward")
    mode.add_argument("--init", action="store_true", help="replace the baseline from the current tree (initial adoption only)")
    args = parser.parse_args(argv)
    root = args.root.resolve()
    baseline_path = args.baseline.resolve()
    if not args.init and not args.write and not baseline_path.is_file():
        print("size gate: baseline is missing; run --init for initial adoption", file=sys.stderr)
        return 2
    try:
        previous = load_baseline(baseline_path)
        if args.init:
            errors = write_baseline(root, baseline_path, {})
        elif args.write:
            errors = write_baseline(root, baseline_path, previous)
        else:
            errors = violations(root, previous)
    except (OSError, ValueError) as exc:
        print(f"size gate: {exc}", file=sys.stderr)
        return 2
    if errors:
        print("size gate failed:", file=sys.stderr)
        print("\n".join(errors), file=sys.stderr)
        return 1
    action = "initialized" if args.init else "updated" if args.write else "checked"
    print(f"size gate {action}: {len(source_files(root))} source files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
