#!/usr/bin/env python3
"""Measure how much of each tracked text file still comes from the baseline.

The rewrite plan uses ``derived_ratio = (current_loc - added_since_base) / current_loc``.
A file that has not changed since the baseline scores 1.0; a file written from
scratch scores 0.0.  The goal of the clean-room work is to drive every ratio
below 0.60 and then remove the attribution that the derived files require.

Usage
-----
    python tooling/derived-audit.py --write docs/derived-files.tsv
    python tooling/derived-audit.py --summary
    python tooling/derived-audit.py --check 0.60

The script is deliberately read-only except for ``--write``.  It uses the
repository's first commit as the baseline, like the plan document does.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

DEFAULT_BASE = "7cf7ff3"
COLUMNS = ("derived_loc", "group", "file", "head_loc", "added", "deleted", "derived_ratio", "binary")


@dataclass(frozen=True)
class Row:
    path: str
    group: str
    loc: int
    added: int
    deleted: int
    binary: bool

    @property
    def derived_loc(self) -> int:
        return max(0, self.loc - self.added)

    @property
    def derived_ratio(self) -> float:
        return self.derived_loc / self.loc if self.loc else 0.0


def git(*args: str) -> str:
    result = subprocess.run(
        ["git", *args], check=False, capture_output=True,
        text=True, encoding="utf-8", errors="replace",
    )
    if result.returncode:
        raise SystemExit(result.stderr.strip() or f"git {' '.join(args)} failed")
    return result.stdout


def resolve_rename(field: str) -> str:
    """Return the current path from a numstat path field.

    Git writes ``old => new`` or ``dir/{old => new}/file`` for detected
    renames.  Directories with a common prefix are compressed into the second
    form; both forms appear in this repository.
    """
    if " => " not in field:
        return field
    if "{" in field and "}" in field:
        before, rest = field.split("{", 1)
        inside, after = rest.split("}", 1)
        old, new = inside.split(" => ", 1)
        return f"{before}{new}{after}"
    return field.split(" => ", 1)[1]


def diff_stats(base: str) -> dict[str, tuple[int, int]]:
    """Map current path -> (added, deleted) against the baseline.

    Rename detection matters: without it a package rename looks like a brand-new
    file and every renamed file would falsely score 0.
    """
    out: dict[str, tuple[int, int]] = {}
    for line in git("diff", "--numstat", "-M", "--no-ext-diff", base, "--").splitlines():
        parts = line.split("\t", 2)
        if len(parts) != 3:
            continue
        added, deleted, field = parts
        path = resolve_rename(field)
        try:
            a, d = int(added), int(deleted)
        except ValueError:  # binary marker "-"
            a = d = 0
        prev = out.get(path, (0, 0))
        out[path] = (prev[0] + a, prev[1] + d)
    return out


def classify(path: str) -> str:
    parts = path.split("/")
    if path.startswith("flutter/"):
        return "flutter"
    if path.startswith("docs/"):
        return "docs"
    if path.startswith("eval/"):
        return "eval"
    if path.startswith("tools/"):
        return "tools"
    if path.startswith("licenses/") or "/assets/licenses/" in path:
        return "licenses"
    if path.startswith("core/src/main/resources/"):
        return "core-text"
    if path.startswith("app/src/main/res/"):
        return "res"
    if path.startswith("app/src/main/assets/"):
        return "assets"
    if path.startswith("app/src/main/cpp/") or path.startswith("app/src/main/aidl/"):
        return "native"
    if "/src/test/" in path or "/src/androidTest/" in path or "/src/testFixtures/" in path:
        return "tests"
    if path.startswith(("core/src/main/", "app/src/main/")):
        return "src"
    return "other"


def read_loc(root: Path, path: str) -> tuple[int, bool]:
    target = root / path
    try:
        data = target.read_bytes()
    except OSError:
        return 0, True
    if b"\0" in data[:8192]:
        return 0, True
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        text = data.decode("utf-8", "replace")
    return len(text.splitlines()), False


def collect(root: Path, base: str) -> list[Row]:
    stats = diff_stats(base)
    rows: list[Row] = []
    for path in git("ls-files", "-z").split("\0"):
        if not path:
            continue
        loc, binary = read_loc(root, path)
        added, deleted = stats.get(path, (0, 0))
        rows.append(Row(path, classify(path), loc, added, deleted, binary))
    rows.sort(key=lambda r: (-r.derived_loc, r.path))
    return rows


def write_tsv(rows: list[Row], target: Path) -> None:
    lines = ["\t".join(COLUMNS)]
    for row in rows:
        lines.append("\t".join((
            str(row.derived_loc),
            row.group,
            row.path,
            str(row.loc),
            str(row.added),
            str(row.deleted),
            f"{row.derived_ratio:.3f}",
            "yes" if row.binary else "no",
        )))
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {target} ({len(rows)} rows)")


def summary(rows: list[Row]) -> None:
    by_group: dict[str, list[Row]] = defaultdict(list)
    for row in rows:
        by_group[row.group].append(row)
    print(f"{'group':<12}{'files':>7}{'loc':>10}{'derived':>10}{'ratio':>8}")
    for group in sorted(by_group, key=lambda g: -sum(r.derived_loc for r in by_group[g])):
        group_rows = by_group[group]
        loc = sum(r.loc for r in group_rows)
        derived = sum(r.derived_loc for r in group_rows)
        ratio = derived / loc if loc else 0.0
        print(f"{group:<12}{len(group_rows):>7}{loc:>10}{derived:>10}{ratio:>8.1%}")
    loc = sum(r.loc for r in rows)
    derived = sum(r.derived_loc for r in rows)
    print(f"{'total':<12}{len(rows):>7}{loc:>10}{derived:>10}{derived / loc if loc else 0:>8.1%}")


def check(rows: list[Row], limit: float) -> int:
    pending = [r for r in rows if r.group != "licenses" and r.derived_ratio >= limit]
    pending.sort(key=lambda r: (-r.derived_loc, r.path))
    print(f"{len(pending)} files still at or above {limit:.0%} derived")
    for row in pending[:20]:
        print(f"  {row.derived_loc:>6}  {row.derived_ratio:>5.1%}  {row.path}")
    if len(pending) > 20:
        print(f"  ... {len(pending) - 20} more")
    return 1 if pending else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default=DEFAULT_BASE)
    parser.add_argument("--write", type=Path)
    parser.add_argument("--summary", action="store_true")
    parser.add_argument("--check", type=float, metavar="RATIO")
    args = parser.parse_args()

    root = Path(__file__).resolve().parent.parent
    rows = collect(root, args.base)
    if args.write:
        write_tsv(rows, args.write)
    if args.summary or not (args.write or args.check is not None):
        summary(rows)
    if args.check is not None:
        return check(rows, args.check)
    return 0


if __name__ == "__main__":
    sys.exit(main())
