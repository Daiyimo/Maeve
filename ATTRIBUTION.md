# Attribution and provenance

Maeve is an independent, Apache-2.0 Android project maintained by Daiyimo.
This file records provenance and licence compliance for everything that ships with
Maeve; it is not a claim of affiliation with or endorsement by any third party.

## Provenance

```text
Daiyimo/Maeve              this repository, maintained independently
```

The published history starts from a single baseline snapshot, root commit `7cf7ff3`
("Baseline: Maeve-old snapshot"), and every later Maeve change follows it.

```bash
BASE="$(git rev-list --max-parents=0 HEAD)"
git log --oneline "$BASE"..HEAD   # one snapshot + every Maeve change
git diff "$BASE"..HEAD            # everything changed since the snapshot
git rev-parse HEAD^{tree}         # changes with every commit; compare within one run only
```

## Apache-2.0 §4 statements

| § | Requirement | How Maeve complies |
|---|---|---|
| 4(a) | Give recipients a copy of the License | `LICENSE` is the unmodified Apache-2.0 text. |
| 4(b) | Modified files carry prominent change notices | See the notice below; the complete per-file change record is the git history. |
| 4(c) | Retain copyright, patent, trademark and attribution notices | All third-party notices are retained; see `licenses/THIRD_PARTY_NOTICES.md`. |
| 4(d) | Retain attribution notices from `NOTICE` | `NOTICE` carries the Maeve attribution and points at the third-party notices. |
| 6 | No trademark / product-name grant | No third-party logo, icon, GIF, slogan or product name is used as Maeve's own branding. |
| 5 | Contributions | Inbound = outbound under Apache-2.0; there is no CLA or DCO. |

### Notice on modified files (§4(b))

> **Files in this codebase were changed during development.** Maeve unified the
> Java/Kotlin package to `com.daiyimo.maeve`, set the Gradle coordinates, repository URLs
> and version, and reworked the visual system, the connection layer and the capability set.
> The complete, per-file change record is the git history: run
> `git diff "$(git rev-list --max-parents=0 HEAD)"..HEAD` (or `git log --follow -- <file>`)
> from the repository root.

## What the repository ships

| Area | Notes |
|---|---|
| Product name / slogan | **Maeve**; slogan *She runs the loop. You keep your screen.* |
| Release identity | `versionCode 1 / 0.1.0-dolores`; `rootProject.name = "Maeve"` |
| Package | `com.daiyimo.maeve` across source tree and Gradle coordinates |
| Third-party notices | `licenses/THIRD_PARTY_NOTICES.md`; ML Kit disclosed; GPL/LGPL texts mirrored into the APK |
| UI | Flutter shell under `flutter/`; Compose surfaces retired screen by screen |

## Naming boundary

The product surface uses Maeve names: `maeve_*` MCP tools, the `maeve` CLI, `Author.Maeve`,
`Download/Maeve/`, `MAEVE_DISPLAY_ID` and the `maeve` sandbox client. No pre-release
compatibility aliases are kept because there is no released binary yet.

## License of this file

Apache-2.0, same as the repository. This document is engineering bookkeeping, not legal
advice; re-check trademark status before any commercial use.
