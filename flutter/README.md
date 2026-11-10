# maeve_flutter

The Flutter shell for Maeve Android. It is a Flutter module, not a standalone app: the
Android host in `:app` embeds the debug AAR built from this directory. Read
[FLUTTER_MIGRATION.md](../FLUTTER_MIGRATION.md) for direction, build order, toolchain and the
full list of prototype limits.

## What lives here

| Path | What it is |
|---|---|
| `lib/main.dart` | Entry point only (`runApp`), re-exporting the app shell. |
| `lib/app/maeve_app.dart` | `MaterialApp`: locale, light/dark themes per palette, global controller. |
| `lib/app/maeve_controller.dart` | Settings, conversations, memories, run state (in memory). |
| `lib/app/maeve_shell.dart` | B2 shell: two mutually exclusive gesture-driven drawers. |
| `lib/app/chat_page.dart` | B3/B4/B5: floating header, message list, floating composer. |
| `lib/app/sessions_drawer.dart` | B6: search, filters, grouping, multi-select, identity bar. |
| `lib/app/quick_panel.dart` | B7: right-hand quick entries. |
| `lib/app/onboarding.dart` | B1: welcome → runtime → appearance → access → key → finishing. |
| `lib/design/` | Tokens, six palettes, OKLCH + contrast, surfaces (glass styles), motion. |
| `lib/ui/common/` | Pressable, HoldToConfirm, AppPage, AppSheet, status widgets, haptics. |
| `lib/ui/settings/` | Settings hub (B8) and sub-pages (B9) plus the settings row components. |
| `lib/ui/phone/`, `lib/ui/runs/`, `lib/ui/memory/` | Phone control (B10), Runs, Memory. |
| `lib/widgets/glass_surface.dart` | `MaeveGlassSurface`: bounded blur, shared sampling, downgrade path. |
| `lib/bridge/maeve_bridge.dart` | `MaeveBridge` channel constants and the `open_settings` / status helpers. |
| `test/` | Theme (contrast/tokens), components, glass pipeline and shell coverage. |
| `tool/verify.sh` | Format, analyze and test, in that order. |

## Build the AAR for `:app`

`flutter build aar --debug` writes `flutter-debug.aar` under
`.android/Flutter/build/outputs/aar/`. The generated `.android` project uses the Flutter
template's own Gradle 9.3.1 / AGP 9.1.0; the host `:app` still uses Gradle 8.11.1 / AGP 8.7.3
from `gradle/libs.versions.toml`. They are two separate builds by design: the AAR is the
boundary. `./gradlew :app:assembleDebug` runs this build automatically when the AAR is
missing or the shell sources changed; `-PskipFlutterAar` skips it when the AAR is prebuilt.

## Commands

Run from the repository root:

```bash
bash flutter/tool/verify.sh
```

Or run the steps directly:

```bash
cd flutter
flutter pub get
dart format lib test
flutter analyze
flutter test
```

`verify.sh` sources `../_env/env.sh` when it exists, or honours `MAEVE_ENV_FILE`; otherwise it
uses `flutter` and `dart` from `PATH`. Set `NO_PROXY=127.0.0.1,localhost,::1` when a machine
proxy is active, or widget tests fail before they run.

## Contracts

- Method channel: `maeve/ui`
- Event channel: `maeve/ui/events`

The names are the contract with the Android host. The host registers `open_settings` and
`status` plus the status event stream; chat, model calls and persistence are not wired, so
the shell still runs on local demo state. Changing a channel name is a bridge change, not a
UI change.

## Limits

Chat replies are local delayed demos. Conversations and memories exist only in process
memory. Runs is a readiness view, attachments are not wired to a picker, and no model or
Kotlin Agent execution is connected.

Backdrop blur is real (bounded `BackdropFilter`, shared sampling per header group) and was
verified on a PZ261A; the earlier "avoid blur" note is superseded. 设置 → 动效与性能 →
减少透明效果 drops every `BackdropFilter` and falls back to an almost opaque fill.
[FLUTTER_MIGRATION.md](../FLUTTER_MIGRATION.md) is the authoritative list, and
[`UI_SPEC_HANDOFF.md`](UI_SPEC_HANDOFF.md) tracks per-screen parity with the design spec.
