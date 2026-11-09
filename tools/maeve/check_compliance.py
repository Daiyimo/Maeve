#!/usr/bin/env python3
"""CI guard for Maeve's attribution and third-party disclosure obligations.

Exits 1 with a list of problems. Deliberately dependency-free (stdlib only) so it can run
on any CI image before Gradle comes up.
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
problems: list[str] = []


def read(rel: str) -> str:
    p = ROOT / rel
    if not p.is_file():
        problems.append(f"missing file: {rel}")
        return ""
    return p.read_text(encoding="utf-8")


def check(cond: bool, message: str) -> None:
    if not cond:
        problems.append(message)


# 1. Apache-2.0 section 4(d): the original attribution must survive verbatim.
notice = read("NOTICE")
first_line = notice.splitlines()[0].strip() if notice else ""
check(bool(first_line) and "Copyright 2026 wangzan101" in notice,
      "NOTICE must keep the verbatim upstream attribution (Apache-2.0 4(d))")

# 2. License text and provenance record.
license_text = read("LICENSE")
check("Apache License" in license_text and "Version 2.0" in license_text,
      "LICENSE is not the Apache-2.0 full text")
read("ATTRIBUTION.md")

# 3. README must state the independent identity, attribution pointer and IP disclaimer.
readme = read("README.md")
check("independent" in readme, "README must describe Maeve as an independent project")
check("NOTICE" in readme, "README lost the attribution pointer")
check("not affiliated with or endorsed by HBO" in readme,
      "README lost the Westworld/HBO disclaimer")

# 4. Every `## ` heading in THIRD_PARTY_NOTICES must appear on the in-app licence screen.
notices = read("licenses/THIRD_PARTY_NOTICES.md")
headings = [l[3:].strip() for l in notices.splitlines() if l.startswith("## ")]
screen = read("app/src/main/java/com/daiyimo/maeve/ui/LicensesScreen.kt")
body = screen[screen.index("val ENTRIES"):] if "val ENTRIES" in screen else ""
names = re.findall(r'Entry\("([^"]+)"', body)
check(len(headings) >= 16, "THIRD_PARTY_NOTICES.md looks truncated")
for h in headings:
    check(h in names, f"third-party notice not shown in LicensesScreen: {h}")
check(bool(names) and names[0] == "Maeve",
      "LicensesScreen must show Maeve as its first entry")
check("上游版权方" in names,
      "LicensesScreen must keep an upstream-attribution entry (Apache-2.0 4(d))")
check("gpl-sources.tar.gz" in screen,
      "GPL source offer string lost from LicensesScreen")

# 5. Licence texts and the source offer must ship inside the APK.
assets = ROOT / "app/src/main/assets/licenses"
for name in ("Apache-2.0.txt", "GPL-2.0.txt", "GPL-3.0.txt", "LGPL-3.0.txt",
             "NOTICE.txt", "SOURCE_OFFER.md"):
    check((assets / name).is_file(), f"APK licence asset missing: assets/licenses/{name}")
check((ROOT / "NOTICE").read_text(encoding="utf-8") ==
      (assets / "NOTICE.txt").read_text(encoding="utf-8"),
      "assets/licenses/NOTICE.txt is not a verbatim copy of NOTICE")

# 6. Package identity must be consistent.
gradle = read("app/build.gradle.kts")
check(gradle.count('"com.daiyimo.maeve"') >= 2,
      "namespace / applicationId is not com.daiyimo.maeve")
check('rootProject.name = "Maeve"' in read("settings.gradle.kts"),
      "settings.gradle.kts rootProject.name is not Maeve")
check('versionName = "0.1.0-dolores"' in gradle, "release codename missing from versionName")

# 7. No residual upstream package identifier in CODE/CONFIG. Markdown is excluded on
#    purpose: ATTRIBUTION.md and SPEC.md must be able to describe the upstream package
#    name as historical fact.
allowed = {"tools/maeve"}
skip_dirs = {".git", "build", ".gradle", ".kotlin", ".idea", "__pycache__"}
code_exts = {".kt", ".kts", ".java", ".xml", ".aidl", ".py", ".go", ".mod",
             ".json", ".pro", ".gradle", ".properties", ".yml", ".yaml",
             ".sh", ".bat", ".txt"}
needles = ("com.wangzan101.", "com/wangzan101/")
for path in ROOT.rglob("*"):
    if not path.is_file() or path.suffix.lower() not in code_exts:
        continue
    if any(part in skip_dirs for part in path.parts):
        continue
    rel = path.relative_to(ROOT).as_posix()
    if any(rel == a or rel.startswith(a + "/") for a in allowed):
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    if any(n in text for n in needles):
        problems.append(f"residual upstream package identifier: {rel}")

# 8. No AGPL-licensed source may be vendored.
for marker in ("io.github.ccch1mneyyy", "package dsh_", "import 'package:dsh"):
    for base in (ROOT / "app", ROOT / "core"):
        for path in base.rglob("*"):
            if not path.is_file():
                continue
            try:
                if marker in path.read_text(encoding="utf-8"):
                    problems.append(f"possible AGPL-3.0 dsh-Go source copied: {path}")
            except (UnicodeDecodeError, OSError):
                pass

if problems:
    print("Compliance check FAILED:")
    for p in problems[:50]:
        print("  -", p)
    if len(problems) > 50:
        print(f"  ... and {len(problems) - 50} more")
    sys.exit(1)
print(f"Compliance check OK ({len(headings)} notices, {len(names)} licence-screen entries).")
