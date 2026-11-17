# Anime Theme Collection — 12 Presets

**Status:** specification. No artwork is bundled and no preset is implemented.

## 0. Asset provenance — read this first

Nine images were supplied with this brief. **They are not yet cleared for redistribution.**

Three of them carry another artist's mark, visible in the image itself:

| Supplied image | Visible mark | Implication |
| --- | --- | --- |
| Windmill (primary reference) | `SNIPER` | third-party signature |
| Samurai ink | `Dream Render` | third-party signature |
| Sakura night | `AFERA XV` | third-party signature |

The brief is explicit: *"Do not scrape random anime images or redistribute copyrighted wallpapers
without permission."* A visible signature is strong evidence the work belongs to someone else.

**Therefore this collection is specified, not shipped.** Three acceptable routes, in order of
preference:

1. **Original artwork commissioned or generated for this project**, with the rights recorded.
2. **Explicit written permission** from each artist, recorded in `NOTICE.md` with the grant.
3. **Palette-only presets** — the measured colours below are enough to ship all twelve themes with
   no artwork at all. This is the only route that is unblocked today.

Until one is chosen, no image goes into `standard` or `fdroid`. The palettes below were measured
from the supplied images with a script, so the presets are ready the moment artwork is cleared.

## 1. Measured palettes

Average colour per horizontal third, plus overall mean and relative luminance (WCAG formula).
L < 0.18 means a dark theme that suits off-white labels.

| Supplied image | Whole | L | Top | Mid | Bottom |
| --- | --- | ---: | --- | --- | --- |
| Rainy Tokyo (dim street) | `#232222` | 0.016 | `#424040` | `#191818` | `#0D0E0E` |
| Dark Samurai (ink) | `#75746F` | 0.174 | `#B1AA99` | `#646664` | `#4A4D4F` |
| Midnight Windmill | `#283031` | 0.028 | `#1D2128` | `#313A40` | `#2B352C` |
| Pastel Dreams (girl, flowers) | `#D0B598` | 0.487 | `#E2CDB5` | `#CCAE8F` | `#C2A483` |
| Sakura Night | `#534169` | 0.066 | `#59488C` | `#5B476B` | `#463345` |
| Snowfall (forest) | `#5C7E7E` | 0.187 | `#3F676D` | `#5D7E82` | `#77948C` |
| Anime Sunset | `#925034` | 0.121 | `#886749` | `#CF562A` | `#5D3328` |
| Moonlit cabin | `#282924` | 0.022 | `#1E1E24` | `#2F2C2C` | `#2B321D` |
| Blue Horizon (rainy road) | `#11406E` | 0.049 | `#165494` | `#123F65` | `#0B2B50` |

Eight of nine are dark; only **Pastel Dreams** is light. That decides label colour per preset rather
than globally.

## 2. The twelve presets

Each entry gives the wallpaper source (or "palette only"), the derived keycap/text colours, and the
accent. Contrast for label-on-keycap is checked by a unit test over the shipped values.

### 1. Midnight Windmill — primary reference
Dark cinematic scenery, deep blue-grey sky, muted green ground, moonlit highlights.
- Background `#1D2128` → `#2B352C` (vertical gradient from the measured thirds)
- Keycap `#283031` at 82% opacity · Label `#E6E9EC` · Border `#3A444A`
- Accent (special keys) `#7E9AA8` · Toolbar icon `#8A98A2`
- Dim 0.18 · Blur 8 dp · Corner 12 dp
- No neon, no glow — the brief forbids both for this one.

### 2. Sakura Night
- Background `#59488C` → `#463345` · Keycap `#4A3A5C` @ 80%
- Label `#F2E9F5` · Accent `#C77FB0` · Toolbar `#B9A3C9`

### 3. Dark Samurai
- Background `#B1AA99` → `#4A4D4F` · Keycap `#3A3C3E` @ 85%
- Label `#EDEAE3` · Accent `#9E3B3B` (muted crimson, not bright red)

### 4. Blue Horizon
- Background `#165494` → `#0B2B50` · Keycap `#123F65` @ 82%
- Label `#DCE9F5` · Accent `#5C9BD6`

### 5. Rainy Tokyo
- Background `#424040` → `#0D0E0E` · Keycap `#1A1A1A` @ 88%
- Label `#D8D8D8` · Accent `#8C5A3C` (the warm sign glow, desaturated)

### 6. Moonlit Forest
- Background `#1E1E24` → `#2B321D` · Keycap `#24261F` @ 85%
- Label `#E2E4DA` · Accent `#7F9A5E`

### 7. Anime Sunset
- Background `#886749` → `#5D3328` · Keycap `#4A2A22` @ 82%
- Label `#FBEBDD` · Accent `#E0793F` (the measured cloud orange `#CF562A`, lightened for text use)

### 8. Snowfall
- Background `#3F676D` → `#77948C` · Keycap `#4E6F73` @ 78%
- Label `#F4FAFA` · Accent `#BBD6D2`

### 9. Pastel Dreams — the one light theme
- Background `#E2CDB5` → `#C2A483` · Keycap `#F3E8DA` @ 90%
- Label `#4A3B2E` (dark text — the only preset that needs it) · Accent `#C98F86`

### 10. Shadow Realm
Monochrome, from the darkest regions of the supplied set.
- Background `#141414` → `#1E1E1E` · Keycap `#1A1A1A` @ 90%
- Label `#CFCFCF` · Accent `#6E6E6E`

### 11. Ocean Memories
- Background `#123F65` → `#0B2B50`, desaturated toward grey-blue
- Keycap `#22415C` @ 80% · Label `#E3EDF3` · Accent `#6FA8B8`

### 12. Starlight Journey
Night sky with violet accent, from the Moonlit cabin palette plus the Sakura violet.
- Background `#1E1E24` → `#463345` · Keycap `#2A2733` @ 85%
- Label `#E9E6F0` · Accent `#9C86C4`

## 3. Presets without artwork

For route 3 (palette-only), each preset ships as a skin JSON with `img` omitted. The engine falls
back to the gradient from `background`, which is exactly what the `fdroid` flavour already does for
the existing ten skins. This means:

- All twelve presets are usable in **both** flavours today.
- The visual difference between flavours is only whether the artwork is drawn.
- No preset is ever "broken" — a missing image degrades to its palette.

## 4. Provenance record (to be completed before any artwork ships)

| Asset | Source | Author | Licence / permission | Cleared? |
| --- | --- | --- | --- | --- |
| windmill | supplied by project owner | marked `SNIPER` | unknown | **no** |
| samurai | supplied | marked `Dream Render` | unknown | **no** |
| sakura night | supplied | marked `AFERA XV` | unknown | **no** |
| rainy street, pastel, snowfall, sunset, cabin, blue road | supplied | unsigned | unknown | **no** |

Every row must read "cleared" with a recorded grant before the file enters the repository.

## 5. Acceptance criteria
1. Twelve presets exist as skin JSON, each with all five new schema groups.
2. Every preset renders without artwork (palette fallback) and with it, if cleared.
3. Label-on-keycap contrast ≥ 4.5:1 for all twelve, asserted in a unit test.
4. Pastel Dreams is the only preset using dark labels, and that is deliberate.
5. No artwork is committed until its provenance row is cleared.
