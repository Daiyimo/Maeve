# Fork lineage and change log

Maeve is a derivative work of **Mika**. This file exists to satisfy Apache-2.0 §4 and to
make the lineage impossible to miss. It is intentionally kept next to `LICENSE` and `NOTICE`.

## Lineage

```
wangzan101/Mika            upstream, Apache-2.0, Copyright 2026 wangzan101
        │
        ▼
Daiyimo/Mika               working fork, base commit bbc8922a (2026-11-06)
        ▼
Daiyimo/Maeve              this repository
```

On 2026-11-06 the published git history was **rewritten** to a single initial
snapshot plus the four Maeve commits, so the repository lists only its maintainer as a
contributor. All commits up to and including `0c92fc39` are collapsed into root commit
`bccdcda6`, whose tree is exactly `bbc8922a`'s tree. The pre-rewrite history is kept on the
local, unpublished branch `backup/full-history-before-rewrite`.

```bash
git log --oneline             # one snapshot + Maeve commits, all by Daiyimo
git diff bccdcda6..HEAD       # everything Maeve changed since the snapshot
git rev-parse HEAD^{tree}     # 643211eb… at rewrite time (a later docs-only commit changes it)
```

**This does not remove Apache-2.0 attribution.** `NOTICE` keeps
`Mika / Copyright 2026 wangzan101` verbatim, `LICENSE` is the unmodified upstream text,
and the in-app licence screen still names Mika as the upstream project. Collapsing commit
history changes who appears as a *commit author*, not who owns the copyright.

## Apache-2.0 §4 statements

| § | Requirement | How Maeve complies |
|---|---|---|
| 4(a) | Give recipients a copy of the License | `LICENSE` is the unmodified Apache-2.0 text from upstream. |
| 4(b) | Modified files carry prominent change notices | See the notice below. The bulk rename is mechanical and is declared here once, with a reproducible diff. |
| 4(c) | Retain copyright, patent, trademark and attribution notices | All upstream notices are retained; `NOTICE` keeps `Mika / Copyright 2026 wangzan101` verbatim. |
| 4(d) | Retain attribution notices from `NOTICE` | `NOTICE` keeps the original two lines first, then an addendum. Nothing was removed or rewritten. |
| 6 | No trademark / product-name grant | No Mika logo, icon, GIF, slogan or product name is used as Maeve's own branding. |
| 5 | Contributions | No CLA/DCO in upstream; contributions are inbound = outbound under Apache-2.0. |

### Notice on modified files (§4(b))

> **This file has been changed from the original Mika source.** Maeve renamed the Java/Kotlin
> package to `com.daiyimo.maeve`, changed the Gradle coordinates, repository URLs and version,
> and is in the process of reworking the visual system, the connection layer and the capability
> set. The complete, per-file change record is the git history: run
> `git diff bccdcda6..HEAD` (or `git log --follow -- <file>`) from the repository root.
> The mechanical rename is reproducible via `python tools/maeve/rename_package.py --apply`.

Files with hand-written (non-mechanical) changes additionally carry a short header comment.
A per-file header was **not** injected into all ~1,440 mechanically renamed files: doing so
would have altered generated sources, AIDL and XML with inconsistent comment syntax. This
central notice plus the preserved diff is the declaration of record.

## What changed at M1

| Area | Change |
|---|---|
| Product name / slogan | Mika → **Maeve**; slogan *She runs the loop. You keep your screen.* |
| Release identity | `versionCode 38 / 0.1.37` → `versionCode 1 / 0.1.0-dolores`; `rootProject.name = "Maeve"` |
| Package | `com.wangzan101.mika` → `com.daiyimo.maeve` in 1,442 files (9,230 replacements) plus 8 source directories |
| Upstream URLs | `MikaLinks.kt` and `ApkIntegrity`'s derived whitelist now point at `Daiyimo/Maeve`; `tools/relay/go.mod` module path updated; CLI and sandbox script paths follow the new package |
| Repository docs | New `README.md`, `FORK.md`, `CONTRIBUTING.md`, `SECURITY.md`, `docs/spec/`, `docs/adr/`, CI templates |
| Attribution | `NOTICE` addendum; third-party notices augmented (ML Kit was previously undisclosed); GPL/LGPL texts mirrored into the APK |
| Formatting | None — upstream source was otherwise left intact to keep the diff reviewable |

## Deliberately not changed (yet)

- Class names such as `MikaApp`, `MikaLinks`, `MikaAccessibilityService`, resource names and
  `mika_*` MCP tool names. Renaming them is a separate, reviewable step (M3 keeps the old
  tool names as deprecated aliases).
- The 10 translated READMEs and ~17 `docs/*.md` pages that still carry upstream URLs.
  They will be rewritten together with the README, because editing them triggers the
  `updateReadme` generated block, the 10-language `translations.py check` and the prompt
  golden snapshots. See `docs/spec/migration.md`.
- Upstream media (`docs/media/*`) — replaced by new Maeve artwork in M2, not reused.
- The archived reports in `docs/reference/` — they describe the upstream project and keep
  its original names on purpose.

## License of this file

Apache-2.0, same as the repository. This document is engineering bookkeeping, not legal
advice; re-check trademark status before any commercial use.
