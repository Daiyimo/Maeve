#!/usr/bin/env python3
"""Historical one-off: sync documentation after the 2026-11-06 history rewrite.

This script is not part of the build, CI or any verification path. It produced the
rewrite described below; the templates and hashes are a record of that operation.
Do not run it against the current tree — `ATTRIBUTION.md` and `SPEC.md` are now maintained by
hand and the published root has moved since.

The published history was collapsed into one Apache-2.0-attributed baseline
snapshot (`bccdcda6`, tree identical to `bbc8922a`) plus the Maeve commits,
so Contributors shows only the maintainer. Every statement claiming the full
upstream commit history was preserved is now false and is corrected here.

Apache-2.0 section 4 statements are deliberately NOT touched: `LICENSE`,
`NOTICE`, `ATTRIBUTION.md`'s compliance table and the in-app licence screen keep the
upstream attribution. Rewriting commit authors does not transfer copyright.

Idempotent — an anchor already updated is skipped. Usage:
    python3 tools/maeve/sync_history_docs.py
"""

from __future__ import annotations

import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]


def rep(rel: str, pairs: list[tuple[str, str]]) -> None:
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    changed = 0
    for old, new in pairs:
        if old in text:
            text = text.replace(old, new, 1)
            changed += 1
        elif new in text:
            continue
        else:
            print(f"{rel}: anchor superseded, skipping: {old[:60]!r}")
    path.write_text(text, encoding="utf-8")
    print(f"{rel}: {changed} replacements")


REWRITE_SECTION = """## History rewrite — single contributor (2026-11-06 18:1x)

The user asked for Contributors to show only themselves and for all commits up to and
including `0c92fc39e29c8c72efb7390daf9fb6a54405e6e2` to be dropped. The 39 commits after
that point included 16 by `wangzan101` (pulled in by a merge of `upstream/main`), so a plain
truncation would still have shown two contributors. Per the user's choice, they were
collapsed into one baseline snapshot:

| Step | Result |
|---|---|
| Local backup | `backup/full-history-before-rewrite` keeps all 526 old commits locally; it is **never pushed**. |
| New root | `bccdcda6` — `git commit-tree bbc8922a^{tree}`, author `Daiyimo <auroral.8751@gmail.com>`, message names Mika / wangzan101 and points at `NOTICE`, `LICENSE`, `ATTRIBUTION.md` and the licence screen. |
| Replay | `git cherry-pick` of the four Maeve commits (`0fd46b7d`, `fa1667db`, `481e87b3`, `a40ee242`). |
| Content check | `git rev-parse HEAD^{tree}` = `643211ebcc2d…`, **identical** to the pre-rewrite tree. Not a single file changed. |
| Contributors | `git log --format='%an <%ae>'` now returns only `Daiyimo <auroral.8751@gmail.com>`. |
| Tag | `amoo-bridge-v1` deleted locally and on `origin`; it pointed at an upstream archive commit and would have kept old objects reachable. |
| `mika-local` remote | Removed, so old upstream objects cannot be fetched back by accident. The upstream repo itself is untouched. |
| Publish | `git push --force-with-lease origin main` — **destructive and irreversible**; anyone who cloned or forked before this point must re-clone or rebase. |

The old `Q6` checklist below records the **first** publish of the full history and is kept
as history; its commit hashes and tag no longer exist in the published repository.

> Compliance reminder: collapsing git history changes who appears as a *commit author*.
> It does **not** remove the Apache-2.0 §4 duty to keep `NOTICE`, `LICENSE`, `ATTRIBUTION.md` and
> the in-app `Mika` licence entry. Those files are unchanged.
"""


def main() -> None:
    rep("ATTRIBUTION.md", [
        (
            "Daiyimo/Mika               working fork, 523 commits, tag amoo-bridge-v1\n"
            "        │                  base commit bbc8922a (2026-11-06)",
            "Daiyimo/Mika               working fork, base commit bbc8922a (2026-11-06)",
        ),
        (
            """The full upstream history was preserved rather than re-initialised:

```bash
git log --oneline | wc -l     # 523 at the time of the M1 cut
git tag                       # amoo-bridge-v1
git diff bbc8922a..HEAD       # everything Maeve changed
```

`git blame` therefore still reaches the original authors. That is deliberate: blame is the
most durable form of attribution.""",
            """On 2026-11-06 the published git history was **rewritten** to a single initial
snapshot plus the four Maeve commits, so the repository lists only its maintainer as a
contributor. All commits up to and including `0c92fc39` are collapsed into root commit
`bccdcda6`, whose tree is exactly `bbc8922a`'s tree. The pre-rewrite history is kept on the
local, unpublished branch `backup/full-history-before-rewrite`.

```bash
git log --oneline             # one snapshot + Maeve commits, all by Daiyimo
git diff bccdcda6..HEAD       # everything Maeve changed since the snapshot
git rev-parse HEAD^{tree}     # 643211eb… — identical to the pre-rewrite tree
```

**This does not remove Apache-2.0 attribution.** `NOTICE` keeps
`Mika / Copyright 2026 wangzan101` verbatim, `LICENSE` is the unmodified upstream text,
and the in-app licence screen still names Mika as the upstream project. Collapsing commit
history changes who appears as a *commit author*, not who owns the copyright.""",
        ),
        (
            "> `git diff bbc8922a..HEAD` (or `git log --follow -- <file>`) from the repository root.",
            "> `git diff bccdcda6..HEAD` (or `git log --follow -- <file>`) from the repository root.",
        ),
    ])

    rep("README.md", [
        (
            """This repository is at **M1 (skeleton)**: the upstream history is preserved, the package has
been renamed to `com.daiyimo.maeve`, and the compliance/attribution layer is in place.
The visual system (M2), the connection hub and bridge v3 (M3), and release hardening (M4)
are not finished yet. Nothing here is a released binary.""",
            """This repository is at **M2 (interaction layer)**: the published history starts from a single
Apache-2.0-attributed baseline snapshot, the package is `com.daiyimo.maeve`, and the
compliance/attribution layer is in place. See [ATTRIBUTION.md](ATTRIBUTION.md) for lineage.
The full M2 visual system, the connection hub and bridge v3 (M3), and release hardening
(M4) are not finished yet. Nothing here is a released binary.""",
        ),
    ])

    rep("SPEC.md", [
        (
            "> **目标仓库**：`E:\\project\\daiyimo\\Maeve` → `https://github.com/Daiyimo/Maeve.git`（526+ 提交，`main` 与 tag `amoo-bridge-v1` 已推送；`mika-local` 仅作本地上游镜像保留）",
            "> **目标仓库**：`E:\\project\\daiyimo\\Maeve` → `https://github.com/Daiyimo/Maeve.git`（**历史已重写为 5 个提交、单一贡献者**：基线快照 `bccdcda6` + 4 个 Maeve 提交；`main` 已强制推送，旧 tag `amoo-bridge-v1` 与 `mika-local` 已移除；见 `ATTRIBUTION.md`）",
        ),
        ("🔶 **本地已完成**：523 提交历史、`com.daiyimo.maeve`", "✅ **已完成并已推送**：`com.daiyimo.maeve`"),
        ("；**公开 push 未执行** |", "；历史已重写为单快照，见 `ATTRIBUTION.md` |"),
        (
            "git fetch mika-local --tags                           # 523 commits + amoo-bridge-v1",
            "git fetch mika-local --tags                           # M0 时的做法;该 remote 已于历史重写时移除",
        ),
    ])

    rep("docs/spec/migration.md", [
        (
            "| Preserve upstream history | Fetched locally from `E:\\project\\daiyimo\\Mika`: **523 commits**, tag `amoo-bridge-v1`, base `bbc8922a`. No re-init, no squashing. |",
            "| Upstream history | Initially fetched from `E:\\project\\daiyimo\\Mika` (523 commits, base `bbc8922a`). On 2026-11-06 the published history was rewritten to one baseline snapshot `bccdcda6` + four Maeve commits so Contributors shows only the maintainer. Pre-rewrite history is on the local, unpublished `backup/full-history-before-rewrite`. See `ATTRIBUTION.md` and \"History rewrite\" below. |",
        ),
        (
            "git log --oneline | wc -l                    # >= 523\ngit tag                                      # amoo-bridge-v1",
            "git log --oneline | wc -l                    # short single-author history\ngit tag                                      # empty by design",
        ),
        (
            "- [x] `git ls-remote origin` returned **no refs** → remote was empty, so all 526 commits\n      of upstream history could be pushed unchanged (no re-init, no squashing).",
            "- [x] `git ls-remote origin` returned **no refs** → remote was empty at first publish,\n      so the full 526-commit history could be pushed unchanged. **Superseded later the\n      same day** by the history rewrite (see \"History rewrite\" below).",
        ),
        (
            "- [x] `git push -u origin --all && git push origin --tags` → `main` at `a438e2fc`,\n      tag `amoo-bridge-v1` pushed; `origin/main` is now the upstream of `main`.",
            "- [x] First publish: `git push -u origin --all && git push origin --tags` → `main` at\n      `a438e2fc`, tag `amoo-bridge-v1`. **Both objects were later replaced** by the\n      history rewrite; `origin/main` remains the upstream of `main`.",
        ),
    ])

    migration = ROOT / "docs/spec/migration.md"
    text = migration.read_text(encoding="utf-8")
    if "## History rewrite — single contributor" not in text:
        migration.write_text(text.rstrip() + "\n\n" + REWRITE_SECTION, encoding="utf-8")
        print("docs/spec/migration.md: appended History rewrite section")
    else:
        print("docs/spec/migration.md: rewrite section already present")


if __name__ == "__main__":
    main()
