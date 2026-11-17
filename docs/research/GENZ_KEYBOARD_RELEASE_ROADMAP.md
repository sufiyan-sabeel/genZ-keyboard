# GenZ Keyboard — Repository Audit and Release Roadmap

**Status:** audit + plan. No feature in this bundle is implemented.
**Baseline:** `main` at `ea59519` — 203 JVM tests, 0 failures; both flavours build.
**Companion documents:** `ADVANCED_THEME_ENGINE_LLD.md`, `ANIME_THEME_COLLECTION.md`,
`THEME_STUDIO_LLD.md`, `FONTS_AND_TEXT_STYLES_LLD.md`, `GENZ_DICTIONARY_LLD.md`,
`EMOJI_GIF_QUOTES_LLD.md`, and the earlier `GENZ_KEYBOARD_1_1_0_LLD.md`.

---

## 1. Repository audit (Phase 0)

Every row was read from source. Classification: **AI** already implemented · **PI** partially
implemented · **MI** missing · **BR** broken · **RA** requires architectural improvement.

| Capability | Class | Evidence |
| --- | --- | --- |
| Theme engine (skin JSON) | **AI** | `Skins.kt` parses 40+ keys: blur, frost, glass, bevel, grain, dotmask, dispersion, shadow, pressed, popup, corner, border, opacity |
| Bundled anime themes | **AI** (10) | `assets/plugins/anime_*.json` |
| Theme gallery + preview | **PI** | `ThemeUi.kt` — static palette card, not the real keyboard |
| Per-keycap colour / opacity / shadow / pressed | **AI** | skin keys `key`, `opacity`, `shadow`, `pressed` |
| Suggestion-strip styling | **MI** | no `suggest` key parsed |
| Toolbar icon colour | **MI** | no `toolbar` key parsed |
| Background dimming | **MI** | no `dim` key parsed |
| Wallpaper position / scale | **MI** | no `scale`/`offset` keys |
| Spacebar-specific styling | **PI** | shares `func`/`key`; no dedicated key |
| Theme import / export | **MI** | — |
| Fonts | **PI** | `FontRepository.kt`: bundled Pixelify Sans (OFL) + one `CUSTOM` slot |
| Font catalogue / categories | **MI** | single bundled font |
| Unicode text styles | **MI** | — |
| Emoji catalogue, search, recents, suggestions | **AI** | `Emoji.kt`, `EmojiSuggest.kt`, `assets/emoji/` |
| Emoji favourites / combos / skin tones | **MI** | — |
| GIF search (Klipy/Giphy) | **AI** | `GifCatalog` in `KeyboardData.kt` |
| Built-in local GIF catalogue | **AI** | `GifCatalog.fallback(...)` filters locally |
| GIF favourites / recents / stickers | **MI** | — |
| GIF privacy control | **AI** | `GifSearchPolicy`; incognito closes the network path before any key is read |
| Prediction dictionary | **AI** | `Dictionaries.kt`, `assets/dict/*.txt`, `WordPredictor.kt` |
| GenZ glossary with meanings | **MI** | — |
| Shortcuts / quick phrases | **PI** | `Plugins.kt` `shortcuts` (cap 500), `personalShortcuts`; no user-facing phrase editor |
| Quotes | **MI** | no quote code anywhere |
| Inline autofill | **AI** | `onCreateInlineSuggestionsRequest`, `InlineAutofillPolicy` |
| Credential Manager | **MI, deliberately** | not a dependency; see `GENZ_KEYBOARD_1_1_0_LLD.md` §8 |
| Privacy architecture | **AI** | `PrivacyState`, `IncognitoTransition`, `PrivacyEffects`, `PrivacyWriteRouter`, `PluginEventPolicy` |
| Incognito preference propagation | **AI** | fixed in `d5f6c708`; listener order no longer matters |
| Typo-undo | **AI** | `UndoGuard`, `UndoAutoCorrectState` |
| Both flavours | **AI** | `standard` 10 artwork files / `fdroid` 0, enforced in CI |
| Instrumentation tests | **PI** | `ImeRegistrationTest` compiles; **never executed** (no device) |
| Lint in CI | **MI** | `checkReleaseBuilds = false`, a pre-existing material3 decision |

### Headline finding

**This is not a wallpaper app that needs a theme engine; it is a theme engine that needs a studio.**
The visual system is largely built. The real work is: five schema keys, a studio UI, a font
catalogue, and content packs. Any plan that starts by rewriting `Skins.kt` is starting in the wrong
place.

### Second finding

**Artwork is the blocking dependency, not code.** Three of the nine supplied images carry other
artists' signatures. See `ANIME_THEME_COLLECTION.md` §0 — all twelve presets are specified with
measured palettes, and all twelve can ship **palette-only** today with no licensing question.

---

## 2. Implementation sequence

Ordered by dependency and by risk retired per unit of work.

### Milestone 0 — Foundations (no user-visible change)
1. Extend the skin schema with `suggest`, `toolbar`, `dim`, `space`, `wallpaper` — with defaults so
   existing skins are untouched (`ADVANCED_THEME_ENGINE_LLD.md` §3).
2. Add the contrast unit test over bundled skins (this is what stops a pretty theme being unreadable).
3. Add the shared JSON pack reader (schema version, size cap, unknown-field tolerance) reused by
   themes, slang, quotes and sticker packs.
4. Add the wallpaper decode/cache layer with the 8 MB cap.

**Exit:** 0 test failures, both flavours build, no visual change yet.

### Milestone 1 — Themes without new artwork
5. Twelve presets as skin JSON, palette-only (`ANIME_THEME_COLLECTION.md`).
6. Studio read path: gallery shows the real keyboard preview, presets apply.

**Exit:** twelve presets usable in both flavours with no artwork.

### Milestone 2 — Studio write path
7. Save / duplicate / rename / reset user themes.
8. Wallpaper import (SAF), scale, offset, dim, blur.
9. Export / import theme JSON with validation as a pure, tested function.

**Exit:** a user can build, save and share a theme; malformed imports are rejected.

### Milestone 3 — Fonts
10. Font catalogue (8–10 families, ≤ 2 MB) with licence files and `NOTICE.md` entries in the same
    commit.
11. Font picker with previews and categories.

**Exit:** licence audit passes; no UI claims to change other apps' fonts.

### Milestone 4 — Text styles
12. `DecorativeText` transforms with mixed-script passthrough, opt-in insertion only, blocked in
    password fields and incognito.

### Milestone 5 — Content packs
13. GenZ glossary (content review required — see the editorial policy).
14. Quotes (original + public domain only; provenance mandatory).
15. Emoji favourites, combos, skin tones.
16. GIF favourites and recents; stickers only if SAF import and attribution are both done.

### Milestone 6 — Release candidate
17. Version bump, changelog, screenshots on a real device, licence audit, fresh-install and upgrade
    tests, F-Droid metadata refresh.

---

## 3. Dependency map

```
schema keys ──┬──> presets ──> studio read path ──> studio write path ──> export/import
              │                                            │
              └──> contrast test                           └──> wallpaper layer

shared JSON reader ──> themes · slang · quotes · sticker packs

FontRepository ──> font catalogue ──> (text styles need a font with the glyphs)

Emoji catalogue ──> favourites / combos / tones

GifCatalog ──> GIF favourites ──> stickers (SAF + attribution)

Dictionaries ──> glossary (separate store) ──┐
Plugins.shortcuts ───────────────────────────┴──> phrase precedence (must ship together)
```

**Hard ordering rule:** slang/phrase insertion must define its precedence against autocorrect and
shortcuts **in the same change**. Five mechanisms on one word boundary will otherwise corrupt text
silently, and precedence is far harder to retrofit once users have data.

---

## 4. Risks

| # | Risk | Impact | Mitigation |
| --- | --- | --- | --- |
| 1 | **Artwork licensing** — supplied images are third-party work | **high** — legal, and blocks the whole visual upgrade | palette-only presets ship now; provenance table must read "cleared" before any image is committed |
| 2 | Blur drops frames on API 26–30 | high — typing responsiveness | pre-blur once off-thread, cache; never per frame |
| 3 | Wallpaper memory blow-up | medium | downscale on decode, 8 MB cap |
| 4 | Phrase precedence mangles words | **high** — silent corruption | pure `resolve()` with an exhaustive pair matrix; ship with slang |
| 5 | Imported theme JSON as an attack surface | medium | reject URLs/absolute paths, size caps, pure validator |
| 6 | Font licence miss | **high** — legal | licence file + NOTICE entry in the same commit; audit step |
| 7 | Quote copyright | **high** — legal | no lyrics, no anime dialogue, provenance per entry |
| 8 | Slang definitions presented as fact | medium — misinformation | provenance + "common usage" framing + content review |
| 9 | APK growth (fonts, packs, stickers) | medium | 4 MB budget, per-pack caps |
| 10 | No device in this environment | **high** — nothing runtime-verified | device checklist before release; no production-ready claim |

---

## 5. Release plan

Version: current **1.0.5** (versionCode 10). Next release uses a higher code; codes are never reused.

| Release | versionCode | Content |
| --- | --- | --- |
| 1.1.0 | 11 | Milestones 0–2 (engine, presets, studio) |
| 1.2.0 | 12 | Milestones 3–5 (fonts, styles, content packs) |
| 1.3.0 | 13 | Polish, accessibility, remaining packs |

Splitting is deliberate: the theme work is the visible upgrade and carries the artwork-licensing
risk; shipping it alone keeps the release reviewable.

### Pre-release gate
1. Full JVM suite green — record the exact count.
2. Both flavours build; instrumentation compiles; dependency and flavour-separation gates pass.
3. Licence audit: fonts, quotes, slang sources, artwork — all with evidence.
4. Device checklist executed on API 26–29 **and** API 30+ (`DEVICE_TEST_CHECKLIST.md`).
5. Fresh install and upgrade from 1.0.5 with settings, learned words and clipboard intact.
6. Screenshots captured on a device; no mockups presented as screenshots.
7. Release notes state what shipped and what deliberately did not.
8. **Release published only after the owner approves the final candidate.**

### What will not be claimed
- That the keyboard changes other apps' fonts.
- That Credential Manager support exists.
- That any test ran on a device when it did not.
- That a preset's artwork is licensed when its provenance is unrecorded.

---

## 6. Open decisions needed before Milestone 1

1. **Artwork**: commission originals, obtain permission, or ship palette-only? (Blocks the visual
   upgrade; palette-only is unblocked today.)
2. **Release split**: one 1.1.0 with everything, or the three-release plan above?
3. **Stickers**: in scope for 1.1.0 or deferred?
4. **Slang delivery**: bundled only, or downloadable (which adds a network path needing policy)?
5. **Font count**: how many bundled families before APK growth is unacceptable?
