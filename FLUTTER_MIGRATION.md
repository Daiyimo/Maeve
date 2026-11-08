# Maeve Flutter migration

## Direction

Maeve Android uses Flutter for its user interface and Kotlin for Android platform capabilities. Flutter presents a cohesive iOS-inspired visual language; this does not make the module an iOS application and does not use SwiftUI or Xcode.

Flutter owns the user-visible shell, responsive navigation, themes, chat composition, Runs, Memory and Settings surfaces. Kotlin remains the Android capability layer for accessibility, sandbox execution, web takeover, voice, notifications, permissions and device services. Existing Kotlin/Compose surfaces can be retired only after the Flutter equivalents have real behavior and regression coverage.

## Current module

- Flutter module: `flutter/`
- Flutter SDK: `D:/dev/flutter/flutter`
- Dart SDK constraint: `^3.13.5`
- Android SDK: `G:/vibe-coding/_toolchains/android-sdk`
- Gradle cache (`GRADLE_USER_HOME`): `G:/vibe-coding/daiyimo/_env/gradle-home`
- Pub cache (`PUB_CACHE`): `G:/vibe-coding/daiyimo/_env/pub-cache`
- Shared visual tokens: `flutter/lib/design/maeve_tokens.dart`
- Tiered glass-like surfaces: `flutter/lib/widgets/glass_surface.dart`

The current Flutter shell includes:

- Maeve eclipse mark, plum accent and unified light/dark design tokens
- Layered chrome, sheet, card and pill surfaces without runtime backdrop blur
- Responsive rail/sidebar navigation and searchable, switchable, creatable in-memory conversations
- User and Maeve message presentation, composer, thinking state and stop action
- Runs readiness state, Memory add/delete interactions, and appearance/platform settings
- Existing bridge contract names `maeve/ui` and `maeve/ui/events` remain unchanged

## Prototype limits

- Chat responses are local delayed demo replies; no model API, streaming, Kotlin Agent execution, tool calls or approval flow is connected.
- Conversations and memories exist only in Flutter process memory. They are not persisted to disk or Android storage.
- Runs is a visual readiness view and has no real execution history.
- Attachments are not connected to the Android file picker.
- The MethodChannel and EventChannel names are retained as contracts, but the current Android host does not register matching handlers.
- Runtime `BackdropFilter`/`RenderEffect` blur is intentionally avoided due to prior Android RenderThread instability.

## Migration order

1. Embed the Flutter module in `app` and expose a feature flag for the Flutter shell.
2. Define and implement typed platform contracts for status, conversations, tasks, voice, permissions, settings and attachments.
3. Move chat state and persistence behind the bridge; replace local demo replies with the real conversation stream.
4. Connect Runs, Memory, Browser, Screens, About and Settings to their real data and capabilities.
5. Retire Compose screen-by-screen only after instrumentation coverage, device stability and feature parity checks pass.

## Validation status

For the design-system change, run `dart format lib test`, `flutter analyze` and `flutter test` from the `flutter/` module, or run `bash flutter/tool/verify.sh`, which sources the shared environment file, performs all three in order and stops at the first failure. A failing format step means the tree is unformatted rather than broken.

The widget tests cover the shell, armed and idle mark rendering, drawer navigation at phone width, scrim dismissal, wide rail navigation, conversation switching, message sending, new conversation creation, conversation search, stopping a pending reply, memory add/delete and the theme toggle. A successful local test run does not imply that Android platform bridge handlers or real model services are connected.

### Local environment

Source `G:/vibe-coding/daiyimo/_env/env.sh` before running any Flutter or Gradle command. It pins the toolchain and caches verified on this machine and keeps loopback out of the proxy.

| Component | Path | Verified version |
| --- | --- | --- |
| Flutter SDK | `D:/dev/flutter/flutter` | 3.47.7 stable |
| Dart | bundled with Flutter | 3.13.5 |
| JDK | `G:/vibe-coding/_toolchains/jdk-17` | 17 |
| Gradle | `G:/vibe-coding/_toolchains/gradle-8.11.1` (`gradlew` also works) | 8.11.1 |
| Android SDK | `G:/vibe-coding/_toolchains/android-sdk` | NDK 25.1.8937393 |
| Gradle cache | `G:/vibe-coding/daiyimo/_env/gradle-home` | — |
| Pub cache | `G:/vibe-coding/daiyimo/_env/pub-cache` | — |

`_toolchains` holds shared binaries; `_env` holds the caches that were previously at `G:/gradle-home` and `G:/pub-cache`. After moving a pub cache, run `flutter pub get --offline` once so `.dart_tool/package_config.json` points at the new location; otherwise analyze fails with `include_file_not_found` for `package:flutter_lints/flutter.yaml`.

Three environment traps are handled by `env.sh` and documented here so they are not rediscovered:

- The system default JDK is 24. Android Gradle Plugin fails against it with `Could not create task of type 'OutgoingVariantsReportTask'`. Use the JDK 17 above through `JAVA_HOME`.
- A machine-wide HTTP proxy is normally set. `flutter test` talks to `flutter_tester` over a loopback WebSocket, which the proxy breaks with `WebSocketException: Invalid WebSocket upgrade request`. Keep `127.0.0.1`, `localhost` and `::1` in `NO_PROXY`, otherwise every widget test fails before it runs.
- Git Bash only finds tools on `PATH` when entries are POSIX-style (`/d/dev/...`, not `D:/dev/...`), so `env.sh` converts them before exporting `PATH`.

Latest run on this machine: `dart format` clean, `flutter analyze` clean, `flutter test` 14/14 passing. The Android host APK and physical-device RenderThread log check remain separate integration acceptance steps. Record the actual Flutter/Gradle/SDK versions and test results from the machine performing the validation; do not reuse old failure notes as if they describe the current build.
