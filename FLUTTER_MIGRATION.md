# Flutter migration

Maeve starts directly in Flutter. All launcher aliases target MainActivity, a cached-engine FlutterActivity. The former Compose application Activity, navigation root and pages have been removed. Android still owns capabilities, task execution, persistence, notifications and platform overlays.

The current visual language defaults to purple and uses six complete light/dark atmospheres, the purple eclipse mark, bounded glass, interaction highlights and reduced-effect fallbacks. See [the design contract](docs/spec/visual-tokens.md) and [the host boundary](docs/spec/flutter-visual-parity.md).

The chat bridge uses real Android conversation state, sends, cancellation and entries, along with native execution steps, guarded preview frames, saved run details and screenshots. Capability adapters remain native. Confirmation modes use ApprovalControl and ApprovalStore; model, system authorization and appearance settings use maeve/settings and the existing native stores. The workbench is removed; settings remain reachable from the conversation drawer. See docs/dev/flutter-capability-audit.md for source/history evidence and the removal ledger.

## Validation

- Flutter analyzer, behavior tests, all-page layout checks and contrast checks.
- JVM core and Android logic tests.
- Android instrumentation APK compilation and real-device Flutter launch/recreation checks.
- Local rendered review via flutter/tool/preview_test.dart. Output lives under flutter/build/previews; external fonts are only for local rendering and do not affect CI.

Retired Compose UI tests are replaced by Flutter widget coverage. Native task, capability, overlay and file-viewer tests remain. CI uses locked dependencies and a fixed Flutter shuffle seed, saves reports on failure, and compiles the device test APK without test retries or ignored failures.
