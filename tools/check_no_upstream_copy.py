#!/usr/bin/env python3
"""阻止上游 AGPL 代码被提交。只看 git 跟踪的文件。

上游 dsh-go 整体是 AGPL-3.0,名称/Logo/形象也不在授权内。Maeve 只借鉴设计决策、
数值与交互语义,不复制任何源码或资源。这个脚本在提交前跑一遍,非 0 即拦。
"""
import pathlib
import subprocess
import sys

NEEDLES = ("Portions derived from PiPilot", "ccch1mneyyy", "package:dsh_client")
# 这几份文件的用途就是"点名上游"以声明合规:许可清单、规格里的红线说明、
# 以及专门检查上游痕迹的脚本。它们不含上游源码,放行。
ALLOW = {
    "tools/check_no_upstream_copy.py",
    "tools/maeve/check_compliance.py",
    "licenses/THIRD_PARTY_NOTICES.md",
    "SPEC.md",
    "flutter/UI_SPEC_HANDOFF.md",
}
EXTS = {".dart", ".kt", ".kts", ".md", ".xml", ".yaml", ".yml", ".sh", ".py"}

bad = []
for rel in subprocess.check_output(["git", "ls-files"], text=True).splitlines():
    if rel in ALLOW or pathlib.Path(rel).suffix not in EXTS:
        continue
    try:
        text = pathlib.Path(rel).read_text(encoding="utf-8", errors="ignore")
    except OSError:
        continue
    if any(n in text for n in NEEDLES):
        bad.append(rel)

print("上游代码被跟踪:\n  " + "\n  ".join(bad) if bad else "OK：无上游代码被跟踪")
sys.exit(1 if bad else 0)
