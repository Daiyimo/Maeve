#!/usr/bin/env python3
"""Remove the upstream author's self-promotion and re-attribute the About page.

* `about_author` -> 戴以沫 / Dai Yimo in all 10 UI languages. Upstream attribution is
  NOT deleted from the project: NOTICE (verbatim), ATTRIBUTION.md and the first
  `LicensesScreen` entry still name Mika / Wang Zan, which is what Apache-2.0
  §4(c)/(d) requires. Only the user-facing "made by" line changes.
* `chat_example_follow` (follow Wang Zan / @wangzan101 in every locale) and
  `chat_example_multi_step` (汽水音乐 VIP in zh locales) are removed from all
  locale resource files; `ChatCopy.examples` drops the two entries too.

Usage:
    python3 tools/maeve/clean_residue.py
"""

from __future__ import annotations

import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"

AUTHORS = {
    "values": "Made by Dai Yimo · © 2026",
    "values-zh": "由 戴以沫 开发 · © 2026",
    "values-b+zh+Hant": "由 戴以沫 開發 · © 2026",
    "values-ja": "制作: 戴以沫 · © 2026",
    "values-ko": "戴以沫 제작 · © 2026",
    "values-es": "Hecho por Dai Yimo · © 2026",
    "values-pt": "Feito por Dai Yimo · © 2026",
    "values-ru": "Автор: Dai Yimo · © 2026",
    "values-ar": "من إعداد Dai Yimo · © 2026",
    "values-in": "Dibuat oleh Dai Yimo · © 2026",
}

DROP_KEYS = ("chat_example_follow", "chat_example_multi_step")
AUTHOR_LINE = re.compile(r'(<string name="about_author">).*?(</string>)', re.S)


def main() -> None:
    for locale, author in AUTHORS.items():
        path = RES / locale / "strings_about.xml"
        text = path.read_text(encoding="utf-8")
        updated, count = AUTHOR_LINE.subn(lambda m: m.group(1) + author + m.group(2), text)
        if count != 1:
            raise SystemExit(f"{path}: expected 1 about_author, found {count}")
        path.write_text(updated, encoding="utf-8")
        print(f"{locale}: about_author -> {author}")

    removed = 0
    for path in sorted(RES.glob("values*/strings_chat.xml")):
        lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
        kept = [line for line in lines if not any(f'name="{key}"' in line for key in DROP_KEYS)]
        if len(kept) != len(lines):
            path.write_text("".join(kept), encoding="utf-8")
            removed += len(lines) - len(kept)
            print(f"{path.parent.name}: dropped {len(lines) - len(kept)} example strings")
    print(f"done: {len(AUTHORS)} author lines, {removed} example strings removed")


if __name__ == "__main__":
    main()
