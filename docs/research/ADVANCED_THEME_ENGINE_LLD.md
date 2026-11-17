# Advanced Theme Engine — LLD

**Status:** design only. Nothing here is implemented.
**Baseline:** `main` at `ea59519` (203 JVM tests, 0 failures).

---

## 1. What the engine already does (measured, not assumed)

`Skins.kt` parses a skin JSON into a `Skin`; `ThemeOptions.kt` derives a `KeyPalette`;
`PluginRepository.resolvedSkin(raw, dark)` composes the active skin over user settings.

Keys the parser actually reads today:

```
background/img   blur   frost   glass   liquid   grain   dotmask   dispersion
border   edge   bevel   corner   inset   highlight   glow   shadow   opacity
pressed   popup   size   accent   alpha   amount   angle   dark   mode
colors   keys   key   buttons   chips   emoji   enter   backspace   func
digit   cell   label   font   italic   cursive   material   globe   keyboard   letter   disp
```

Ten bundled anime skins (`anime_crimson`, `anime_ego`, `anime_flame`, `anime_ink`, `anime_manga`,
`anime_rose`, `anime_sage`, `anime_sky`, `anime_split`, `anime_violet`) plus `classic.json`.

**This is already a theme system, not a wallpaper switcher.** It supports per-keycap colour and
opacity, corner radius, borders, pressed state, shadows, blur, frost, glass, bevel, grain, dot-mask
and popup-key styling. Any plan that describes building these from scratch is wrong.

## 2. Gap analysis against the 20-point theme requirement

| Requirement (Phase 1.2) | Status | Evidence |
| --- | --- | --- |
| Wallpaper / background artwork | **Already implemented** | `img`, `background` |
| Primary / secondary background colour | **Already implemented** | `background`, `colors` |
| Keycap background colour and opacity | **Already implemented** | `key`, `opacity` |
| Keycap text colour | **Already implemented** | `label` |
| Keycap border colour | **Already implemented** | `border`, `edge` |
| Keycap corner radius | **Already implemented** | `corner` |
| Keycap shadow | **Already implemented** | `shadow` |
| Keycap pressed state | **Already implemented** | `pressed` |
| Special-key colours | **Already implemented** | `func`, `digit`, `enter`, `backspace`, `globe` |
| Shift / backspace styling | **Already implemented** | `backspace`, `keys` |
| Enter-key styling | **Already implemented** | `enter` |
| Spacebar styling | **Partially** — shares `func`/`key` styling; no dedicated key | no `space` key in schema |
| Suggestion-strip styling | **Missing** | no `suggest` key parsed |
| Toolbar icon colours | **Missing** | no `toolbar` key parsed |
| Popup-key appearance | **Already implemented** | `popup` |
| Emoji / symbol panel styling | **Already implemented** | `emoji` |
| Selected-state styling | **Partially** — `pressed` covers keys, not panel selection | no `selected` key |
| Optional background blur | **Already implemented** | `blur` |
| Optional background dimming | **Missing** | no `dim` key |
| Light/dark compatibility | **Already implemented** | `dark`, `mode` |

**Net new schema work: four keys — `suggest`, `toolbar`, `dim`, `space` — plus wallpaper
positioning (`scale`, `offsetX`, `offsetY`) which the schema also lacks.**

This reframes the project: the engine needs **five additions**, not a rewrite.

## 3. Proposed additions

### 3.1 Schema extension (backward compatible)
```jsonc
{
  "suggest": { "bg": "#1A1C22CC", "fg": "#E8E6F0", "accent": "#B9A6FF", "radius": 10 },
  "toolbar": { "icon": "#9AA3B2", "iconActive": "#B9A6FF" },
  "space":   { "bg": "#242832", "fg": "#C9CFDA" },
  "dim":     0.25,              // 0..1, applied over the wallpaper only
  "wallpaper": { "scale": 1.0, "offsetX": 0.0, "offsetY": 0.0, "anchor": "center" }
}
```
Unknown keys are already tolerated by the parser (it reads known fields only), so old skins keep
working and new skins degrade to defaults on older builds. **No migration needed for user data**;
`ThemeOptions` needs a defaults layer for the five new groups.

### 3.2 Rendering pipeline

```
skin JSON ──> Skin ──> ThemeOptions ──> KeyPalette ──> Compose
                          │
                          └─ wallpaper layer: decode → downscale → blur (if set) → dim → draw
```

Constraints, all of which the current code must keep honouring:

- **Decode once per wallpaper, not per frame.** `coil` 2.6.0 is already a dependency; decode to a
  downscaled bitmap sized to the keyboard view, cache by (uri, target size).
- **Blur is the expensive part.** Use `RenderEffect`/`Modifier.blur` on API 31+; below that,
  pre-blur the bitmap once off the main thread rather than per frame. A per-frame blur on API 26–30
  will drop frames on mid-range devices.
- **Resize and rotation must not re-decode.** Cache key includes view size; a rotation produces a
  new size and therefore one new decode, not a decode per frame.
- **No bitmap allocation in the draw path.**

### 3.3 Fallback chain (mandatory)

```
skin wallpaper → user wallpaper → solid palette background
```
If artwork is missing — which is the **normal** case in the `fdroid` flavour, and the case for any
user theme referencing a deleted image — the palette must render. `Skins.kt` already falls back;
the new wallpaper layer must not break that.

### 3.4 Theme transitions
Cross-fade the palette over ~180 ms on theme change only, never on keyboard show (which must be
instant). Animate colour, not the bitmap, to keep it cheap.

## 4. Performance budget

| Path | Budget | Measurement |
| --- | --- | --- |
| Keyboard show with wallpaper | ≤ 1 frame over no-wallpaper | Macrobenchmark frame timing |
| Theme switch | ≤ 3 frames | Macrobenchmark |
| Rotation | one decode, no jank | trace section |
| Memory: wallpaper | ≤ 8 MB decoded | `Bitmap` size assertion in a unit test on target dimensions |

## 5. Accessibility
- Every palette must reach 4.5:1 for key label vs keycap. A unit test iterates the bundled skins and
  asserts it — this is the guard that stops a pretty theme from being unreadable.
- Wallpaper must never sit behind text without the dim/blur layer keeping contrast; the contrast
  test runs against the *composited* colour, not the raw palette.

## 6. Tests
Unit: schema parse for the five new keys, defaults when absent, old skin unchanged, contrast over
all bundled skins, wallpaper cache key correctness. Device: frame timing with a 4K wallpaper, blur
on API 30 vs 31+, rotation with a wallpaper set.

## 7. Risks
| Risk | Mitigation |
| --- | --- |
| Blur drops frames on API 26–30 | pre-blur once, cache; never per frame |
| Wallpaper memory blow-up on 4K images | downscale on decode, 8 MB cap |
| New schema keys break old skins | parser already ignores unknown keys; defaults layer |
| Artwork licensing (see `ANIME_THEME_COLLECTION.md`) | provenance recorded per asset; `fdroid` ships none |
