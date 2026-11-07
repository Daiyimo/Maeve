# Maeve Flutter migration

## Direction

Maeve is moving to a Flutter-first UI architecture. Flutter owns the user-visible shell, navigation, responsive layout, theme, motion, chat composition, settings surfaces, memory views, and run timelines. Kotlin remains the Android capability layer for accessibility, sandbox execution, web takeover, voice, notifications, permissions, and device services.

This is an add-to-app migration. Existing Kotlin/Compose UI is preserved while Flutter replaces screens incrementally. The native package and existing Android capabilities remain unchanged during the migration.

## Current module

- Flutter module: `flutter/`
- Flutter SDK: `D:/dev/flutter/flutter`
- Dart: `3.13.5`
- Android SDK: `G:/vibe-coding/_toolchains/android-sdk`
- Gradle cache: `G:/gradle-home`
- Pub cache: `G:/pub-cache`

The current Flutter shell includes:

- Maeve eclipse mark and restrained plum accent
- Compact drawer and expanded rail/sidebar layout
- Chat-first workspace with host-style response rail
- Runs, Memory and Settings destinations as migration placeholders
- Theme switcher and send interaction
- Platform bridge contract at `maeve/ui` and `maeve/ui/events`

## Migration order

1. Embed the Flutter module in `app` and expose a feature flag for the Flutter shell.
2. Define typed platform contracts for status, conversations, tasks, voice, permissions, and settings.
3. Move the chat state model behind the bridge, then replace the placeholder chat with the real conversation stream.
4. Migrate drawer destinations: Runs, Memory, Browser, Screens, About and Settings.
5. Retire Compose screen-by-screen only after instrumentation coverage and parity checks pass.

## Build note

The Flutter module passes Dart formatting and `flutter analyze`. Widget tests are currently blocked by the local `flutter_tester` WebSocket startup failure. APK build is blocked because the generated module wrapper tries to download Gradle 9.3.1; the existing Maeve project uses Gradle 8.11.1 and the G-drive cache should be reused for the host integration.
