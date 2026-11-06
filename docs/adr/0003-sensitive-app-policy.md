# ADR-0003 · Sensitive-app policy with mandatory `ASK_USER`

**Status:** Proposed · **Date:** 2026-11-06 · **Spec:** §13.6

## Context

A commercial GUI phone assistant was blocked by ~155 apps in 2026: injected taps were
classified as automation/cheating by risk-control systems, and reading protected video
output touched a security boundary. The vendor responded by shipping a "refuse-to-operate
app list" and restricting financial scenarios. Maeve is a third-party app without system
signature: it cannot read GPU buffers, cannot use `INJECT_EVENTS`, and `FLAG_SECURE`
surfaces (banking, password fields) are invisible to it.

This is a product constraint the industry has already validated, not an optional polish.

## Decision

Introduce `SensitiveAppPolicy` with an updatable list of sensitive package names
(financial / payment / identity / health / credentials):

1. Any `IrreversibleDetector` hit inside a listed app degrades to `ASK_USER` via
   `ConfirmDecision`; this composes with `HoldToConfirm` (§4.4).
2. `Unattended` is **forbidden** for listed apps, even if the user explicitly enables it in
   the connection hub.
3. The Settings page exposes the list plus import/export. Blocking is never silent — the
   reason is written to the run record.
4. `FLAG_SECURE` surfaces keep the existing `ASK_USER` fallback. Do not attempt to bypass
   secure input or protected output.

## Consequences

Some tasks in sensitive apps will require a human and cannot run unattended. That is
intentional. The policy must be described as **product self-restraint**, never as a blanket
"safe / privacy-first" claim (see the copy red lines in the naming document, appendix E.4).

## Acceptance

- [ ] A payment-class action in a listed app always produces a confirmation gate.
- [ ] Enabling `Unattended` leaves listed apps in `Reachable`.
- [ ] Every block has a visible reason and a corresponding run-record entry.
- [ ] No copy claims a general security or privacy guarantee.
