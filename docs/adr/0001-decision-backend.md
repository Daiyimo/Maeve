<!-- docs-check: spans-off — Proposed type names; none implemented yet -->
# ADR-0001 · Reserve a `DecisionBackend` seam, ship no local model

**Status:** Proposed · **Date:** 2026-11-06 · **Spec:** §13.4

## Context

Maeve executes on the device but planning needs a model, and the upstream app requires a
user-configured LLM provider. The project must not claim "on-device / offline / 100% local"
in its copy. At the same time, `AgentLoop.UNLIMITED_STEPS` plus `MemoryContext` budgets make
long tasks a real scaling problem, and there are credible local/PC-side GUI models
(GELab-Zero-4B-preview: Apache-2.0 weights, MIT repo code; LGPL-3.0 `yadb` binary) plus a
GUI-MCP pattern of keeping screenshots on-device and uploading only semantic summaries.

## Decision

Add a pure-JVM `DecisionBackend` interface in `:core`. Product `v0.1.0` implements only
`RemoteProviderBackend` (the existing provider chain). `LanPcBackend` and `LocalVlmBackend`
are documented seams, **not** implementations; no weights, llama.cpp, QNN or ExecuTorch are
bundled. Reserve a `RollingSummary` hook in `ContextBuilder` (10-step compression / 10-step
window / 8 items per field, per GELab's config) without turning it on. Every backend still
emits actions through `ActionExecutor`; none bypasses the guard chain.

"Not implemented" is not "architecturally blocked".

## Consequences

- Adds one small interface and one insertion point in `:core`; no Android coupling.
- The privacy-preserving "semantic summary only" mode remains a future option and must not
  appear in README until implemented.
- Licence hygiene: if a backend is ever added, model weights (Apache-2.0), repo code (MIT),
  base-model terms and any bundled `yadb` (LGPL-3.0) are **four separate obligations**.

## Acceptance

- [ ] `v0.1.0` APK contains no model weights and no local inference runtime.
- [ ] No README/About copy claims offline or on-device inference.
- [ ] `DecisionBackend` has no Android imports and is covered by a `:core` unit test.
