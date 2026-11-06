#!/usr/bin/env python3
"""M2 first cut: align unit-test copy assertions with the renamed brand.

The visible-copy change made 16 previously-passing tests fail: 15 asserted
Chinese/English copy containing "Mika", and UpdateCheckerTest asserted the
User-Agent prefix. This script fixes only those assertions.

Safeguards:
  * Only the ten affected test files are touched.
  * `Mika(?![A-Za-z])` leaves identifiers (MikaLinks, MikaColors, MikaTheme,
    MikaLinksTest, loginForMika, ...) untouched — a blind replace would break
    compilation.
  * URL fixtures are stashed first, so `wangzan101/Mika` test data survives.
  * `Mika/1.2.3` values are self-consistent test inputs (set + asserted), so
    rewriting them to Maeve is harmless and keeps the suite internally clean.

Usage:
    python3 tools/maeve/brand_tests.py
"""

from __future__ import annotations

import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
TEST = ROOT / "app/src/test/java/com/daiyimo/maeve"

FILES = [
    "about/MikaLinksTest.kt",
    "about/UpdateCheckerTest.kt",
    "capsule/CapsuleModelTest.kt",
    "task/AppKnowledgeWiringTest.kt",
    "task/SedimentationWiringTest.kt",
    "ui/ChatCopyTest.kt",
    "ui/MemoryViewModelTest.kt",
    "ui/ModelSetupViewModelTest.kt",
    "ui/PhoneSetupCopyTest.kt",
    "ui/StatusCopyTest.kt",
]

URL = re.compile(r"https?://[^\s\"'<>)]+")
TOKEN = re.compile(r"Mika(?![A-Za-z])")


def main() -> None:
    total = 0
    for rel in FILES:
        path = TEST / rel
        text = path.read_text(encoding="utf-8")
        urls: list[str] = []

        def hold(match: re.Match[str]) -> str:
            urls.append(match.group(0))
            return f"\x00URL{len(urls) - 1}\x00"

        safe = URL.sub(hold, text)
        safe, count = TOKEN.subn("Maeve", safe)
        for i, url in enumerate(urls):
            safe = safe.replace(f"\x00URL{i}\x00", url)
        if count:
            path.write_text(safe, encoding="utf-8")
        total += count
        print(f"{rel}: {count}")
    print(f"total: {total} brand tokens aligned in {len(FILES)} test files")


if __name__ == "__main__":
    main()
