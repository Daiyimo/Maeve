<!-- docs-check: spans-off — Historical migration log; old names describe past states -->
# Migration notes — M1 → M4

Live status of turning the upstream repository into `Daiyimo/Maeve`. Update this file as each
milestone lands. See [SPEC.md](../../SPEC.md) §8–§10 for the plan and [ATTRIBUTION.md](../../ATTRIBUTION.md)
for the Apache-2.0 change record.

## M1 — done (2026-11-06)

| Item | Result |
|---|---|
| Upstream history | Initially fetched from `E:\project\daiyimo\Maeve` (523 commits, base `bbc8922a`). On 2026-11-06 the published history was rewritten to one baseline snapshot `bccdcda6` + four Maeve commits so Contributors shows only the maintainer. Later edits moved the root to `7cf7ff3`; see `ATTRIBUTION.md` for the current state. Pre-rewrite history, if still present locally, is on the unpublished `backup/full-history-before-rewrite`. See `ATTRIBUTION.md` and "History rewrite" below. |
| Package rename | upstream package → `com.daiyimo.maeve`: 9,230 replacements in 1,442 files + 8 source directories. 0 residual references in the source tree. |
| Gradle identity | `rootProject.name = "Maeve"`, `namespace` / `applicationId` = `com.daiyimo.maeve`, `versionCode = 1`, `versionName = "0.1.0-dolores"`. |
| Upstream URLs | `MaeveLinks.kt` (5 URLs, also drives `ApkIntegrity`'s whitelist), `tools/relay/go.mod` module path, `maeve_cli.py` `PHONE_FILES`, `tools/sandbox/build-runtime.py` split path tuple, `AboutScreenTest` release fixture. |
| Compliance | New `README.md`, `ATTRIBUTION.md`, `CONTRIBUTING.md`, `SECURITY.md`; `NOTICE` addendum; `THIRD_PARTY_NOTICES.md` + in-app `Licenses.ENTRIES` augmented (ML Kit was undisclosed); GPL/LGPL texts, verbatim `NOTICE` and `SOURCE_OFFER.md` mirrored into `app/src/main/assets/licenses/`. |
| Docs | `docs/spec/` (visual tokens, haptics, bridge v3, this file), `docs/adr/`, CI compliance job, release workflow. |

Reproduce the mechanical rename:

```bash
python tools/maeve/rename_package.py            # dry run, prints every affected file
python tools/maeve/rename_package.py --apply    # idempotent
python tools/maeve/check_compliance.py          # see CI section below
```

## M2 first cut — done (2026-11-06, visible brand only)

Scope: make the installed app visibly Maeve. **Not** the full M2 design system — no OKLCH
derivation, no four launcher aliases, no `HapticEngine`, no glass surfaces.

| Item | Result |
|---|---|
| App / launcher name | `app_name` → `Maeve`; `devInstall` resValue → `Maeve Dev`. |
| Visible copy | `tools/maeve/brand_strings.py` replaced **2,360** brand tokens in XML text nodes across **310** `values*/strings*.xml` files (10 UI languages). URLs are stashed before replacement, so the 20 remaining upstream doc links stay intact. `about_author` ("Made by Wang Zan · © 2026") is preserved verbatim for Apache-2.0 §4(c)/(d). |
| In-app literals | `tools/maeve/brand_code.py` — whitelist-only edits in 7 files: three `R.drawable` logo references, `ApkUpdater` update title, `Diagnostics.HEADER`, `UpdateChecker` User-Agent, Gradle dev label. |
| Hard-coded product literals | `tools/maeve/brand_literals.py` (idempotent) — **26 literals in 14 files** that never touch `strings.xml`: `AboutScreen` page title, `ChatCopy.APP_TITLE` (top app bar), `ChatDrawer` header, `TaskService` foreground-notification titles, `CapsuleContent` / `CapsuleCard*` idle + status labels, `McpServerStore` `clientName` + sign-in HTML, `RemoteTaskHub` / `AmooBridge` remote error messages, clipboard labels, `ProviderStore` default UA. |
| Test assertions | `tools/maeve/brand_tests.py` — **26 brand tokens in 10 test files** aligned with the renamed copy; URL fixtures and `MaeveLinks`-style identifiers are protected by `Maeve(?![A-Za-z])`. |
| Icon | `tools/maeve/make_launcher_art.py` generates two 512×512 RGBA PNGs from §4's motif (two offset rings + off-centre light point): `maeve_launcher_art.png` (transparent foreground) and `maeve_logo.png` (opaque reverie gradient, used by Welcome / About / Logo). `ic_launcher.xml` now uses a gradient background plus a `monochrome` layer for Android 13+ themed icons. Old `maeve_launcher_art.png` deleted (1.0 MB, zero references left). |
| Accent | `Colors.kt` light `0xFF0A7CFF` → `0xFF6E6296` (reverie seed, 5.45:1 vs white text); dark `0xFF3D97FF` → `0xFF7969B4` (toned down from the `#B3A8D6` seed so `onAccent` white still clears 4.5:1 and the accent clears 3:1 on the dark page). `accentSoft` alpha unchanged. Hand-picked values, **not** yet the OKLCH `derive()` / `guard()` pipeline. |

**Kept as `Maeve` on purpose:** `LicensesScreen`'s first `Entry("Maeve", "Apache-2.0", …)`
(and the `filter { it.name != "Maeve" }` that depends on it) is the upstream attribution,
asserted by `check_compliance.py`; `NOTICE` and `about_author` likewise. `Theme.Maeve` is a
style name. `:core` protocol identities (`McpRpc.serverInfo`, `clientName`, `APP_NAME`,
`KEY_LABEL`) are left for M3, where `maeve_*` names and `maeve_*` deprecated aliases are
introduced together — changing half of them now would desynchronise the wire contract.

Verification: `translations.py check` → 1989 resources / 10 languages pass;
`check_compliance.py` → 23 notices, 24 licence-screen entries pass;
`./gradlew :app:assembleDebug` → BUILD SUCCESSFUL; `adb install -r` → Success;
cold start 548–580 ms. Welcome screen, home screen and the main chat screen (top bar
included) all show Maeve with the violet accent and no `Maeve` text.
**The old upstream package is still installed alongside** — the old
home-screen icon is expected, not a failed install.

### Test regression caught and fixed

The first copy replacement broke 16 previously-passing tests: 1508 tests / **75 failed**
vs. the M1 baseline of 59. Fifteen were `expected:<M[ika]...> but was:<M[aeve]...>` copy
assertions and one was `UpdateCheckerTest` asserting that `User-Agent` starts with `Maeve/`.
Aligning the assertions brought it to 60; the last failure exposed
`CapsuleContent.describe`'s hard-coded `"Maeve ${view.text}"`, which no resource edit could
reach — that is what prompted the `brand_literals.py` pass. The suite is back to
**1508 tests / 59 failed** with **zero** brand-related failures.

Reproduce the first cut:

```bash
python3 tools/maeve/brand_strings.py --dry-run   # then without --dry-run
python3 tools/maeve/make_launcher_art.py         # needs numpy; PNGs are committed
python3 tools/maeve/brand_code.py
python3 tools/maeve/brand_literals.py
python3 tools/maeve/brand_tests.py
```

## M2 interaction layer — done (2026-11-06, dsh-Go essence)

The first cut only re-skinned Maeve. This pass changes how the UI actually *feels*:

| Item | Result |
|---|---|
| Haptic engine | `ui/haptic/HapticEngine.kt`: `Tick / Light / Medium / Heavy / Reject` on `VibratorManager` + `VibrationEffect`, `areEffectsSupported` degrade chain, system `HAPTIC_FEEDBACK_ENABLED` + in-app switch (`MaeveHaptics`), `Reject` as a distinct two-pulse waveform. Not a wrapper around Compose's two haptic types. `VIBRATE` added to the manifest (it was missing). |
| Single entry | `MaeveTheme.haptics` provided by `MaeveTheme`; every UI file already imports `MaeveTheme`, so no per-screen haptic wiring. The four scattered upstream `LongPress` calls are gone. |
| Real interaction points | `MaeveButton` (press 0.97 scale, 90 ms, `Tick`), `SegmentedControl` (only when the value changes), `ListRow` / example rows (`Tick`), send goal (`Light`), long-press menus (`Medium`), refuse (`Reject`), approve irreversible action (`Heavy`). |
| HoldToConfirm | `ui/haptic/HoldToConfirm.kt`: 1500 ms ring, raw pointer events only, `Tick` on down / `Heavy` on completion / nothing on cancel. Wired to Advanced › clear all workspaces. Reduced motion does **not** shorten the hold. |
| Settings | Settings › 交互触感 switch (`settings_haptic` / `settings_haptic_sub`, 10 locales), backed by `MaeveHaptics.setAppEnabled`. |
| Shape & motion | `MaeveShapes.SquircleShape` (superellipse, continuous curvature) now backs `MaeveShapes.Card / Button / Tile`; `MaeveMotion` carries dsh-Go's duration ladder and `glide` curve. `ButtonSize.shape` widened from `RoundedCornerShape` to `Shape`. |
| Residual cleanup | `about_author` → 戴以沫 / Dai Yimo in all 10 locales; `chat_example_follow` (a follow-the-author example in *every* locale) and `chat_example_multi_step` (汽水音乐 VIP) removed from resources and `ChatCopy.examples` (6 → 4); ADB certificate `CN=Maeve` (wireless-debugging paired-device name); MCP `serverInfo`, `clientName`, `APP_NAME`, trace service name → Maeve. |

**Apache-2.0 boundary kept at the time:** `NOTICE`, `ATTRIBUTION.md` and the first `LicensesScreen` entry
still named Maeve and the upstream author. Only the user-facing "made by" line was re-attributed.
(The upstream attribution in `NOTICE` / `ATTRIBUTION.md` / `LicensesScreen` was removed later,
on the owner's decision — see `docs/derived-rewrite-plan.md` section 9.)

Verification: `translations.py check` → 1989 resources / 10 languages; `check_compliance.py`
→ 23 notices / 24 entries; `assembleDebug` → BUILD SUCCESSFUL; `adb install -r` → Success.
Unit tests **1513 / 59 failed** = the 59 Windows platform failures + 5 new passing
`HapticEngineTest` cases, **zero** new regressions. Two transient failures were fixed during
the pass: `StringResourcesTest` (CJK author left in `values-ko`) and `ChatCopyTest`
(asserted 6 examples after the count became 4). Device screenshots confirm 4 example rows,
the haptic switch, and 由 戴以沫 开发 on the About page.

Not in this pass (next round): OKLCH six-preset `derive()`/`guard()`, four-tier glass
surfaces, four launcher aliases + `LauncherIconController`, and the `:core` prompt / trust
labels / `Download/Maeve/` export path + `maeve_*` protocol aliases (M3, together with
`maeve-bridge` v3 — changing half of them now desynchronises the wire contract).

## Deliberately deferred — do not "fix" these casually

### Upstream documentation URLs (M4)

About 17 files under `docs/` (plus 5 translated READMEs in `docs/readme/`) still contain
upstream links, and `README.en.md` / `docs/README.*` still describe the upstream
product. They were **not** mechanically rewritten because:

1. Some lines are attribution statements, not links — a blind replace would turn
   "a fork of Maeve by the upstream author" into a false claim.
2. Editing user-facing copy triggers three generated/checked surfaces, in this order:
   - the `updateReadme` Gradle task (generated block in `README.md`, source in
     `configure-maeve/SKILL.md`),
   - `python3 tools/i18n/translations.py check` for 10 locales,
   - prompt golden snapshots via `-PupdateSnapshots`.
3. The whole README family is being replaced by the Maeve template anyway.

`app/src/main/res/values*/strings_mcp.xml` also keeps 20 upstream doc URLs (2 per locale);
they are protected by `brand_strings.py` and must be rewritten together with `docs/` in M4.

Do it as one reviewed change, with `git diff` of every generated file in the PR.

### Brand identifiers (M3 / M4)

Class names (`MaeveApp`, `MaeveLinks`, `MaeveAccessibilityService`, `MaeveTheme`, …), Log TAGs,
model-facing prompt strings, self-check strings, Binder/wake-lock tags, `Download/Maeve/`
export paths and `maeve_*` MCP tool names still exist. The `maeve_launcher_art` resource was
removed in the M2 first cut; launcher assets are now `maeve_*`. Renaming the remaining
identifiers is a separate, testable step. Product `v0.1.0` keeps the old MCP names as
**deprecated aliases** and `server/discover` advertises `deprecated_aliases`; they are
removed in product `v0.2.0`.
Do not remove the aliases in the same commit that adds the new names.

### Capability pruning (M3)

Disable at `TaskTools.toolDispatcher()` / `RunAssembler`, never with UI branches:
`SandboxCapability`, `RootCapability`, outbound Relay, external MCP server list, voice,
`AmooIntercept`. Keep `PhoneCapability`, inbound MCP, the file bridge, the whole guard
chain, memory/knowledge/skills and recovery.

### Bridge v3 (M3)

`AmooSetup` still injects `maeve-bridge` v2 with `SKILL_VERSION = 2` and hard-coded view IDs.
The protocol and a ready-to-inject `SKILL.md` draft are in [bridge-v3.md](bridge-v3.md).
Until M3 lands, the file bridge is **known broken** on the new package — this is expected,
not a regression to debug.

## Test baseline on this Windows host

`./gradlew :core:compileKotlin :app:compileDebugKotlin` → **BUILD SUCCESSFUL** (3m42s),
only pre-existing deprecation warnings. AIDL, resources, manifests and both Kotlin source
sets resolve under the new package.

Unit tests have a **pre-existing platform failure baseline** on Windows:

| run | result |
|---|---|
| upstream snapshot @ `bbc8922a` | 1508 tests, **59 failed** |
| Maeve M1, first run | 1508 tests, **61 failed** |
| Maeve M1, after fixing URL assertions | 1508 tests, **59 failed** (= baseline) |
| Maeve M2 first cut, before aligning copy assertions | 1508 tests, **75 failed** |
| Maeve M2 first cut, final | 1508 tests, **59 failed** (= baseline, 0 brand-related) |

The 59 are Windows-specific, not migration regressions:
`UnsupportedOperationException: View 'unix' not available` (POSIX file attributes in
`NioFdSys.statOf`), `trigger store rename failed`, `/bin/sh` unavailable, Windows path
separators and filesystem-timing assertions.
CI runs on `ubuntu-latest`, where they pass.

The counts above are the pre-Flutter suite (1508 tests). After the Flutter shell and the added
tests, a 2026-11-09 Windows run reports 2954 tests, 56 failed, 3 skipped; the failures are the
same category of platform issues plus snapshot hashes that differ from the Linux-generated
goldens. The Linux CI job is the judge for this suite.

The **2 extra failures were introduced by M1** and are fixed: `ApkIntegrityTest` and
The link test asserted hard-coded upstream URLs. They now derive the expected
URL from `MaeveLinks.RELEASES` / `MaeveLinks.NEW_ISSUE` so a future repo move cannot silently
break them again. Fake JSON fixtures that still contain an upstream URL are deliberate —
they are arbitrary test data, not assertions about production config.

> Do **not** "fix" the 59 by weakening assertions. Verify on Linux/CI instead.

## Verification commands (read-only)

```bash
# package identity
grep -n "namespace =\|applicationId =\|versionCode\|versionName" app/build.gradle.kts
grep -n "rootProject.name" settings.gradle.kts

# residual upstream package in source (expected: only SPEC.md and the historical script)
python tools/maeve/rename_package.py        # must print 0 replacements / 0 moves

# attribution must survive
cat NOTICE                                   # first two lines verbatim
git log --oneline | wc -l                    # short single-author history
git tag                                      # empty by design

# CI-equivalent local checks
python3 tools/i18n/translations.py check
python3 tools/maeve/check_compliance.py
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

On a device, add `MSYS_NO_PATHCONV=1` to any `adb shell` command containing `/sdcard/...`
under Git Bash — MSYS otherwise rewrites the path to `/Files/Git/sdcard/`.

## Q6 — published (2026-11-06 18:08)

Pushing the full history to a public repository is irreversible. The user explicitly
authorised it ("提交commit并push吧"); every check below passed first:

- [x] `git remote -v` → `origin = https://github.com/Daiyimo/Maeve.git`.
- [x] `git ls-remote origin` returned **no refs** → remote was empty at first publish,
      so the full 526-commit history could be pushed unchanged. **Superseded later the
      same day** by the history rewrite (see "History rewrite" below).
- [x] `git status --short` empty — no APK, no local notes, no build output.
- [x] `check_compliance.py` → 23 notices / 24 licence-screen entries; `NOTICE` first two
      lines verbatim; `translations.py check` → 1989 resources / 10 languages.
- [x] No secret, key, token or device serial in tracked files (the `sk-…` hits are
      redaction-test fixtures; `.env.example` and `PageScriptSecretRuleTest` are not secrets).
- [x] First publish: `git push -u origin --all && git push origin --tags` → `main` at
      `a438e2fc`, tag `amoo-bridge-v1`. **Both objects were later replaced** by the
      history rewrite; `origin/main` remains the upstream of `main`.
- [x] Verified with `git ls-remote origin`: HEAD, `refs/heads/main` and the annotated tag.

The `maeve-local` remote (a local filesystem path used only to seed history) is **kept** so
upstream fixes can still be fetched later. It must never be a push target: always name
`origin` explicitly, or rely on the `origin/main` upstream.

**Publishing source is not the same as shipping a release.** Still open before a public
APK release: verify the Google ML Kit licence terms, publish the GPL/LGPL source bundle
behind `licenses/SOURCE_OFFER.md`, configure a release keystore / `signingConfigs`, finish
R8 + release CI, and land M3 (`maeve-bridge` v3 — the file bridge is currently **known
broken** on the new package).

## History rewrite — single contributor (2026-11-06 18:1x)

The user asked for Contributors to show only themselves and for all commits up to and
including `0c92fc39e29c8c72efb7390daf9fb6a54405e6e2` to be dropped. The 39 commits after
that point included 16 by the upstream author (pulled in by a merge of `upstream/main`), so a plain
truncation would still have shown two contributors. Per the user's choice, they were
collapsed into one baseline snapshot:

| Step | Result |
|---|---|
| Local backup | `backup/full-history-before-rewrite` keeps all 526 old commits locally; it is **never pushed**. |
| New root | `bccdcda6` — `git commit-tree bbc8922a^{tree}`, author `Daiyimo <auroral.8751@gmail.com>`, message names Maeve and the upstream author and points at `NOTICE`, `LICENSE`, `ATTRIBUTION.md` and the licence screen. |
| Replay | `git cherry-pick` of the four Maeve commits (`0fd46b7d`, `fa1667db`, `481e87b3`, `a40ee242`). |
| Content check | At rewrite time `git rev-parse HEAD^{tree}` = `643211ebcc2d…`, **identical** to the pre-rewrite tree — no source file changed. The later docs-only commit changes the tree hash by design. |
| Contributors | `git log --format='%an <%ae>'` now returns only `Daiyimo <auroral.8751@gmail.com>`. |
| Tag | `amoo-bridge-v1` deleted locally and on `origin`; it pointed at an upstream archive commit and would have kept old objects reachable. |
| `maeve-local` remote | Removed, so old upstream objects cannot be fetched back by accident. The upstream repo itself is untouched. |
| Publish | `git push --force-with-lease origin main` — **destructive and irreversible**; anyone who cloned or forked before this point must re-clone or rebase. |

The old `Q6` checklist below records the **first** publish of the full history and is kept
as history; its commit hashes and tag no longer exist in the published repository.

> Compliance reminder: collapsing git history changes who appears as a *commit author*.
> It does **not** remove the Apache-2.0 §4 duty to keep `NOTICE`, `LICENSE`, `ATTRIBUTION.md` and
> the in-app `Maeve` licence entry. Those files are unchanged.
