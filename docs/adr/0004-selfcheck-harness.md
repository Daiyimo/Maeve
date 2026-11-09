<!-- docs-check: spans-off — Historical ADR; names describe the decision record -->
# ADR-0004 · Extend `SelfCheck` into the official acceptance harness

**Status:** Proposed · **Date:** 2026-11-06 · **Spec:** §13.7

## Context

The upstream project already ships a 12-item compatibility self-check
(`core/selfcheck/{SelfCheck,SelfCheckRunner,SelfCheckReport}.kt`): device, permissions,
helper process, virtual display, opening apps, screen reading, tapping/typing, sustained
running, main screen undisturbed, built-in browser, sandbox, second app copy. Each item has
a 5–25 s limit, the whole run has a 90 s limit, the report is passed through
`Diagnostics.sanitize` (no accounts, app lists or serial numbers), and `AndroidSelfCheck`
uses the **same** `SharedShell` / `AgentDisplay` / `AndroidScreen` paths as real tasks.

Q7 asks whether R1 must work fully on ordinary (non-StepOS) Android. Answering that with
ad-hoc adb commands would be fragile and would duplicate an existing, better instrument.

A separate historical note says "putting an Activity on a StepOS virtual display crashes
system_server (Transition NPE, 5/5)". That finding belongs to **GAgent's `vd_*` self-ADB
route**, not to Maeve's `ShellService` (which uses `FLAG_OWN_FOCUS |
FLAG_STEAL_TOP_FOCUS_DISABLED`); the real device has four successful `done` results from the
virtual-display path. Conclusions must not be extrapolated across unrelated implementations.

## Decision

Extend `SelfCheck` with three probes and make the extended report the M3/M4 acceptance
artifact:

| probe | pass condition |
|---|---|
| amoo mailbox | directory readable/writable; `skill.json` = `maeve-bridge` v3; a test request reaches a terminal `out/` state |
| MCP endpoint | loopback `curl` returns 401 (server alive); with LAN enabled a desktop client can `initialize` |
| long-task residency | a ≥60 s background-display task stays alive, screen stays on, main-screen focus is not stolen |

Also re-verify the virtual-display chain after changing `applicationId` and signing key —
platform-signature gates, SELinux labels and helper-process binding may all change.

## Consequences

Adds three checks to a well-tested harness instead of a second verification stack. If the
virtual-display chain does crash under the new identity, fall back to the a11y foreground
route and record it as a **known limitation in the report** — never silently.

## Acceptance

- [ ] Extended report is green on PZ261A / Android 16 and on one non-StepOS device.
- [ ] Report stays sanitised and pasteable into a GitHub issue.
- [ ] Virtual-display regression has an explicit pass/fail line, not an assumption.
