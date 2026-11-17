# Emoji, GIF and Quotes — LLD

**Status:** design only. Nothing implemented.

---

# Part A — Emoji

## A1. What exists
`Emoji.kt` (catalogue, search, `EmojiRecents`), `EmojiSuggest.kt` (inline suggestions in the strip),
`assets/emoji/`. Recents already route through `PrivacyWriteRouter.rememberEmoji`, so incognito is
handled.

## A2. Gaps
| Requested | Status |
| --- | --- |
| Categories | **Already implemented** |
| Search | **Already implemented** |
| Recently used | **Already implemented** (privacy-gated) |
| Favourites | **Missing** |
| Frequently used | **Missing** (distinct from recents: ranked by count, not recency) |
| Skin-tone selection | **Missing** |
| Combinations / sequences | **Missing** |
| Emoji suggestions | **Already implemented** (`EmojiSuggest.kt`) |
| Better panel layout | **Partially** — panel exists; no favourites tab |
| Theme-aware panel | **Partially** — panel uses `KeyPalette`; verify it follows the new strip/toolbar keys |

## A3. Design
```kotlin
data class EmojiFavourite(val emoji: String, val order: Int)
data class EmojiCombo(val id: String, val parts: List<String>, val label: String?)
```
- **Favourites**: app-private JSON, ordered, capped at 200.
- **Frequently used**: derived from recents by count, computed on demand — no second store.
- **Skin tones**: resolve `emoji + tone` to the tone-modified codepoint; the base catalogue stays
  tone-neutral so it is not duplicated five times.
- **Combinations**: inserted as one string, removed by one backspace (the panel owns the undo unit).
- **Artwork**: platform Unicode emoji only. The brief forbids bundling questionable emoji artwork;
  the existing `assets/emoji` is used as-is and must not gain unlicensed sets.

## A4. Privacy
Favourites and combos persist (explicit user data). Recents and frequency counters are suppressed
under `effectiveIncognito`. This distinction goes into `PRIVACY.md`.

## A5. Flavours
Identical in both. Emoji are text, not artwork, so the `fdroid` flavour is unaffected.

## A6. Tests
Unit: favourite ordering, cap, tone resolution correctness, combo insertion exactness, frequency
ranking determinism, recents suppressed when private. Device: panel open time with the full
catalogue, TalkBack labels per emoji.

---

# Part B — GIF and Stickers

## B1. What exists
`GifCatalog` (Klipy + Giphy clients, built-in curated catalogue with local title filtering,
categories, paging), `GifPanel`/`GifSearchBar`, `GifSearchPolicy` (incognito closes the network path
before any key is read), localised notice string.

## B2. Gaps
| Requested | Status |
| --- | --- |
| Search | **Already implemented** |
| Categories | **Already implemented** |
| Trending | **Not verified** — depends on provider API support; must not be claimed until checked |
| Recently used | **Missing** |
| Favourites | **Missing** |
| Stickers | **Missing** |
| Local curated collection | **Already implemented** (built-in catalogue) |
| Loading / error states | **Already implemented** (`GifPage.error`) |
| Efficient loading | **Already implemented** (Coil) |
| Privacy controls | **Already implemented** (`GifSearchPolicy`) + per-provider toggle below |

## B3. Design
```kotlin
data class SavedGif(val id: String, val provider: String, val title: String, val url: String, val savedAt: Long)
data class StickerPack(val id: String, val name: String, val entries: List<String>)
```
- **Favourites**: store id + URL + title, never the bytes. A dead URL degrades to a placeholder.
- **Recently used**: same store, different query, capped at 30.
- **Per-provider toggle**: evaluated inside `GifSearchPolicy.mode`, not scattered at call sites.
- **Stickers**: local packs imported via SAF (PNG/WebP, size-capped, max N entries). No sticker
  *network* source is added — that would add a provider whose terms have not been reviewed.
- **Trending**: implement only if the provider's documented API exposes it. **Do not claim support
  until the integration is verified against the live API.**

## B4. Privacy — preserved, not reimplemented
- Incognito: `GifCatalog.fetch(allowNetwork = false)` returns the built-in catalogue **before any
  key is read or request built**. No substitute request, no alternative route.
- The notice ("Incognito: built-in GIFs only") shows only when privacy is the cause, not when no key
  is configured.
- No query is logged. No tracking parameters. API keys are read only when a network search is
  actually permitted.

## B5. Flavours
Identical. GIF search is network-only and user-keyed. The F-Droid metadata's `NonFreeNet`
anti-feature covers it and must be updated if a new provider is added.

## B6. Tests
Unit: `GifSearchPolicy.mode` matrix with the provider toggle, saved-GIF round-trip, dead-URL
handling, sticker pack validation. Device: incognito search performs **zero** requests (proxy), and
normal-mode search still works.

---

# Part C — Quotes

## C1. What exists
**Nothing.** No quote code, no data, no panel. This is the only fully greenfield feature here.

## C2. Design
```kotlin
data class Quote(
    val id: String,
    val text: String,
    val author: String?,      // null for original/anonymous
    val source: String?,      // provenance
    val category: QuoteCategory,
    val lang: String = "en"
)
```
Categories: motivation, life, friendship, study, creativity, confidence, **original
anime-inspired**, short aesthetic.

## C3. Copyright — the hard constraint
The brief is explicit: *"Do not reproduce copyrighted anime dialogue or song lyrics."*

- **No lyrics.** No song text at all.
- **No anime dialogue** — that is copyrighted script.
- "Anime-inspired" means **written for this project**: original lines in that register, authored or
  commissioned, marked `author = null, source = "original"`.
- Public-domain quotations (pre-1929, or explicit PD) are acceptable with attribution.
- Every entry carries `source`; an entry without provenance fails review.

## C4. Browsing surface
The brief requires quotes stay **separate from the typing surface** so the keyboard is not
cluttered: quotes live in their own panel (structured like `Panels.kt` entries), never in the
suggestion strip. Random quote, copy, insert, favourites, recents, offline.

## C5. Privacy
Local. Favourites persist; in-panel search history does not; recents suppressed in incognito.

## C6. Tests
Unit: pack parsing, provenance present for every entry, search determinism, random selection is
uniform over the filtered set, category filter. Content: a review pass for copyright and accuracy.

## C7. Acceptance criteria
1. Every quote has provenance; "original" is explicit, not implied.
2. No lyrics, no anime dialogue.
3. Quotes never appear in the suggestion strip.
4. Search and browsing are offline and deterministic.
5. Favourites persist; recents suppressed in incognito.
