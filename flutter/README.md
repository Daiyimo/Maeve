# maeve_flutter

The Flutter shell for Maeve Android. It is a Flutter module, not a standalone app: the
Android host in `:app` embeds the debug AAR built from this directory. Read
[FLUTTER_MIGRATION.md](../FLUTTER_MIGRATION.md) for direction, build order, toolchain and the
full list of prototype limits.

## What lives here

| Path | What it is |
|---|---|
| `lib/main.dart` | The shell: responsive rail/drawer navigation, conversations, composer, Runs, Memory, Settings, and the app entry point. |
| `lib/bridge/maeve_bridge.dart` | `MaeveBridge` channel constants and the `open_settings` / status helpers. |
| `lib/models/conversation.dart` | `Conversation` and `Message`, the in-memory chat model. |
| `lib/widgets/maeve_mark.dart` | `MaeveMark`, `MarkState` and the mark painter. |
| `lib/design/maeve_tokens.dart` | Colors, spacing, radii, motion durations and the light/dark `ThemeData`. |
| `lib/widgets/glass_surface.dart` | Tiered glass surfaces and buttons. No runtime backdrop blur. |
| `test/widget_test.dart` | Widget coverage for navigation, mark states, search, conversations, memory, stopping a reply and the theme toggle. |
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
Kotlin Agent execution is connected. Runtime blur is deliberately avoided because it crashed
Android RenderThread in earlier builds. [FLUTTER_MIGRATION.md](../FLUTTER_MIGRATION.md) is the
authoritative list.
