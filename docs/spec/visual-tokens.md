# Visual tokens — M2 design contract

> Source of truth: [SPEC.md](../../SPEC.md) §2.2 and §3. This file is the hand-off between
> design and implementation. **All values below are design decisions, not code.** dsh-Go is
> AGPL-3.0; only its decisions (colour-derivation rules, contrast guards, motion durations)
> may be consulted. Re-implement everything in the app's own stack (Flutter now, Compose for
> the surfaces still in service); copy no source, icon, font or asset.
>
> **Status (2026-11-09):** the Flutter shell implements its own tokens in
> `flutter/lib/design/maeve_tokens.dart`; this contract remains the design reference until
> the Flutter theme reaches parity.

## 1. Colour derivation (OKLCH)

Do **not** use `ColorScheme.fromSeed` or `dynamicColorScheme`. Build the scheme by hand.

| Rule | Value | Why |
|---|---|---|
| Hue change | Rotate hue at **constant lightness** | Neutral surfaces must not get brighter/darker when the accent changes. |
| Max chroma | `0.13` | Keeps every preset quiet; high saturation reads as an alert. |
| Lightness band | `0.30–0.60`, dark theme `+0.234` | Predictable contrast without per-colour fudging. |
| Out-of-gamut | Reduce chroma only, **never lightness** | sRGB clipping otherwise shifts perceived brightness. |
| Non-text contrast | ≥ `3:1` | Icons, hairlines, focus rings. |
| Text contrast | ≥ `4.5:1` | WCAG AA for body text. |
| Usage ratio | 70 neutral / 25 text / 5 accent | Accent is a signal, not a surface. |
| Elevation | `surfaceTint = transparent`, `elevation = 0` | Depth comes from hairlines and outer shadow, not tint. |

Wallpaper picking (API 31+) reads `system_accent1_500/600/200` and `system_neutral1_500`
and returns **hue + chroma only** — it is not Material You. Hide the option below API 31.
Custom colour-wheel drags are throttled to ~100 ms before calling `derive()`.

## 2. Presets

| id | Name | Light seed | Dark seed | Note |
|---|---|---|---|---|
| `reverie` | 雾紫 (**default**) | `#6E6296` | `#B3A8D6` | reveries |
| `dolores` | 晨金 | `#8A6A3E` | `#D8BC93` | first release codename |
| `maze` | 沙褐 | `#7A6248` | `#C9AE93` | |
| `pearl` | 珠白 | `#4F6472` | `#AFC3CE` | |
| `sublime` | 深靛 | `#3A4A7A` | `#9BAEDD` | internal codename only — never a product name |
| `graphite` | 石墨 | `#2C2E32` | `#E5E8ED` | near-achromatic |

`AccentSource = Preset | Custom | Wallpaper`; all three go through the same `derive()` then
`guard()`. There is no separate code path for custom colours.

## 3. Surfaces, shape, motion

Four surface tiers, each with `fill / blurSigma / saturation / hairline / rim / outerShadow`:

| tier | alpha | blur | saturation |
|---|---|---|---|
| `chrome` | 0.70 | 18 | 1.3 |
| `sheet` | 0.82 | 24 | 1.4 |
| `card` | 0.62 | — | — |
| `pill` | 0.55 | — | — |

- Only **outer** shadows; no inner glow, no elevation tint.
- One full-screen backdrop per page; the backdrop veil fades only inside its band. Switching
  tabs must not rebuild or flash the backdrop.
- When `blurEnabled = false`, use an opaque `fallbackFill` and **do not create** a blur layer.
- Grouped backdrop sampling: one `BackdropFilter.grouped` for a group of glass siblings.

Tokens:

```
radius   xs6 sm10 md14 lg20 xl28          (continuous curvature)
spacing  2 4 8 12 16 20 24 32            (multiples of 4)
hairline 0.5px
readingWidth 720dp
motion   press90 quick160 standard240 collapse280 emphasized340
         sheetIn380 glassIn120 breath2400   (ms; all → 0 when reduced motion is on)
```

## 4. Icons

Motif: **two offset rings + one off-centre light point** — outer ring = main screen, inner
ring = hidden display, the dot = the step currently running. Light theme uses foggy
violet-grey; dark theme uses low-saturation indigo. No high saturation, no glow effects.

Adaptive icon: 108 dp grid, 72 dp visible area, plus a `monochrome` layer for Android 13+
themed icons. Four launcher variants via `activity-alias`:

| variant | background | rule |
|---|---|---|
| `reverie` (default) | foggy violet gradient + double ring | only alias enabled in manifest |
| `pearl` | pearl + single ring | |
| `night` | deep indigo + light point | |
| `mono` | graphite + monochrome glyph | |

Switching rules (easy to get wrong): **enable the new alias first, then disable the old**;
disable with `STATE_DEFAULT (0)`, never `STATE_DISABLED (2)`; defer disabling the original
alias until its task is no longer visible or Android clears the already-open task.

In-app tile styles — `duotone` (default) / `geometric` / `glass` — affect only home tiles and
tool cards, never launcher icons.

## 5. Deliverables and acceptance

`ui/theme/`: `MaeveColors` (OKLCH + presets + derive/guard), `MaevePalette`,
`MaeveSurfaces`, `MaeveShapes`, `MaeveMotion`, `MaeveSemantic`, `MaeveTheme`.
`res/mipmap-anydpi-v26/`: four adaptive icons + monochrome. `app/ui/LauncherIcon.kt`.

Acceptance (M2):

- [ ] All six presets × light/dark: text ≥ 4.5:1, non-text ≥ 3:1 — verified by a unit test
      computing contrast, not by eye.
- [ ] Switching launcher icon does not clear an already-open task.
- [ ] `blurEnabled=false` produces no blur layer and no crash on API 30.
- [ ] Reduced motion zeroes every duration.
- [ ] No dsh-Go source, string resource, drawable, font or colour literal copied.
