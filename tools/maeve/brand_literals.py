#!/usr/bin/env python3
"""M2 first cut, part 2: visible brand literals hard-coded in product code.

`brand_strings.py` only covers Android resources. Several user-visible strings
are built directly in Kotlin — the About page title, foreground-service
notification titles, the idle capsule label, the MCP sign-in HTML, the MCP
client name and remote-task error messages. Leaving them as "Mika" makes the
app look half-renamed, and one of them (CapsuleContent.describe) breaks a test.

Still deliberately untouched (full M2 refactor): Log TAGs, the
`MikaHelperUidListener` Binder name, wake-lock tags, `Download/Mika/` paths,
model-facing prompts, self-check strings, comments and all class/type names.

Usage:
    python3 tools/maeve/brand_literals.py
"""

from __future__ import annotations

import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
MAIN = "app/src/main/java/com/daiyimo/maeve/"

EDITS: dict[str, list[tuple[str, str]]] = {
    MAIN + "capsule/CapsuleContent.kt": [
        ('const val IDLE = "Mika"', 'const val IDLE = "Maeve"'),
        ('else -> "Mika ${view.text}"', 'else -> "Maeve ${view.text}"'),
    ],
    MAIN + "capsule/CapsuleCardModel.kt": [
        ('CapsuleKind.Idle -> "Mika"', 'CapsuleKind.Idle -> "Maeve"'),
    ],
    MAIN + "capsule/CapsuleCardContent.kt": [
        ('ifBlank { "Mika" }', 'ifBlank { "Maeve" }'),
    ],
    MAIN + "ui/AboutScreen.kt": [
        ('Text("Mika", Modifier.padding(top = 12.dp)',
         'Text("Maeve", Modifier.padding(top = 12.dp)'),
    ],
    MAIN + "task/TaskService.kt": [
        ('.setContentTitle("Mika")', '.setContentTitle("Maeve")'),
        ('TaskState.Idle -> "Mika" to ""', 'TaskState.Idle -> "Maeve" to ""'),
    ],
    MAIN + "settings/McpServerStore.kt": [
        ('clientName = "Mika"', 'clientName = "Maeve"'),
        ('<h3>Mika: 登录成功</h3>', '<h3>Maeve: 登录成功</h3>'),
        ('<h3>Mika: 登录失败</h3>', '<h3>Maeve: 登录失败</h3>'),
        ('return to Mika.', 'return to Maeve.'),
        ('Return to Mika and try again.', 'Return to Maeve and try again.'),
    ],
    MAIN + "task/RemoteTaskHub.kt": [
        ('"Mika could not start the task on the phone. Check its status before trying again."',
         '"Maeve could not start the task on the phone. Check its status before trying again."'),
        ('"Mika could not start the task on the phone."',
         '"Maeve could not start the task on the phone."'),
        ('"Mika could not record this task\'s start. Check phone storage."',
         '"Maeve could not record this task\'s start. Check phone storage."'),
    ],
    MAIN + "intercept/AmooBridge.kt": [
        ('"Mika refused to start the task"', '"Maeve refused to start the task"'),
    ],
    MAIN + "settings/ProviderStore.kt": [
        ('private val userAgent: String = "Mika"', 'private val userAgent: String = "Maeve"'),
    ],
    MAIN + "ui/ChatCopy.kt": [
        ('const val APP_TITLE = "Mika"', 'const val APP_TITLE = "Maeve"'),
    ],
    MAIN + "ui/ChatDrawer.kt": [
        ('Text("Mika", style = MikaType.Title', 'Text("Maeve", style = MikaType.Title'),
    ],
    MAIN + "ui/McpScreen.kt": [
        ('ClipData.newPlainText("Mika", text)', 'ClipData.newPlainText("Maeve", text)'),
    ],
    MAIN + "ui/MessageMenu.kt": [
        ('ClipData.newPlainText("Mika", text)', 'ClipData.newPlainText("Maeve", text)'),
    ],
    MAIN + "cap/phone/shell/ShellService.kt": [
        ('private const val CLIP_LABEL = "Mika"', 'private const val CLIP_LABEL = "Maeve"'),
    ],
}


def main() -> None:
    total = 0
    for rel, pairs in EDITS.items():
        path = ROOT / rel
        text = path.read_text(encoding="utf-8")
        hits = 0
        for old, new in pairs:
            count = text.count(old)
            if count == 0:
                if new in text:
                    continue  # idempotent: already applied by an earlier run
                raise SystemExit(f"{rel}: no match for {old!r} — source drifted, review before editing")
            text = text.replace(old, new)
            hits += count
            total += count
        path.write_text(text, encoding="utf-8")
        print(f"{rel}: {hits}")
    print(f"total: {total} literals updated in {len(EDITS)} files")


if __name__ == "__main__":
    main()
