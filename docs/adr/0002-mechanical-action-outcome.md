<!-- docs-check: spans-off — Historical ADR; names describe the decision record -->
# ADR-0002 · Mechanical action outcomes, but not byte-exact screenshot diff

**Status:** Proposed · **Date:** 2026-11-06 · **Spec:** §13.5

## Context

A mobile-GUI reference implementation settles each action with cheap mechanical facts
(`DISPATCHED → SETTLED / REJECTED / FAILED / RECOVERED`, `screenshot_exactly_same`,
`foreground_identity_changed`) and uses three bounded anti-loop tiers. Maeve already has
`OperationGuards.unchangedFrames` (`NO_EFFECT` → counter, warn at 2, stop at ≥3),
`GuardRules.NO_EFFECT_LADDER`, `StopRules`, `treeBlind` and `progressVersion`.

Crucially, the reference's "screenshot unchanged" test is a **base64 string equality** on
JPEG payloads. On the very same physical device (`ZYPZ261A01M68700159`), a static screen
produces identical MD5s while an animated game screen produces a different image every
frame — the whole "strong no-effect evidence" chain silently degrades. Android 16 also
throws an NPE on non-ASCII `input text`, which Maeve avoids by using a11y `SET_TEXT`.

## Decision

1. Keep Maeve's existing guard ladder. Add an explicit `ActionOutcome` enum and document
   that "screen did not change" **does not** mean "task failed semantically".
2. Use `(package, activity)` changes from `AndroidScreen.foregroundApps` as a
   `progressVersion` signal to reduce false stalls.
3. Add the missing middle anti-loop tier: soft hint → rewrite the action (WAIT / BACK /
   refocus) → reject dispatch. Every tier is bounded and ends in ABORT.
4. **Do not** adopt byte-exact screenshot comparison. If perceptual comparison is ever
   added, use a perceptual hash / region diff and use the **screenshot's real dimensions**
   (on this device a landscape game can report 2670×1200 while `wm size` says 1200×2670).
5. Do not vendor `yadb` (LGPL-3.0); a11y `SET_TEXT` already solves Chinese input.

## Consequences

Slightly more guard state, but no extra VLM calls. Avoids a known, silent failure mode on
animated pages. Keeps the codebase free of LGPL binaries.

## Acceptance

- [ ] A ≥20-step run on an animated page never ends with a false `no_progress` stop.
- [ ] Guard decisions carry machine-readable reasons in the run record.
- [ ] No `yadb` binary or LGPL dependency appears in the APK.
