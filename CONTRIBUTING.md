# Contributing to Maeve

Thanks for helping. Maeve is a fork of Mika (Apache-2.0) — please read
[FORK.md](FORK.md) first so attribution stays intact.

## Before you open a PR

- Search existing issues, especially the [device report](.github/ISSUE_TEMPLATE/device-report.yml)
  template — most "it doesn't work" reports are device/permission specific.
- For anything touching the agent loop, the guard chain, or the background display, describe
  the **observable behaviour** before and after. These paths are hard to reason about from
  a diff alone.
- Keep the change small. The upstream project has 500+ commits of careful invariants; a
  mechanical rename of 1,400 files was already a lot for one step.

## Development setup

- JDK 17 (`:core` uses `jvmToolchain(17)`), Android SDK 36, `minSdk 30`, `targetSdk 35`.
- `./gradlew :app:assembleDebug` and `./gradlew :core:test` must pass.
- Device work needs ADB / wireless debugging for the `shell` helper process, plus the
  accessibility service enabled. See [SPEC.md](SPEC.md) §12.4 for known StepOS constraints.

## Invariants worth knowing

These come from the upstream code review and are easy to break accidentally:

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

1. `README.md` has a generated block produced by the `updateReadme` Gradle task (source in
   `configure-mika/SKILL.md`).
2. 10 locales are verified by `tools/i18n/translations.py check`.
3. Prompt wording is guarded by golden snapshots — update with `-PupdateSnapshots` only
   when the change is intentional, and include the snapshot diff in the PR.

Do them in that order. A PR that changes strings without the snapshots will fail CI.

## Commit and PR

- One logical change per commit; imperative subject.
- Include `file:line` references when you fix a defect — see `docs/reference/` for the style.
- PR checklist:
  - [ ] `./gradlew :app:assembleDebug` and `./gradlew :core:test` pass
  - [ ] No new upstream URL, logo, or Mika branding introduced
  - [ ] `NOTICE` and `FORK.md` still accurate if you changed licensing/attribution scope
  - [ ] New third-party dependency added to `licenses/THIRD_PARTY_NOTICES.md` **and** mirrored
        into `app/src/main/assets/licenses/`
  - [ ] Device-specific behaviour verified on a real device (state model + Android version)

## Licence of contributions

Apache-2.0 §5, inbound = outbound. There is no CLA and no DCO; by submitting a PR you
license your contribution under Apache-2.0. Do not paste code from AGPL-licensed projects
— in particular, dsh-Go is AGPL-3.0 and only its **design decisions** may be consulted,
never its source, icons, fonts or other assets.
