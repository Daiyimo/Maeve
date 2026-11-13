# Contributing to Maeve

Thanks for helping. Maeve is an independent Apache-2.0 project — please read
[ATTRIBUTION.md](ATTRIBUTION.md) and [NOTICE](NOTICE) first so attribution stays intact.

## Before you open a PR

- Search existing issues, especially the [device report](.github/ISSUE_TEMPLATE/device-report.yml)
  template — most "it doesn't work" reports are device/permission specific.
- For anything touching the agent loop, the guard chain, or the background display, describe
  the **observable behaviour** before and after. These paths are hard to reason about from
  a diff alone.
- Keep the change small. The codebase carries years of careful invariants; a mechanical
  rename of 1,400 files was already a lot for one step.

## Development setup

- JDK 17 (`:core` uses `jvmToolchain(17)`), Android SDK 36, `minSdk 30`, `targetSdk 35`.
- Flutter 3.47.7 with Dart 3.13.5 for `flutter/`. The pinned JDK / Gradle / AGP / Flutter
  set, installation and self-check live in [tooling/toolchain/README.md](tooling/toolchain/README.md).
- `powershell -NoProfile -File tooling/toolchain/verify.ps1` (or `bash tooling/toolchain/verify.sh`) must pass before
  you touch the build; it catches a wrong JDK, Gradle or Flutter version early.
- `./gradlew :app:assembleDebug` and `./gradlew :core:test` must pass.
- Flutter changes must also pass `bash flutter/tool/verify.sh`, which runs `dart format`,
  `flutter analyze` and `flutter test` in that order.
- For workflow changes, run `bash tools/ci/check_workflows.sh` on Linux or WSL before
  pushing. It downloads and verifies pinned actionlint, including GitHub's case-insensitive
  duplicate-key checks. Preserve the executable bit on `gradlew` for Linux checkouts.
- Device work needs ADB / wireless debugging for the `shell` helper process, plus the
  accessibility service enabled. See [SPEC.md](SPEC.md) §12.4 for known StepOS constraints.

### Module layout

| Path | Rule |
|---|---|
| `:core` | Pure JVM. No Android imports (invariant 5). Prompts, knowledge and skills are resources here. |
| `:app` | Android host; source root is `app/src/main/java`. It consumes the Flutter debug AAR, so the Flutter module must be built before `:app` compiles. |
| `flutter/` | Dart UI shell. Not a Gradle subproject. It owns presentation and the `maeve/ui` bridge contracts, never platform work. |

A clean clone has no `flutter/.android/` directory and therefore no AAR. Build the Flutter
module first; `:app:assembleDebug` alone cannot succeed without it.

## Invariants worth knowing

These come from the project code review and are easy to break accidentally:

1. **Every action that changes the outside world goes through `ActionExecutor`.** Do not
   add a side-effecting shortcut in a tool or ViewModel — the operation guards, confirmation
   decision and provenance record all live on that path.
2. **Never swallow `CancellationException`.** Catch it and rethrow, or let structured
   concurrency handle it.
3. **No `GlobalScope`.** Use a scope owned by the component, with a `SupervisorJob` where
   sibling failures must not cancel each other.
4. **Context carries `source` and `trust`.** Anything injected into a prompt from a screen,
   a file or a remote agent is untrusted data, not instructions.
5. **The `:core` module is pure JVM.** Do not add an Android import there.
6. **Remote tasks share one executor.** amoo file-bridge tasks, MCP tasks and CLI tasks all
   land in `RemoteTaskService → RemoteTaskHub → TaskService.startMcp`. Do not build a
   second runner.

## Changing user-facing text

Text is not free-floating. Editing copy can trigger three generated/checked surfaces:

1. `README.md` has generated blocks produced by the `updateReadme` Gradle task (source in
   `core/src/main/resources/skills/configure-maeve/SKILL.md`). Run `./gradlew :core:updateReadme`
   rather than editing between the markers.
2. The translated READMEs under `docs/readme/` must be updated in the same change: each keeps
   a `translated-from` fingerprint of `README.md` and the same number of `##` sections.
3. 10 locales are verified by `tools/i18n/translations.py check`.
4. Prompt wording is guarded by golden snapshots — update with `-PupdateSnapshots` only
   when the change is intentional, and include the snapshot diff in the PR.

Do them in that order. A PR that changes strings without the snapshots will fail CI.

## Commit and PR

- One logical change per commit; imperative subject in Conventional Commits form:
  `type(scope): summary`. Use `feat`, `fix`, `build`, `docs`, `refactor`, `test` or `chore`,
  with a `core`, `app`, `flutter`, `docs` or `tools` scope.
- The body states the problem, the change, and how it was verified. The recent commits are
  the model: a short motivation paragraph, bullet points for the change, and a final
  `Verified:` line naming the commands and the device.
- Include `file:line` references when you fix a defect.
- PR checklist:
  - [ ] `./gradlew :app:assembleDebug` and `./gradlew :core:test` pass
  - [ ] For Flutter changes, `bash flutter/tool/verify.sh` passes
  - [ ] No third-party URL, logo, or branding introduced
  - [ ] `NOTICE` and `ATTRIBUTION.md` still accurate if you changed licensing/attribution scope
  - [ ] New third-party dependency added to `licenses/THIRD_PARTY_NOTICES.md` **and** mirrored
        into `app/src/main/assets/licenses/`
  - [ ] Device-specific behaviour verified on a real device (state model + Android version)

## Licence of contributions

Apache-2.0 §5, inbound = outbound. There is no CLA and no DCO; by submitting a PR you
license your contribution under Apache-2.0. Do not paste code from AGPL-licensed projects
— in particular, dsh-Go is AGPL-3.0 and only its **design decisions** may be consulted,
never its source, icons, fonts or other assets.
