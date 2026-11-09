#!/usr/bin/env python3
"""M1 migration: rename the upstream package identifier to com.daiyimo.maeve.

Defaults to a dry run. Pass --apply to actually write files and move source
directories.

Scope is deliberately narrow: ONLY the Java/Kotlin package identifier and its
slash-path form are rewritten. Brand strings, class names (MikaApp, MikaLinks,
...), MCP tool names, resource names and every upstream attribution (including
NOTICE and tools/relay/NOTICE) are left untouched. Apache-2.0 section 4(c)/(d)
requires the original attribution to survive a fork, and renaming a class is a
separate, reviewable step.

The script is idempotent: a second run reports zero replacements / zero moves.
"""
from __future__ import annotations

import argparse
import os
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

OLD_DOTTED = "com.wangzan101.mika"
NEW_DOTTED = "com.daiyimo.maeve"
OLD_PATH = "com/wangzan101/mika"
NEW_PATH = "com/daiyimo/maeve"

# Never touch git internals, build output, the migration tooling, or Maeve's own
# documentation: those files deliberately quote the OLD package name as historical
# fact (ATTRIBUTION.md, docs/spec/bridge-v3.md, ADR-0005, the archived analysis reports).
# Rewriting them would falsify the change record. The script must stay idempotent
# after the docs have been added.
SKIP_PREFIXES = (
    ".git/", ".gradle/", ".kotlin/", ".idea/", "dist/",
    "docs/reference/", "docs/spec/", "docs/adr/", "tools/maeve/",
    "build/", "app/build/", "core/build/",
)
SKIP_DIR_NAMES = {"__pycache__", ".git"}
SKIP_FILES = {"SPEC.md", "ATTRIBUTION.md", "SECURITY.md", "rename_package.py"}

MAX_BYTES = 2 * 1024 * 1024


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def should_skip_file(path: Path) -> bool:
    r = rel(path)
    if r in SKIP_FILES or os.path.basename(r) in SKIP_FILES:
        return True
    if any(r.startswith(p) for p in SKIP_PREFIXES):
        return True
    if any(part in SKIP_DIR_NAMES for part in path.parts[:-1]):
        return True
    if path.suffix.lower() in {".apk", ".png", ".jpg", ".jpeg", ".webp",
                               ".gif", ".so", ".aar", ".jar", ".xz", ".gz",
                               ".zip", ".ttf", ".otf", ".keystore", ".jks"}:
        return True
    try:
        if path.stat().st_size > MAX_BYTES:
            return True
    except OSError:
        return True
    return False


def rewrite_text(apply: bool) -> tuple[int, list[str]]:
    changed: list[str] = []
    total_hits = 0
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or should_skip_file(path):
            continue
        try:
            raw = path.read_bytes()
            text = raw.decode("utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        hits = text.count(OLD_DOTTED) + text.count(OLD_PATH)
        if hits == 0:
            continue
        new = text.replace(OLD_DOTTED, NEW_DOTTED).replace(OLD_PATH, NEW_PATH)
        total_hits += hits
        changed.append(f"{rel(path)}  ({hits})")
        if apply:
            # Preserve original newline style and permissions.
            path.write_bytes(new.encode("utf-8"))
    return total_hits, changed


def package_dirs() -> list[Path]:
    out: list[Path] = []
    for path in ROOT.rglob("mika"):
        if not path.is_dir():
            continue
        r = rel(path)
        if any(r.startswith(p) for p in SKIP_PREFIXES):
            continue
        if not r.endswith(OLD_PATH):
            continue
        out.append(path)
    return sorted(out)


def move_dirs(apply: bool) -> list[str]:
    planned: list[str] = []
    for src in package_dirs():
        com_dir = src.parent.parent           # .../com
        dst_parent = com_dir / "daiyimo"
        dst = dst_parent / "maeve"
        planned.append(f"{rel(src)}  ->  {rel(dst)}")
        if not apply:
            continue
        dst_parent.mkdir(parents=True, exist_ok=True)
        if dst.exists():
            raise SystemExit(f"target already exists, aborting: {dst}")
        shutil.move(str(src), str(dst))
        try:
            src.parent.rmdir()               # .../wangzan101, if now empty
        except OSError:
            pass
    return planned


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--apply", action="store_true",
                    help="write changes (default: dry run)")
    args = ap.parse_args()

    hits, files = rewrite_text(args.apply)
    moves = move_dirs(args.apply)

    mode = "APPLIED" if args.apply else "DRY RUN"
    print(f"[{mode}] root = {ROOT}")
    print(f"[{mode}] {hits} replacements in {len(files)} files")
    for line in files[:20]:
        print("   ", line)
    if len(files) > 20:
        print(f"    ... and {len(files) - 20} more files")
    print(f"[{mode}] {len(moves)} source directories to move")
    for line in moves:
        print("   ", line)
    if not args.apply:
        print("\nNothing was written. Re-run with --apply to execute.")


if __name__ == "__main__":
    main()
