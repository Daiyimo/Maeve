# maeve_flutter

The Flutter shell for Maeve Android. It is a Flutter module, not a standalone app: the
Android host in `:app` embeds the debug AAR built from this directory. Read
[FLUTTER_MIGRATION.md](../FLUTTER_MIGRATION.md) for direction, build order, toolchain and the
full list of prototype limits.

## What lives here

| Path | What it is |
|---|---|
| `lib/main.dart` | Entry point only (`runApp`), re-exporting the app shell. |
| `lib/app/maeve_app.dart` | `MaterialApp`: locale, light/dark themes per preset, `MaeveThemeScope` above the Navigator. |
| `lib/app/maeve_controller.dart` | Settings, conversations, memories, run state (in memory). |
| `lib/app/maeve_shell.dart` | Two mutually exclusive gesture-driven drawers on one progress value. |
| `lib/app/chat_page.dart` | Glass top bar, screenplay turn rail, paper-strip composer, quote/queue. |
| `lib/app/sessions_drawer.dart` | Search, filters, pin/folder/day grouping, multi-select, identity bar. |
| `lib/app/quick_panel.dart` | Right-hand entry **grid** (plus a visible header button, so narrow screens can reach it). |
| `lib/app/onboarding.dart` | Welcome → runtime → appearance → access → key → finishing. |
| `lib/design/` | OKLCH, six presets, colours, semantic, four glass tiers + aurora, shapes, type, motion, motifs, theme. |
| `lib/ui/common/` | Pressable, HoldToConfirm, AppPage/TopBar, AppSheet, status widgets, haptics. |
| `lib/ui/settings/` | Settings hub + sub-pages and the row language (A9 spacing rule lives in `RowMetrics`). |
| `lib/ui/phone/`, `lib/ui/runs/`, `lib/ui/memory/`, `lib/ui/more/` | Phone control, runs, memory, web/terminal/community/share. |
| `lib/widgets/glass_surface.dart` | `MaeveGlassSurface`: bounded blur, grouped sampling, rim/hairline, downgrade. |
| `lib/bridge/maeve_bridge.dart` | `MaeveBridge` channel constants and the `open_settings` / status helpers. |
| `test/` | Theme (contrast/tokens), components, glass pipeline, screens and shell. |
| `tool/verify.sh` | Format, analyze and test, in that order. |

Design decisions are recorded per token in
[`docs/spec/flutter-visual-parity.md`](../docs/spec/flutter-visual-parity.md); the design contract is
[`docs/spec/visual-tokens.md`](../docs/spec/visual-tokens.md) and the Compose reference is
`app/src/main/java/com/daiyimo/maeve/ui/theme/`.

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

Backdrop blur is real (bounded `BackdropFilter`, one `BackdropGroup` for the header).
设置 → 动效与性能 → 减少透明效果 drops every `BackdropFilter` and falls back to a near-opaque fill.
**This rewrite was not device-verified** (REWRITE_SPEC §9); the owner runs the device pass.
[FLUTTER_MIGRATION.md](../FLUTTER_MIGRATION.md) is the authoritative list; the superseded replica
hand-off stays as history in [`UI_SPEC_HANDOFF.md`](UI_SPEC_HANDOFF.md).
