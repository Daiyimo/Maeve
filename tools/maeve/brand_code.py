#!/usr/bin/env python3
"""M2 first cut: swap the visible brand token in Kotlin/Gradle literals.

Deliberately NOT a global rename. Class names (MikaLinks, MikaColors,
MikaTheme, MikaApp), the Theme.Mika style, every Log TAG, the NOTICE
attribution and about_author are left alone until the full M2 refactor.

Only the whitelisted user-visible literals below are changed, and each entry
must match exactly once or the script aborts rather than silently doing less.

Usage:
    python3 tools/maeve/brand_code.py
"""

from __future__ import annotations

import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
UI = "app/src/main/java/com/daiyimo/maeve/ui/"

EDITS: dict[str, list[tuple[str, str]]] = {
    "app/build.gradle.kts": [
        ('resValue("string", "app_name", "Mika Dev")',
         'resValue("string", "app_name", "Maeve Dev")'),
    ],
    UI + "WelcomeScreen.kt": [
        ("R.drawable.mika_launcher_art", "R.drawable.maeve_logo"),
    ],
    UI + "AboutScreen.kt": [
        ("R.drawable.mika_launcher_art", "R.drawable.maeve_logo"),
    ],
    UI + "components/Logo.kt": [
        ("R.drawable.mika_launcher_art", "R.drawable.maeve_logo"),
    ],
    "app/src/main/java/com/daiyimo/maeve/about/ApkUpdater.kt": [
        ('.setTitle("Mika ${release.version}")',
         '.setTitle("Maeve ${release.version}")'),
    ],
    "app/src/main/java/com/daiyimo/maeve/about/Diagnostics.kt": [
        ('const val HEADER = "Mika 诊断信息 / Mika diagnostics"',
         'const val HEADER = "Maeve 诊断信息 / Maeve diagnostics"'),
    ],
    "app/src/main/java/com/daiyimo/maeve/about/UpdateChecker.kt": [
        ('.header("User-Agent", "Mika/$currentVersion")',
         '.header("User-Agent", "Maeve/$currentVersion")'),
    ],
}


def main() -> None:
    for rel, pairs in EDITS.items():
        path = ROOT / rel
        text = path.read_text(encoding="utf-8")
        for old, new in pairs:
            count = text.count(old)
            if count != 1:
                raise SystemExit(f"{rel}: expected exactly 1 match for {old!r}, got {count}")
            text = text.replace(old, new)
        path.write_text(text, encoding="utf-8")
        print(f"updated {rel}")
    print(f"done: {len(EDITS)} files")


if __name__ == "__main__":
    main()
