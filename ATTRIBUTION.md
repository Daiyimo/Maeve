# Attribution and provenance

Maeve is an independent, Apache-2.0 Android project. Its codebase began from the Mika
snapshot recorded below, and Apache-2.0 requires the attribution in this file and in
`NOTICE` to be retained. This file records provenance and licence compliance; it is not a
claim that Maeve is affiliated with, endorsed by, or maintained as a fork of the upstream
project.

## Provenance

```text
wangzan101/Mika            upstream, Apache-2.0, Copyright 2026 wangzan101
        │
        ▼
Daiyimo/Mika               working snapshot, base commit bbc8922a (2026-11-06)
        ▼
Daiyimo/Maeve              this repository, maintained independently
```

The published history starts from a single baseline snapshot, root commit `7cf7ff3`
("Baseline: Maeve-old snapshot"), and every later Maeve change follows it. Earlier revisions
of this file named rewrite commits `bccdcda6` and `0c92fc39`; those objects are not part of
the current published history.

```bash
BASE="$(git rev-list --max-parents=0 HEAD)"
git log --oneline "$BASE"..HEAD   # one snapshot + every Maeve change
git diff "$BASE"..HEAD            # everything changed since the snapshot
git rev-parse HEAD^{tree}         # changes with every commit; compare within one run only
```

**This does not remove Apache-2.0 attribution.** `NOTICE` keeps
`Mika / Copyright 2026 wangzan101` verbatim, `LICENSE` is the unmodified Apache-2.0 text, and
the in-app licence screen still names Mika where the licence requires it. Collapsing commit
history changes who appears as a *commit author*, not who owns the copyright.

## Apache-2.0 §4 statements

| § | Requirement | How Maeve complies |
|---|---|---|
| 4(a) | Give recipients a copy of the License | `LICENSE` is the unmodified Apache-2.0 text. |
| 4(b) | Modified files carry prominent change notices | See the notice below. The bulk rename is mechanical and is declared here once, with a reproducible diff. |
| 4(c) | Retain copyright, patent, trademark and attribution notices | All upstream notices are retained; `NOTICE` keeps `Mika / Copyright 2026 wangzan101` verbatim. |
| 4(d) | Retain attribution notices from `NOTICE` | `NOTICE` keeps the original two lines first, then an addendum. Nothing was removed or rewritten. |
| 6 | No trademark / product-name grant | No Mika logo, icon, GIF, slogan or product name is used as Maeve's own branding. |
| 5 | Contributions | Inbound = outbound under Apache-2.0; there is no CLA or DCO. |

### Notice on modified files (§4(b))

> **This codebase contains files changed from the Mika snapshot.** Maeve renamed the
> Java/Kotlin package to `com.daiyimo.maeve`, changed the Gradle coordinates, repository URLs
> and version, and reworked the visual system, the connection layer and the capability set.
> The complete, per-file change record is the git history: run
> `git diff "$(git rev-list --max-parents=0 HEAD)"..HEAD` (or `git log --follow -- <file>`)
> from the repository root. The mechanical rename is reproducible via
> `python tools/maeve/rename_package.py --apply`.

Files with hand-written (non-mechanical) changes additionally carry a short header comment.
A per-file header was **not** injected into all ~1,440 mechanically renamed files: doing so
would have altered generated sources, AIDL and XML with inconsistent comment syntax. This
central notice plus the preserved diff is the declaration of record.

## What changed since the baseline

| Area | Change |
|---|---|
| Product name / slogan | **Maeve**; slogan *She runs the loop. You keep your screen.* |
| Release identity | `versionCode 38 / 0.1.37` → `versionCode 1 / 0.1.0-dolores`; `rootProject.name = "Maeve"` |
| Package | `com.wangzan101.mika` → `com.daiyimo.maeve` in the renamed source tree plus the source directories |
| Repository URLs | `MaeveLinks` and `ApkIntegrity`'s derived whitelist point at `Daiyimo/Maeve`; `tools/relay/go.mod` module path updated; CLI and sandbox script paths follow the new package |
| Repository docs | `README.md`, this file, `CONTRIBUTING.md`, `SECURITY.md`, `docs/spec/`, `docs/adr/`, CI workflow and issue template |
| Attribution | `NOTICE` addendum; third-party notices augmented (ML Kit was previously undisclosed); GPL/LGPL texts mirrored into the APK |
| UI | Flutter shell under `flutter/`; Compose surfaces retired screen by screen |

## Deliberately kept for compatibility

- `mika_*` MCP tool names, the CLI `mika` command, `Author.Mika`'s serialized name and the
  `Download/Mika/` legacy path. External clients and stored conversations depend on them;
  new names ship as aliases where the wire contract allows.
- Upstream media under `docs/media/` that is still referenced as historical material.
- The archived reports in `docs/reference/` — they describe the earlier Mika-based source and
  keep its original names on purpose.

## License of this file

Apache-2.0, same as the repository. This document is engineering bookkeeping, not legal
advice; re-check trademark status before any commercial use.
