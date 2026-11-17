# GenZ Keyboard 1.1.0 — Low-Level Design

**Status:** design only. No feature here is implemented.
**Baseline:** `main` after the privacy milestone (203 JVM tests, 0 failures).
**App id:** `com.genz.keyboard` · **minSdk 26** · **target/compileSdk 35** · Kotlin + Compose.

Every "existing" claim was read from the repository at that commit. Nothing assumes a component
exists because it would be convenient.

---

## 0. Ground rules

1. **Extend, do not replace.** Themes, emoji, GIF search, fonts, dictionaries, shortcuts and inline
   autofill already exist. Each feature starts from what is there.
2. **The privacy layer is load-bearing.** `PrivacyState`, `IncognitoTransition`, `PrivacyEffects`,
   `PrivacyWriteRouter`, `PluginEventPolicy`, `InlineAutofillPolicy`, `GifSearchPolicy`, `UndoGuard`
   define what may happen in a private session. New features route through them, not around them.
3. **No new permissions** unless a section justifies one explicitly.
4. **Both flavours keep working.** `standard` ships ten anime illustrations; `fdroid` ships none.

---

## 1. Existing components inventory

| Area | Files | What already works |
| --- | --- | --- |
| Themes | `Skins.kt`, `ThemeUi.kt`, `ThemeOptions.kt`, `KeyPalette.kt`, `Design.kt`, `Theme.kt` | Skin JSON parser, gallery + preview, palette derived from artwork, brand/seed separation, 10 bundled skins in `assets/plugins/anime_*.json` |
| Emoji | `Emoji.kt`, `EmojiSuggest.kt`, `assets/emoji/` | Catalogue, search, `EmojiRecents`, strip suggestions |
| GIF | `KeyboardData.kt` (`GifCatalog`), `Panels.kt`, `GifSearchPolicy.kt` | Klipy/Giphy clients, built-in catalogue with local filtering, categories, paging, incognito network block |
| Fonts | `FontRepository.kt`, `res/font/pixelify.ttf` | Bundled Pixelify Sans (OFL), user-installed `CUSTOM` font, version-keyed cache |
| Dictionary | `Dictionaries.kt`, `assets/dict/*.txt`, `WordPredictor.kt` | Word lists, download support, learning, bigrams, autocorrect |
| Shortcuts | `Plugins.kt` (`shortcuts`), `WordPredictor.kt` (`personalShortcuts`) | Plugin expansion map (cap 500), user shortcuts |
| Quotes | — | **Nothing exists.** "quote" matches only incidental text in `AiFix.kt`, `I18n.kt`, `Plugins.kt` |
| Autofill | `InlineAutofillPolicy.kt`, `KeyboardService.onCreateInlineSuggestionsRequest` | Themed inline chips, privacy gate, generation guard, API 30+ |
| Credentials | — | **No Credential Manager integration.** `androidx.credentials` is not a dependency |

Deps: Compose BOM 2025.06.00, material3 1.5.0-alpha13, coil 2.6.0, `androidx.autofill:autofill:1.1.0`,
`org.mozilla:rhino:1.7.15`, `com.anthropic:anthropic-java:2.34.0`. No JSON library (`org.json` only).

---

## 2. Feature 1 — Advanced themes and customisation

**Existing:** `Skins.kt` parses skin JSON, `ThemeUi.kt` renders the gallery, `ThemeOptions.kt` derives
colours, `PluginRepository.resolvedSkin(raw, dark)` composes a skin over settings.

**New:** `theme/ThemeImport.kt` (validate imported JSON: schema version, colour parse, size cap, no
network URLs), `theme/ThemeExport.kt`, `theme/ThemeEditor.kt` (live colour/shape/font editing).

```kotlin
data class UserTheme(
    val id: String,                 // content hash, stable
    val name: String,
    val base: String,               // "BRAND" | "SEED" | palette id
    val colors: Map<String, Long>,  // ARGB
    val shape: String,              // GenZShapes key
    val fontId: String?
)
```
Storage: `filesDir/themes/<id>.json`. Not SharedPreferences — themes are bulky and user-authored.

**API:** none beyond 26. Import/export via SAF (`OpenDocument`/`CreateDocument`), so no storage
permission.

**Flavours:** `standard` may preview artwork; `fdroid` renders palette-only. A theme referencing
missing artwork must fall back to its palette — `Skins.kt` already does this and the editor must not
regress it.

**Privacy:** no user text. Import must reject remote URLs in `background`/`asset` fields so a shared
theme cannot become a fetch vector.

**Tests:** unit — schema validation, colour parsing, hash stability, unknown-field tolerance;
device — gallery scroll with 50+ themes, import from Downloads.

**Perf/a11y:** theme switch within one extra frame; thumbnails at preview size; every palette must
meet 4.5:1 contrast against key text, asserted in a unit test over the bundled palettes.

---

## 3. Feature 2 — Emoji picker, favourites, combinations

**Existing:** `Emoji.kt`, `EmojiSuggest.kt`, `assets/emoji/`; recents already gated through
`PrivacyWriteRouter.rememberEmoji`.

**New:** `emoji/EmojiFavourites.kt`, `emoji/EmojiCombo.kt` (multi-emoji inserted as one unit),
`emoji/EmojiSkinTone.kt` (variant resolution without duplicating the catalogue).

```kotlin
data class EmojiFavourite(val emoji: String, val addedAt: Long, val order: Int)
data class EmojiCombo(val id: String, val parts: List<String>, val label: String?)
```
Storage: app-private JSON; recents unchanged.

**UI:** picker already exists in `Panels.kt`. Favourites become a first tab, combos a row above the
grid. The strip is unchanged — its three-slot contract is guarded by `SuggestionChipModelTest`.

**Privacy:** favourites and combos are explicit user data and persist in incognito; recents remain
suppressed. This distinction goes into `PRIVACY.md`.

**Tests:** ordering stability, cap enforcement, tone resolution, combo insertion exactness; device —
picker opens under 100 ms with 2,000 entries.

---

## 4. Feature 3 — GIF catalogue, stickers, search, privacy controls

**Existing:** `GifCatalog` (Klipy + Giphy, built-in catalogue, `fallback(category, query)` local
filtering), `GifPanel`/`GifSearchBar`, `GifSearchPolicy`, localised notice string.

**New:** `gif/GifFavourites.kt` (id + URL + title, never bytes), `gif/StickerPack.kt` (SAF import,
PNG/WebP, size-capped), `gif/GifAttribution.kt` (required attribution + per-provider terms).

```kotlin
data class SavedGif(val id: String, val provider: String, val title: String, val url: String, val savedAt: Long)
data class StickerPack(val id: String, val name: String, val entries: List<String>)
```

**Privacy controls:** extend the existing policy rather than scattering checks — a per-provider
toggle is evaluated inside `GifSearchPolicy.mode`. Incognito already closes the network path;
favourites and local packs stay usable because they are chosen assets, not a record of typing.
No query is logged.

**Deps:** none new — clients are hand-rolled `HttpURLConnection`, Coil decodes GIFs. Attribution
terms must be checked per provider before shipping.

**Flavours:** identical. The `NonFreeNet` anti-feature in the F-Droid metadata already covers GIF
search and must be updated if a sticker source is added.

**Tests:** `GifSearchPolicy.mode` matrix including the provider toggle, local filtering, saved-GIF
round-trip; device — incognito search performs no request (proxy-verified).

---

## 5. Feature 4 — Fonts and decorative text

**Existing:** `FontRepository.kt` resolves `CUSTOM` or bundled Pixelify Sans; `SettingsAppearance`
exposes the choice.

**New:** `font/FontCatalog.kt` (bundled list with per-font licence metadata), `font/DecorativeText.kt`
(Unicode-style transforms: bold, italic, small caps, circled).

```kotlin
data class FontEntry(val id: String, val label: String, val asset: String, val licence: String, val sizeBytes: Int)
```

**Licensing (hard):** every bundled font OFL/Apache/public-domain, licence text in `licenses/`,
`NOTICE.md` and the in-app list updated **in the same commit**. The Pixelify Sans mislabelling earlier
in this project is the cautionary example.

**Privacy:** transforms are local string maps, never applied in password fields, and must respect
`effectiveIncognito` — a transform changes what is committed. Decision: opt-in per insert, never
automatic.

**Perf:** font switching uses the existing version-keyed cache; bundled fonts capped at 2 MB total.

**Tests:** transform idempotence, unknown-character passthrough, licence metadata present for every
entry; device — rendering at 12–24 sp.

---

## 6. Feature 5 — Slang dictionary, abbreviations, quick phrases

**Existing:** `Dictionaries.kt` + `assets/dict/*.txt`, `WordPredictorEngine` (learning, bigrams,
`personalShortcuts`), `Plugins.kt` (`shortcuts`).

**New:** `dict/SlangPack.kt` (versioned pack loaded like a dictionary), `dict/QuickPhrase.kt`,
`dict/PhraseTrigger.kt` (resolve at a word boundary).

```kotlin
data class SlangEntry(val trigger: String, val expansion: String, val lang: String, val tags: Set<String>)
data class QuickPhrase(val id: String, val trigger: String, val text: String, val category: String?)
```

**The real design problem — precedence.** Five mechanisms can fire on one word boundary. Wrong
precedence silently corrupts text, so it is a pure, unit-tested function rather than a chain of `if`s
in the service:

```
personal phrase → personal shortcut → plugin shortcut → slang pack → autocorrect
```

**Privacy:** all local. Learning and usage stats suppressed in incognito; user-authored phrases
persist (explicit data).

**Flavours:** identical; slang packs ship in `main/assets`, not the `standard` source set.

**Tests:** precedence matrix over every pair, punctuation, case, language scoping; device — typing
latency unchanged with a 5,000-entry pack.

---

## 7. Feature 6 — Quotes, favourites, quick insertion

**Existing: nothing.** This is the only genuinely new feature in the bundle.

**New:** `quotes/QuotePack.kt`, `quotes/QuoteFavourites.kt`, `quotes/QuotePanel.kt` (structured like
existing panels).

```kotlin
data class Quote(val id: String, val text: String, val author: String?, val source: String?, val lang: String)
```

**Copyright (hard):** public-domain or explicitly licensed text only, with attribution. Do not bundle
lyrics or text from living authors without a licence. Provenance is part of each entry and is covered
by the release licence audit.

**Privacy:** local only; favourites persist, in-panel search history does not.

**Tests:** pack parsing, attribution present for every entry, deterministic search ranking.

---

## 8. Feature 7 — Autofill and Credential Manager

### 8.1 What exists
`onCreateInlineSuggestionsRequest` + `onInlineSuggestionsResponse` implement **inline autofill**
(API 30+): the keyboard requests suggestions, receives themed chip views, renders them in the strip.
`InlineAutofillPolicy` gates it on `PrivacyState` plus a generation counter.

### 8.2 What Credential Manager is (from the official docs, fetched for this design)
- *"the recommended Jetpack API for credential exchange in Android apps"* — passkeys, federated
  sign-in, passwords, digital credentials.
- A **credential provider** is *"a component that stores and provides user credentials, such as
  Google Password Manager"*, and providers *"provide a centralized and secure location for users to
  manage credentials across their devices."*
- Credential Manager *"integrates with autofill to display credentials within the autofill UI."*

### 8.3 Ownership — the decision

| Concern | Owner |
| --- | --- |
| Credential storage | **The credential provider.** Never this app. |
| Authentication / user verification | **The credential provider**, with system UI |
| Which credential applies to a field | **The host app** (relying party) |
| Displaying a suggestion in the keyboard | **This keyboard**, via inline autofill |

**GenZ Keyboard must not integrate with Credential Manager as a client or a provider.**
`CredentialManager.getCredential()` is for the app that owns the login form; an IME has no
relying-party context, no server and no business holding credentials. Implementing
`CredentialProviderService` would make this keyboard a password manager — a far larger security
burden than a keyboard should carry, and precisely what the brief forbids.

Correct behaviour, already implemented: the keyboard **renders** what the system provides and
**stores nothing**.

### 8.4 What 1.1.0 adds — hardening, not integration
- "No credentials stored" assertion in `PRIVACY.md`, verifiable by inspection.
- Wider API 26–29 fallback testing of the clipboard-offer path.
- Optional: surface the autofill provider name in the chip subtitle, using data already in the response.

### 8.5 Never to be built
A vault/PIN/encrypted store for passwords; logging, transmitting or caching credential values;
reading a password field's contents; a `CredentialProviderService`.

### 8.6 Verification
Unit: `InlineAutofillPolicy` matrix (26 tests today). Device: with Google Password Manager configured
a login field shows a themed chip; with none, nothing appears and no space is reserved; in incognito
or a password field, no request is made.

**No claim of Credential Manager support will be made.** The accurate statement: the keyboard supports
system inline autofill suggestions, which is how Credential Manager credentials reach a keyboard.

---

## 9. Cross-cutting

### 9.1 Storage summary
| Data | Location | In incognito |
| --- | --- | --- |
| Theme/font selection | prefs + `filesDir/themes` | user data, persists |
| Emoji favourites, combos | `filesDir/emoji` | persists (explicit) |
| Emoji recents | existing store | **suppressed** |
| GIF favourites, sticker packs | `filesDir/gif` | persists (explicit) |
| Slang packs, quick phrases | `assets` / `filesDir/dict` | phrases persist, usage suppressed |
| Quotes, favourites | `assets` / `filesDir/quotes` | persists |
| Learned words | existing store | **suppressed** |
| Credentials | **nowhere** | n/a |

### 9.2 Mandatory privacy integration
`PrivacyWriteRouter` for any persistent write; `PluginEventPolicy` for any new mod event;
`GifSearchPolicy` for any network path; `InlineAutofillPolicy` for anything autofill-shaped;
`PrivacyState` as the only privacy predicate.

### 9.3 Accessibility
New panels expose content descriptions, work with TalkBack, respect key height and one-handed mode.
The GIF privacy notice is currently a plain text line; making it a live-region announcement is in
scope for 1.1.0.

### 9.4 Performance budgets
Panel open ≤ 100 ms; theme switch ≤ 1 extra frame; typing latency unchanged (Macrobenchmark);
APK growth ≤ 4 MB for all bundled content.

---

## 10. Open questions for review

1. Themes shareable as files, or local only? (Affects import validation surface.)
2. Sticker packs in 1.1.0, or GIF favourites alone?
3. Slang packs bundled, downloadable, or both? Downloadable adds a network path needing policy.
4. Quote pack: which sources are acceptable?
5. How many bundled fonts before APK growth is unacceptable?
