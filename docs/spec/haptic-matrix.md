<!-- docs-check: spans-off — Android and Compose haptic API names used as counter-examples -->
# Haptic engine — M2 implementation contract

> Source of truth: [SPEC.md](../../SPEC.md) §4. dsh-Go is a Flutter app using
> `selectionClick / lightImpact / mediumImpact / heavyImpact`. Maeve is native Compose, so
> those APIs do not exist here. **Do not wrap `LocalHapticFeedback` and call it done** —
> Compose exposes only `LongPress` and `TextHandleMove`, which cannot express the semantics.

## 1. Single entry point

```kotlin
interface HapticEngine {
    fun emit(level: HapticLevel)
    val enabled: StateFlow<Boolean>          // Settings > Haptic feedback
}

enum class HapticLevel { Tick, Light, Medium, Heavy, Reject }
```

Implementation: `VibratorManager` + `VibrationEffect` on API 30+, falling back to
`HapticFeedbackConstants` on older devices. Check `Vibrator.areEffectsSupported` before each
effect class; if unsupported, degrade silently to the next weaker level and finally to no
feedback. Requires `android.permission.VIBRATE`. When the system haptic setting is off, the
engine is a no-op — never throw, never log a warning per tap.

| Level | Meaning | Android | Fallback |
|---|---|---|---|
| `Tick` | select / tap | `EFFECT_TICK` | `CLOCK_TICK` / `VIRTUAL_KEY` |
| `Light` | confirm / send / done | `EFFECT_CLICK` | `CONTEXT_CLICK` |
| `Medium` | stop / long-press / takeover / remote arrival | `EFFECT_HEAVY_CLICK` | `LONG_PRESS` |
| `Heavy` | irreversible action approved | `EFFECT_DOUBLE_CLICK` or waveform | `LONG_PRESS` ×2 |
| `Reject` | **Maeve-only**: blocked by a guard, or authorisation lost | `EFFECT_REJECT` | `REJECT` / none |

`Reject` must feel qualitatively different from `Heavy`: "the system refused" is not "you
confirmed something dangerous". If the device cannot express it, prefer silence over a
positive-sounding click.

## 2. Feedback matrix

| Event | Level | Note |
|---|---|---|
| Any pressable component | `Tick` | One shared `Pressable` wrapper; 90 ms reveal delay while scrolling |
| Send a goal (R1) | `Light` | |
| Copy / expand tool card / segment change / slider snap | `Tick` or `Light` | Emit **only when the value actually changed** |
| Confirmation gate appears | `Light` | Foreground overlay; main screen stays usable |
| Gate approved (before an irreversible action) | `Heavy` | Requires 1500 ms hold; release cancels |
| Guard blocks / takeover requested | `Reject` | Never reuse `Heavy` |
| Stop a task / open message menu | `Medium` | |
| Takeover succeeded (control handed back) | `Medium` ×2 short | Maeve-specific |
| Remote task arrived (R2: file bridge or MCP) | `Medium` once | Must be noticeable even outside the app |
| Background display executes one action | **none** | No per-step buzzing |
| Task finished | `Light` | |
| Task failed / cancelled | `Medium` | Distinct from success |
| Bridge handshake / MCP client first connect | `Tick` | Once only, not on every poll |
| Accessibility revoked / execution degraded | `Reject` | Always paired with a visible block notice |

## 3. `HoldToConfirm`

1500 ms ring progress; one `Tick` on pointer-down, one `Heavy` on completion; maximum two
emissions per hold; cancellation emits nothing. Use raw pointer events rather than a gesture
detector so an accessibility pass or an accidental touch cannot complete it.

Used for: lifting execution restrictions, approving payment-class actions, enabling
unattended mode.

## 4. Consolidating the upstream calls

Maeve scatters four `LongPress` feedback calls. All must route through `HapticEngine`:

| File | Current |
|---|---|
| `app/src/main/java/com/daiyimo/maeve/ui/MessageMenu.kt` | `LocalHapticFeedback` `LongPress` |
| `app/src/main/java/com/daiyimo/maeve/ui/ChatDrawer.kt` | `LocalHapticFeedback` `LongPress` |
| `app/src/main/java/com/daiyimo/maeve/ui/VoiceHold.kt` | several `LongPress` |
| `app/src/main/java/com/daiyimo/maeve/cap/phone/a11y/OverlayHost.kt` | View `HapticFeedbackConstants.LONG_PRESS` |

`VoiceHold.kt` belongs to the voice capability, which is disabled in product `v0.1.0`; if
that module is removed entirely, its entry disappears with it. Otherwise fix it like the rest.

## 5. Acceptance (M2) — implemented 2026-11-06

- [x] A repo-wide search finds **no** `performHapticFeedback` / `LocalHapticFeedback` /
      `HapticFeedbackType` / `HapticFeedbackConstants` outside `HapticEngine` itself.
      The four upstream call sites (`MessageMenu`, `ChatDrawer`, `VoiceHold`, `OverlayHost`)
      now route through `MaeveTheme.haptics`.
- [x] With the Settings switch off: zero emissions — `HapticEngineTest.disabledEngineEmitsNothing`
      and `refreshPicksUpHapticSettingChanges` use a `FakeActuator`.
- [x] Without effect support: no crash, no exception, silent degrade —
      `unsupportedEffectsDegradeSilentlyWithoutCrashing`. `Heavy` deliberately stays silent
      rather than faking an approval.
- [x] `Reject` is distinct from `Heavy`: `Reject` is a two-pulse descending waveform, never a
      predefined click (`rejectIsNeverAPositiveClick`). Devices without amplitude control
      stay silent instead of emitting a positive-sounding click.
- [x] `HoldToConfirm` uses raw pointer events only (no `clickable` / `combinedClickable`), so a
      synthetic `click` cannot complete it. 1500 ms is deliberately **not** shortened by
      reduced motion — that would make dangerous actions fire on a single tap.

Implementation: `ui/haptic/HapticEngine.kt`, `ui/haptic/HoldToConfirm.kt`,
`ui/theme/MaeveMotion.kt`, `ui/theme/MaeveShapes.kt`, `ui/theme/Tokens.kt`,
`ui/theme/Theme.kt` (`MaeveTheme.haptics`), `ui/components/Controls.kt` (press scale + Tick),
`ui/components/Rows.kt`, `ui/ChatScreen.kt` (send `Light`, refuse `Reject`, approve `Heavy`,
example rows `Tick`), `ui/AdvancedSection.kt` (clear-all hold), `ui/SettingsScreen.kt`
(Settings › Haptic feedback switch), `AndroidManifest.xml` (`VIBRATE`).
