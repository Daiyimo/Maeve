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
| `lib/app/chat_page.dart` | Quiet page chrome, left-aligned opening, screenplay turn rail, two-row composer, quote/queue. |
| `lib/app/sessions_drawer.dart` | Search, filters, pin/folder/day grouping, multi-select, identity bar. |
| `lib/app/quick_panel.dart` | Right-hand entry **grid** (plus a visible header button, so narrow screens can reach it). |
| `lib/app/onboarding.dart` | Welcome → runtime → appearance → access → key → finishing. |
| `lib/design/` | OKLCH, six presets, nearly neutral canvases, matte cards, bounded glass chrome, type, motion and motifs. |
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

`flutter build aar --debug --no-profile --no-release` publishes the debug AAR and its POM
to `build/host/outputs/repo/`. The Android host resolves that Maven module and its transitive
engine dependencies rather than opening files at hard-coded Gradle cache hashes. The Flutter
artifact repository honours `FLUTTER_STORAGE_BASE_URL` when a mirror is configured.

The generated `.android` project and the Android host remain separate builds; the host's
tool versions are pinned in the Gradle wrapper and version catalog. `./gradlew :app:assembleDebug`
rebuilds the Maven output when the shell sources change; `-PskipFlutterAar` uses a prebuilt AAR.

## Commands

Run from the repository root:

```bash
bash flutter/tool/verify.sh
```

The same format, analyze and widget checks run automatically on pushes and pull requests.
Tests use a fixed ordering seed and two workers; set `MAEVE_TEST_SEED` to exercise another order.
JSON test events are saved in `build/test-results/flutter.json` and uploaded by CI.

Or run the steps directly:

```bash
cd flutter
flutter pub get
dart format lib test tool
flutter analyze
flutter test
```

`verify.sh` sources `../_env/env.sh` when it exists, or honours `MAEVE_ENV_FILE`; otherwise it
uses `flutter` and `dart` from `PATH`. Set `NO_PROXY=127.0.0.1,localhost,::1` when a machine
proxy is active, or widget tests fail before they run.

For optional local visual review with a CJK font already on your machine:

```bash
cd flutter
flutter test tool/preview_test.dart --dart-define=MAEVE_PREVIEW_FONT=/absolute/path/font.ttf
```

This writes light/dark chat, drawer and settings screenshots to `build/previews/`.
It loads Material icons from `FLUTTER_ROOT` and does not add a bundled font or platform-specific
golden comparison to the normal widget suite.

## Contracts

- UI method / status channels: `maeve/ui`, `maeve/ui/events`
- Chat method / event channels: `maeve/chat`, `maeve/chat/events`
- Notification / deep-link entries: `maeve/ui/entry`

The names are the contract with the Android host. Chat snapshots and conversation operations
use `MaeveChatBridge`; widget tests inject `FakeChatBridge` and never require a live Android host.
Changing a channel name is a bridge change, not a visual change.

## Limits

Chat replies and conversation operations are bridged to the Android host. Some auxiliary
Flutter pages still expose local preferences or placeholder actions; attachments are not yet
wired to a platform picker.

Backdrop blur is real (bounded `BackdropFilter`, one `BackdropGroup` for the header).
设置 → 动效与性能 → 减少透明效果 drops every `BackdropFilter` and falls back to a near-opaque fill.
Local renders and widget tests verify layout; they do not replace a physical-device pass.
[FLUTTER_MIGRATION.md](../FLUTTER_MIGRATION.md) is the authoritative list; the superseded replica
hand-off stays as history in [`UI_SPEC_HANDOFF.md`](UI_SPEC_HANDOFF.md).
