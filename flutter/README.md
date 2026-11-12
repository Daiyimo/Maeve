# Maeve Flutter UI

Flutter is the only application interface. Android owns the existing Mika-derived task engine, capabilities, persistence, provider credentials and platform overlays. See [host boundary](../FLUTTER_MIGRATION.md) and [source/history audit](../docs/dev/flutter-capability-audit.md).

## Current surfaces

- Real chat and conversation search, pinning and deletion through maeve/chat.
- Model configuration and selection through maeve/settings and the existing native ProviderStore, including its provider presets with their native endpoints and protocols. Keys are not returned to Flutter.
- Android permission status and system authorization targets, plus native helper pairing notifications.
- Amoo setup and file-bridge status through the existing native AmooSetup and FeatureFlags; reading the page does not start setup.
- Persistent themes, six palettes, reduced transparency and haptics.
- About, the purple eclipse mark (equilibrium at rest, local divergence during real work), and packaged legal notices.

The account, billing, high/medium/low tiers, plugin market, remote host, terminal, web, demo memory/run pages, fake authorization controls and temporary archive are removed. Native capabilities behind those labels remain in Android/core; an unconnected UI entry is not evidence that the underlying capability is absent.

## Layout and material

The idle home fits normal phone viewports without scrolling; three compact starts leave space between the hero and composer. Large text and the keyboard can scroll when needed. Chrome and sheets use bounded backdrop sampling; scrolling cards use static directional light to avoid GPU feedback stripes. System reduced motion and high contrast remain supported.

## Validation

Run flutter/tool/verify.sh from the repository or the Flutter analyzer and widget tests from this module. Tests cover chat behavior, native settings contracts, persistence, endpoint input validation, all nine real settings destinations, narrow/wide/large-text layouts and six light/dark palettes. No screenshot font is required by CI.

Wireless pairing reuses Android's native DeveloperSettings route, with the wireless-debugging preference highlighted when supported. The user enables wireless debugging and opens its pairing-code dialog there; inaccessible Settings targets return a visible error. It does not use an unsupported wireless-debugging action or silently redirect to app details.

System access distinguishes an enabled accessibility setting from a connected service. Pairing guides the user to notification settings first when notifications are blocked, so the native code-entry notification remains available during setup.

Chat execution follows native `ComposerMode`; a turn's `live` flag identifies the attached turn and may remain true after completion. Conversation summary events update metadata while preserving loaded turns. Regression tests cover completion, summary refreshes during and after work, fresh conversations and error details with blank drafts.

`ConfiguredChatDeviceTest` is an optional device check for an already configured main model. It sends one greeting, checks the rendered reply and completion controls, then exercises a native summary update. Run instrumentation with `-e class com.daiyimo.maeve.ui.ConfiguredChatDeviceTest -e maeve_verify_configured_chat true` to opt in. Without that flag it is skipped; ordinary CI and launch tests require no credentials or paid requests.

The live logo uses vector geometry in lib/widgets/eclipse_geometry.dart. After editing it, run dart run tool/export_eclipse_icons.dart from this module to update Android launcher vectors; CI checks the generated files with --check. The idle mark has no continuous animation. Reduced motion and hidden pages freeze the executing mark.

For local visual review, run tool/preview_test.dart with MAEVE_PREVIEW_FONT pointing to a local CJK font. It renders actual widgets and captures themes, all current pages and their scrolled ends under build/. The local font and screenshots are not packaged.

tool/eclipse_preview_test.dart exports actual static and executing vector frames without requiring a font. These previews are isolated from device model configuration and do not send a model request.
