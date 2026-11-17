# Theme Studio — LLD

**Status:** design only. Nothing implemented.

## 1. What exists to build on

`ThemeUi.kt` already provides a theme gallery with a preview dialog; `Skins.kt` parses skin JSON;
`ThemeOptions.kt` derives a palette; `SettingsAppearance.kt` is the existing settings page.

**This is an upgrade of an existing screen, not a new app.** The brief says so, and the code
supports it.

## 2. Capability matrix

| # | Capability | Status | Plan |
| ---: | --- | --- | --- |
| 1 | Live keyboard preview | **Partially** — gallery preview dialog exists, but it is a static palette card, not the real keyboard | Reuse the real key renderer (`KeyView.kt`) at reduced scale |
| 2 | Select a preset | **Already implemented** | — |
| 3 | Import a custom wallpaper | **Missing** | SAF `OpenDocument`, copy into `filesDir/themes/img/` |
| 4 | Wallpaper position and scale | **Missing** — no schema keys | new `wallpaper` group (see engine LLD) |
| 5 | Background opacity | **Partially** — `alpha`/`opacity` exist for keys, not for the wallpaper layer | add `dim` |
| 6 | Dimming and blur | **Partially** — `blur` exists, `dim` does not | add `dim` |
| 7 | Keycap colours | **Already implemented** | expose in UI |
| 8 | Text colours | **Already implemented** | expose in UI |
| 9 | Special keys | **Already implemented** (`func`, `digit`, `enter`, `backspace`) | expose in UI |
| 10 | Toolbar appearance | **Missing** — no `toolbar` key | new group |
| 11 | Corner radius | **Already implemented** (`corner`) | expose in UI |
| 12 | Spacing | **Partially** — key height is a setting; per-theme spacing is not | add `space`/`gap` |
| 13 | Shadows and borders | **Already implemented** (`shadow`, `border`, `edge`) | expose in UI |
| 14 | Save custom themes | **Missing** | `UserTheme` JSON in `filesDir/themes/` |
| 15 | Duplicate a theme | **Missing** | copy + new id |
| 16 | Rename | **Missing** | edit `name` |
| 17 | Reset to defaults | **Partially** — per-setting resets exist | per-theme reset |
| 18 | Export / import configuration | **Missing** | SAF `CreateDocument` / `OpenDocument` |

**Seven capabilities exist already; the work is exposing them plus six new ones.**

## 3. Data model

```kotlin
data class UserTheme(
    val id: String,                 // content hash of the base preset + overrides
    val name: String,
    val basePreset: String,         // e.g. "midnight_windmill"
    val overrides: Map<String, Any>,// sparse: only what the user changed
    val wallpaper: WallpaperRef?,   // file name in filesDir/themes/img
    val createdAt: Long,
    val schemaVersion: Int = 1
)

data class WallpaperRef(
    val file: String,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val anchor: String = "center"
)
```

**Sparse overrides, not a full copy.** Storing only what changed means a future preset improvement
reaches existing user themes, and the file stays small.

## 4. Storage and migration

| Item | Location | Note |
| --- | --- | --- |
| Theme definitions | `filesDir/themes/<id>.json` | one file per theme |
| Wallpapers | `filesDir/themes/img/<hash>.<ext>` | content-hashed, deduplicated |
| Active theme id | existing `KeyboardPrefs` | one new string key |

`schemaVersion` is present from day one so a later format change can migrate. **No existing setting
changes shape**, so no migration is needed for this release; the only new key is the active theme id,
which defaults to the current behaviour when absent.

Wallpaper import limits: reject > 8 MB, reject non-image MIME, downscale to the device's keyboard
view size on import (not on every draw).

## 5. UI structure

```
Settings → Appearance → Theme Studio
├─ Presets            (grid, 12 + user themes)
├─ Live preview       (real keyboard at ~60% scale, updates as you edit)
├─ Wallpaper          (choose / import / scale / offset / dim / blur)
├─ Colours            (keycap, label, border, special keys, toolbar, strip)
├─ Shape              (corner radius, spacing, shadow)
└─ Actions            (save, duplicate, rename, reset, export, import)
```

**Preview fidelity is a requirement, not a nicety:** the brief forbids "a separate inaccurate
mockup". The preview must render with the same `KeyView` and `KeyPalette` the keyboard uses, driven
by the same `Skin` object. If the preview and the keyboard disagree, the preview is wrong.

## 6. Import validation (the risk surface)

Imported theme JSON is untrusted input. Reject:

- Unknown schema version (forward-incompatible).
- Colours that do not parse as `#RRGGBB` / `#AARRGGBB`.
- `img` / `background` values that are URLs or absolute paths — **only a local file name is
  accepted**, so a shared theme cannot become a network fetch or a path traversal.
- Files above a size cap (64 KB of JSON).
- More than N wallpapers referenced (one per theme).

Validation is a pure function (`ThemeImport.validate(json) -> Result<UserTheme>`), unit-tested with
malformed inputs — not inline in a Compose click handler.

## 7. Privacy

Themes contain no user text. The only privacy-relevant action is importing a wallpaper from SAF,
which grants per-URI access without a storage permission. Wallpapers are copied into app-private
storage; nothing is uploaded, and export writes only to a location the user picks.

## 8. Accessibility
Every colour control needs a label and a value announcement; the live preview must not trap focus;
the contrast test from the engine LLD runs against user themes too, warning rather than blocking.

## 9. Tests
Unit: import validation (10+ malformed cases), sparse-override merge, id stability, wallpaper
deduplication, contrast on user themes. Device: live preview parity with the real keyboard, import
from Downloads, export round-trip, low-memory behaviour with a large wallpaper.

## 10. Acceptance criteria
1. All 18 capabilities reachable from the Studio.
2. Preview matches the keyboard exactly (same renderer, same palette).
3. A malformed import is rejected with a message and never crashes.
4. Imported JSON cannot reference a remote or absolute path.
5. Saved themes survive restart; export/import round-trips without loss.
6. Existing themes and settings are untouched by the upgrade.
