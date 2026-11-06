#!/usr/bin/env python3
"""M2 first cut: replace the visible brand token in Android string resources.

Only XML text nodes are touched. URLs are stashed first so upstream document
links (which still point at wangzan101/Mika until M4 rewrites the docs/) are
not corrupted. Style names (Theme.Mika), Kotlin identifiers (MikaLinks,
MikaColors, MikaTheme), NOTICE attribution and about_author are out of scope
and deliberately untouched.

Usage:
    python3 tools/maeve/brand_strings.py --dry-run
    python3 tools/maeve/brand_strings.py
"""

from __future__ import annotations

import argparse
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "app" / "src" / "main" / "res"
OLD, NEW = "Mika", "Maeve"

URL = re.compile(r"https?://[^\s\"'<>]+")
# Text between a closing '>' and the next '<'. Character class matches newlines,
# so multi-line string bodies are covered without DOTALL.
TEXT = re.compile(r"(?<=>)([^<]*)(?=<)")


def stash_urls(text: str) -> tuple[str, list[str]]:
    urls: list[str] = []

    def hold(match: re.Match[str]) -> str:
        urls.append(match.group(0))
        return f"\x00URL{len(urls) - 1}\x00"

    return URL.sub(hold, text), urls


def restore_urls(text: str, urls: list[str]) -> str:
    for i, url in enumerate(urls):
        text = text.replace(f"\x00URL{i}\x00", url)
    return text


def fix_node(match: re.Match[str]) -> str:
    body, urls = stash_urls(match.group(1))
    return restore_urls(body.replace(OLD, NEW), urls)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    files = sorted(
        p
        for d in RES.iterdir()
        if d.is_dir() and d.name.startswith("values")
        for p in d.glob("strings*.xml")
    )
    total = 0
    for path in files:
        original = path.read_text(encoding="utf-8")
        updated = TEXT.sub(fix_node, original)
        # Count only brand tokens outside URLs; links are preserved verbatim.
        count = original.count(OLD) - sum(u.count(OLD) for u in URL.findall(original))
        if updated != original and not args.dry_run:
            path.write_text(updated, encoding="utf-8")
        total += count
        if count:
            print(f"{path.relative_to(ROOT).as_posix()}: {count}")

    mode = "would replace" if args.dry_run else "replaced"
    print(f"{mode} {OLD} -> {NEW}: {total} occurrences across {len(files)} resource files")


if __name__ == "__main__":
    main()
