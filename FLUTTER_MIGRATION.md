# Maeve Flutter migration

## Direction

Maeve Android uses Flutter for its user interface and Kotlin for Android platform capabilities. Flutter presents a cohesive iOS-inspired visual language; this does not make the module an iOS application and does not use SwiftUI or Xcode.

Flutter owns the user-visible shell, responsive navigation, themes, chat composition, Runs, Memory and Settings surfaces. Kotlin remains the Android capability layer for accessibility, sandbox execution, web takeover, voice, notifications, permissions and device services. Existing Kotlin/Compose surfaces can be retired only after the Flutter equivalents have real behavior and regression coverage.

## Current module

- Flutter module: `flutter/`
- Flutter SDK: `D:/dev/flutter/flutter`
- Dart SDK constraint: `^3.13.5`
- Android SDK: `G:/vibe-coding/_toolchains/android-sdk`
- Gradle cache: `G:/gradle-home`
- Pub cache: `G:/pub-cache`
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

For the design-system change, run `dart format lib test`, `flutter analyze` and `flutter test` from the `flutter/` module. The widget tests cover the shell, drawer navigation, message sending and new conversation creation. A successful local test run does not imply that Android platform bridge handlers or real model services are connected.

The Android host APK and physical-device RenderThread log check remain separate integration acceptance steps. Record the actual Flutter/Gradle/SDK versions and test results from the machine performing the validation; do not reuse old failure notes as if they describe the current build.
