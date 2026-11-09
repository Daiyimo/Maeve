#!/usr/bin/env python3
"""Print the per-package derived table that ``docs/derived-rewrite-plan.md`` uses.

``derived-audit.py`` answers "how much of the tree is still upstream"; this script
answers "which package is left".  It reads a TSV written by ``derived-audit.py
--write`` and prints one Markdown row per source package, in derived-LOC order,
so the plan's section 3 table and its 读表要点 can be refreshed without
hand-counting.

Usage
-----
    python tooling/derived-audit.py --base 7cf7ff3 --write docs/derived-files.tsv
    python tooling/package-audit.py [docs/derived-files.tsv]

Package grouping matches the plan document: ``app:ui*`` collapses into ``app:ui``,
``app:cap/<family>`` and ``core:cap/<family>`` collapse into the family, and
``core:uistate/tools`` collapses into ``core:uistate``.  A package counts as
``done`` when none of its source files is still at or above 60% derived.

The script is read-only.
"""

from __future__ import annotations

import csv
import re
import sys
from collections import defaultdict
from pathlib import Path

HOT = 0.60


def collapse(pkg: str) -> str:
    """Fold sub-packages the way the plan document's table does."""
    if pkg.startswith("app:ui"):
        return "app:ui"
    if pkg.startswith("app:cap/"):
        return "app:cap/" + pkg.split("/")[1]
    if pkg.startswith("core:cap/"):
        return "core:cap/" + pkg.split("/")[1]
    if pkg.startswith("core:uistate"):
        return "core:uistate"
    return pkg


def source_package(path: str) -> str | None:
    m = re.match(r"core/src/main/kotlin/com/daiyimo/maeve/core/(.+)/[^/]+$", path)
    if m:
        return collapse("core:" + m.group(1))
    m = re.match(r"app/src/main/java/com/daiyimo/maeve/(.+)/[^/]+$", path)
    if m:
        return collapse("app:" + m.group(1))
    if re.match(r"app/src/main/java/com/daiyimo/maeve/[^/]+$", path):
        return "app:MaeveApp.kt"
    return None


def test_package(path: str) -> str | None:
    m = re.search(r"com/daiyimo/maeve/(core/[^/]+(?:/[^/]+)*?)/[^/]+$", path)
    if m:
        return collapse(m.group(1).replace("/", ":", 1))
    m = re.search(r"com/daiyimo/maeve/(cap/[^/]+(?:/[^/]+)*?)/[^/]+$", path)
    if m:
        return collapse("app:" + m.group(1))
    m = re.search(r"com/daiyimo/maeve/(ui(?:/[^/]+)*?)/[^/]+$", path)
    if m:
        return collapse("app:" + m.group(1))
    m = re.search(r"com/daiyimo/maeve/([^/]+)/[^/]+$", path)
    if m:
        return "app:" + m.group(1)
    return None


def main() -> int:
    tsv = Path(sys.argv[1] if len(sys.argv) > 1 else "docs/derived-files.tsv")
    rows = list(csv.reader(tsv.open(encoding="utf-8"), delimiter="\t"))[1:]

    files: dict[str, int] = defaultdict(int)
    loc: dict[str, int] = defaultdict(int)
    derived: dict[str, int] = defaultdict(int)
    hot: dict[str, int] = defaultdict(int)
    tests: dict[str, set[str]] = defaultdict(set)

    for derived_loc, group, path, line_count, _added, _deleted, ratio, _binary in rows:
        pkg = source_package(path)
        if pkg and group == "src":
            files[pkg] += 1
            loc[pkg] += int(line_count)
            derived[pkg] += int(derived_loc)
            if float(ratio) >= HOT:
                hot[pkg] += 1
        if group == "tests":
            owner = test_package(path)
            if owner:
                tests[owner].add(path)

    print(f"{'package':<24}{'files':>6}{'loc':>9}{'derived':>9}{'pct':>6}{'tests':>7}  state")
    for pkg in sorted(derived, key=lambda p: (-derived[p], p)):
        state = "done" if hot[pkg] == 0 else f"{hot[pkg]} hot"
        print(
            f"{pkg:<24}{files[pkg]:>6}{loc[pkg]:>9,}{derived[pkg]:>9,}"
            f"{derived[pkg] / loc[pkg]:>5.0%}{len(tests.get(pkg, ())):>7}  {state}"
        )

    print()
    print(f"source files: {sum(files.values())}  loc: {sum(loc.values()):,}  "
          f"derived: {sum(derived.values()):,}")
    core = sum(v for k, v in hot.items() if k.startswith("core:"))
    app = sum(v for k, v in hot.items() if k.startswith("app:"))
    done = sorted(p for p, n in hot.items() if n == 0 and p.startswith("core:"))
    print(f"hot source files: core {core}  app {app}")
    print(f"core packages done ({len(done)}): {' '.join(done)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
