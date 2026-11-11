# Maeve Flutter UI

Flutter is the only application interface. Android owns the existing Mika-derived task engine, capabilities, persistence, provider credentials and platform overlays. See [host boundary](../FLUTTER_MIGRATION.md) and [source/history audit](../docs/dev/flutter-capability-audit.md).

## Current surfaces

- Real chat and conversation search, pinning and deletion through maeve/chat.
- Model configuration and selection through maeve/settings and the existing native ProviderStore. Keys are not returned to Flutter.
- Android permission status and system authorization targets, plus native helper pairing notifications.
- Persistent themes, six palettes, reduced transparency and haptics.
- About, the original awakening-eye mark, and packaged legal notices.

The account, billing, high/medium/low tiers, plugin market, remote host, terminal, web, demo memory/run pages, fake authorization controls and temporary archive are removed. Native capabilities behind those labels remain in Android/core; an unconnected UI entry is not evidence that the underlying capability is absent.

## Layout and material

The idle home fits normal phone viewports without scrolling; three compact starts leave space between the hero and composer. Large text and the keyboard can scroll when needed. Chrome and sheets use bounded backdrop sampling; scrolling cards use static directional light to avoid GPU feedback stripes. System reduced motion and high contrast remain supported.

## Validation

Run flutter/tool/verify.sh from the repository or the Flutter analyzer and widget tests from this module. Tests cover chat behavior, native settings contracts, persistence, endpoint input validation, all eight real settings destinations, narrow/wide/large-text layouts and six light/dark palettes. No screenshot font is required by CI.

For local visual review, run tool/preview_test.dart with MAEVE_PREVIEW_FONT pointing to a local CJK font. It renders actual widgets and captures themes, all current pages and their scrolled ends under build/. The local font and screenshots are not packaged.
