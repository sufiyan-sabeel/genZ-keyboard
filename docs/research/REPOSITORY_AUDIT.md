# Repository Audit — GenZ Keyboard (v1.1 Phase 1)

- **Repository:** https://github.com/sufiyan-sabeel/genZ-keyboard
- **Commit inspected:** `78630d0e1bd247c1e58e87eaaa6b37d1c0c32ccc` (branch `main`)
- **Method:** fresh `git clone`, then direct reading of source. Every path and line number below
  was read from that clone; nothing is inferred from a previous report.
- **Release at time of audit:** v1.0.5 (versionCode 10, versionName 1.0.5)

> Scope note: this document describes what exists. It proposes nothing.

## 1. Structure

| Fact | Value |
| --- | --- |
| Kotlin files | 71 |
| Kotlin lines | 34,120 |
| Package | `com.genz.keyboard` |
| Source sets | `app/src/main`, `app/src/standard`, `app/src/test` |
| Min / target / compile SDK | 26 / 35 / 35 |

`app/src/standard` contains **only assets** (`assets/anime/*.webp`, 10 files). It carries no
Kotlin. The `fdroid` flavour has no source directory at all — it exists purely to exclude that
artwork, so both flavours compile the identical `main` source tree.

Largest files, by role:

| File | Lines | Role |
| --- | --- | --- |
| `KeyboardService.kt` | 3,470 | the IME service; nearly all input logic lives here |
| `ScriptEngine.kt` | 1,730 | Rhino JS host for plugins/mods |
| `WordPredictor.kt` | 1,673 | prediction, autocorrect, learning |
| `I18n.kt` | 1,437 | Russian-keyed string table → English |
| `Panels.kt` | 1,316 | emoji / clipboard / plugin panels |
| `Skins.kt` | 1,236 | theme skin parser and renderer |
| `ThemeUi.kt` | 1,130 | theme gallery, preview dialog |
| `Plugins.kt` | 1,119 | plugin model, import, validation |
| `KeyView.kt` | 1,033 | key rendering |
| `KeyboardData.kt` | 940 | layouts, key geometry |
| `Dictionaries.kt` | 886 | bundled + downloaded dictionaries |
| `KeyboardSettings.kt` | 822 | settings model and persistence |
| `KeyboardHeader.kt` | 807 | toolbar and **suggestion strip** |
| `KeyboardUi.kt` | 647 | keyboard composition root, background |
| `AiFix.kt` | 577 | optional AI text fixing (network) |
| `KeyboardState.kt` | 562 | observable UI state (`KeyboardState`) |
| `Clipboard.kt` | 301 | clipboard store, screenshot capture |
| `SecretBox.kt` | — | `PrivateData` wipe helpers |

## 2. AndroidManifest

One manifest only: `app/src/main/AndroidManifest.xml`. No flavour-specific manifest.

Permissions declared (6):

```
android.permission.VIBRATE
android.permission.INTERNET
android.permission.RECORD_AUDIO
android.permission.READ_MEDIA_IMAGES
android.permission.READ_MEDIA_VISUAL_USER_SELECTED
android.permission.READ_EXTERNAL_STORAGE        (maxSdkVersion 32)
```

The IME service is declared with `android:permission="android.permission.BIND_INPUT_METHOD"`
and `android:exported="true"` — the standard, required declaration. A `SpellCheckerService` is
also declared (`android.service.textservice.SpellCheckerService`).

**Relevant to v1.1:** no new permission is needed by any of the five proposed features.
`READ_MEDIA_*` exists solely for the optional "screenshots into clipboard" feature.

## 3. InputMethodService implementation

`KeyboardService.kt:160` — `class ComposeKeyboardService : InputMethodService(), ...`

Lifecycle overrides actually present:

| Method | Line | Behaviour |
| --- | --- | --- |
| `onComputeInsets` | 514 | sets `contentTopInsets`/`visibleTopInsets` from the measured keyboard rect |
| `onCreateInputView` | 525 | builds a `ComposeView`, disposes the previous composition, sets view-tree owners |
| `onStartInput` | 577 | flushes a pending voice result |
| `onStartInputView` | 586 | `RESUMED` lifecycle, permission refresh, personal-dictionary load, clipboard wipe, screenshot scan |
| `onCreateInlineSuggestionsRequest` | 617 | **inline autofill, already implemented** (see §8) |
| `onFinishInputView` | 667 | teardown |
| `onUpdateSelection` | 965 | cursor/selection tracking |

`onCreateInputView` explicitly disposes the old composition before building a new one — the
comment records that stale key views otherwise mix with new ones after rotation or theme change.
Any new UI must respect this: it is re-created per input view.

## 4. EditorInfo processing and field detection

`computeFlags(info: EditorInfo?)` — `KeyboardService.kt:847`. This is the single place field
semantics are derived, and it is the natural extension point for auto-incognito.

```kotlin
val cls       = type and InputType.TYPE_MASK_CLASS
val variation = type and InputType.TYPE_MASK_VARIATION
val password  = text && variation in {PASSWORD, VISIBLE_PASSWORD, WEB_PASSWORD}
             || (cls == NUMBER && variation == NUMBER_VARIATION_PASSWORD)
val addressLike = text && variation in {EMAIL_ADDRESS, WEB_EMAIL_ADDRESS, URI}
val noSuggest = (type and TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
```

It returns `FieldFlags(isPassword, suggestions, autoCorrect, autoCap)`. Password detection
already covers the three text variations **and** the numeric password variation.

`fieldKey(info)` — `KeyboardService.kt:875` — identity of the current field as
`"<packageName>/<fieldId>"`, used to distinguish a window restart from a field change.

`resetForInput(info, restarting)` — `KeyboardService.kt:878` — recomputes flags and clears
per-field state. Line 881: `ScriptHost.privateMode = flags.isPassword || settings.incognito`.

**Limitation:** `IME_FLAG_NO_PERSONALIZED_LEARNING` is **not read anywhere** in the codebase
(grep for `IME_FLAG` returns only `IME_FLAG_NO_ENTER_ACTION` at lines 900/1866). Feature #13
needs this.

## 5. Commit pipeline

There is no separate `InputConnection` wrapper class. All commits go through
`currentInputConnection` inside `KeyboardService`:

- composing text: `ic.setComposingText(word, 1)` — line 1081
- finishing: `ic.finishComposingText()` — lines 1017, 1668, 1855, 1927, 1977, 2081
- reads before acting: `currentInputConnection?.getTextBeforeCursor(64, 0)` — line 938

Consequence for **Typo-Undo (#3)** and **Math (#1)**: any replacement must be issued as a
composing-text edit plus `finishComposingText`, and must be preceded by a `getTextBeforeCursor`
check, because the host editor may have changed underneath. There is no abstraction layer to
hook; the design must add one rather than scattering more calls.

## 6. Existing autocorrect and undo state

**Typo-Undo partially exists already.**

- `UndoAutoCorrectState(originalWord: String, committedText: String)` — `WordPredictor.kt:157`
- field `private var undo: UndoAutoCorrectState?` — `KeyboardService.kt:235`, set at 1023/1197
- `SuggestionChipModel.isUndo: Boolean` — `WordPredictor.kt:165`, so the strip already has a
  dedicated undo chip concept
- `lastCommittedWord` — `KeyboardService.kt:233`, feeds `autoCorrectionFor(...)` at 1112

`WordPredictorEngine.autoCorrectionFor(...)` — `WordPredictor.kt:1353`.

So #3 is an **extension of an existing mechanism**, not a new one. What is missing is expiry
timing, cancellation on cursor move, and selection-change validation.

## 7. Clipboard

- `ClipboardManager.OnPrimaryClipChangedListener` — `KeyboardService.kt:275`, registered at 467
- `syncClipboard()` — `KeyboardService.kt:2845`
- `ClipStore` — `Clipboard.kt:103` (history, persistence)
- `ClipImages` — `Clipboard.kt:35`
- `kb.clipOffer: String?` — `KeyboardState.kt:194`, a **single** clipboard offer chip with a
  TTL (`CLIP_OFFER_MS`), cleared on key press at 1992

**Limitation for #4:** `clipOffer` is a `String?` — no type, no classification, no confidence.
`ClipDescription.EXTRA_IS_SENSITIVE` is not read (grep: no occurrence).

## 8. Inline autofill — already implemented

`KeyboardService.kt:617`, `@RequiresApi(Build.VERSION_CODES.R)`:

```kotlin
override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest?
```

It builds a real `androidx.autofill.inline.v1.InlineSuggestionUi` style with
`setSingleIconChipStyle`, `setChipStyle`, `setTitleStyle`, `setSubtitleStyle`, and an
`InlinePresentationSpec.Builder` bounded to `64×34dp` … `200×38dp` so the chip cannot exceed the
gap between the toolbar buttons and the microphone.

Dependency: `androidx.autofill:autofill:1.1.0` — `app/build.gradle.kts:157`.

**This is the most important finding of the audit:** feature #23 is not a greenfield feature.
Phase 1 should be scoped to *hardening and fallback*, not construction.

## 9. Existing incognito / privacy

Incognito today is a **manual toggle**, not automatic:

- preference `incognito` (boolean), toggled from the toolbar —
  `KeyboardHeader.kt:648-650`
- `PrivateData` — `SecretBox.kt:97` — `wipeClipboard` flag, `wipe(context)` clearing learned
  words, CJK user data, emoji recents, pinned clips, clip files and secret prefs

The **suppression** machinery already exists and is already gated on `settings.incognito`:

| Suppressed | Location |
| --- | --- |
| word learning | `KeyboardService.kt:1315` (`!settings.incognito`) |
| plugin `word` event | `KeyboardService.kt:1316` |
| clipboard history save | `KeyboardService.kt:2539` |
| clipboard restore at boot | `KeyboardService.kt:447` |
| screenshot capture | `KeyboardService.kt:497` |
| system spellcheck | `KeyboardService.kt:1132` |
| AI fix | `KeyboardService.kt:2848` |
| script private mode | `KeyboardService.kt:881` |

What does **not** exist: automatic activation, a reason/indicator, a package allowlist, or any
handling of `IME_FLAG_NO_PERSONALIZED_LEARNING`.

## 10. Settings and persistence

- `KeyboardSettings` data class — `KeyboardSettings.kt`, ~90 fields, `@Immutable`
- `KeyboardPrefs` object — `KeyboardSettings.kt:434` — SharedPreferences-backed, exposes
  `state`, `edit(context) {}`, `observe()`, and `forPower(...)`
- secrets are stored separately (`SecretBox`, `SECRET_PREFS`)
- `android:allowBackup="false"` (see manifest), so prefs do not enter cloud backup

Adding a setting is a well-worn path: field on the data class, read in `load`, write via
`KeyboardPrefs.edit`.

## 11. Dictionaries, suggestion and prediction

- `WordPredictorEngine` — `WordPredictor.kt:189`; `learn(lang, previous, word, boost)` at 748,
  `clearUserData()` at 772, `autoCorrectionFor(...)` at 1353
- `Dictionaries` — `Dictionaries.kt`; bundled assets in `app/src/main/assets/dict/`
  (`en.txt`, `en_bi.txt`, `en_more.txt`, `en_typos.txt`, `ru*.txt`, plus `zh/`)
- `CjkUser.learn(...)` — `Cjk.kt:57`
- suggestion strip rendering — `KeyboardHeader.kt:251` onward, `SuggestionChipModel` list

The strip is a fixed 3-slot model (`SuggestionChipModel.EMPTY_ROW`), with slots replaced by
shortcut/undo/emoji chips under explicit conditions. **Math (#1) and Entity chips (#4) must fit
that 3-slot contract** or the strip needs a documented widening.

## 12. Dependencies

`app/build.gradle.kts`. Compose BOM 2025.06.00, `material3:1.5.0-alpha13`, core-ktx 1.13.1,
activity-compose 1.9.2, lifecycle 2.8.6, savedstate 1.2.1, `androidx.autofill:autofill:1.1.0`,
`coil-compose`/`coil-gif` 2.6.0, `org.mozilla:rhino:1.7.15`,
`androidx.profileinstaller:1.3.1`, `com.anthropic:anthropic-java:2.34.0`, JUnit 4.13.2 (test).

`com.google.mlkit:genai-proofreading` was removed for F-Droid and is absent.

**No JSON parser dependency is present** (only `org.json` from the platform), relevant if the
math parser or entity classifier wants structured data.

## 13. Tests

`app/src/test/java/com/genz/keyboard/` — 15 files, JVM-only (no instrumentation, no androidTest
source set):

`AnimeThemesTest`, `AutocorrectEval`, `ContractionsTest`, `CustomAiTest`, `EmojiSuggestTest`,
`GlideEval`, `KeySoundsTest`, `LearnedTypoTest`, `ModEffectsTest`, `MyanmarInputTest`,
`PhraseEval`, `PredictionTest`, `TestAssets`, `ThemePaletteTest`, `UserLayoutsTest`.

They read real assets from `src/main/assets` and `src/standard/assets` via relative paths
(`TestAssets.kt`), which is the established pattern for asset-dependent tests.

**No `androidTest` source set exists**, so nothing in the repo currently exercises real
`InputConnection` behaviour. Phase 1's test plan must not assume instrumentation exists.

## 14. Flavours

```kotlin
flavorDimensions += "distribution"
productFlavors {
    create("standard") { dimension = "distribution" }
    create("fdroid")   { dimension = "distribution" }
}
```

Both inherit the same manifest, permissions and dependencies. The only difference is
`app/src/standard/assets/anime/`. CI enforces the split (`standard` 10 artwork files, `fdroid` 0)
and fails on any proprietary dependency group on the fdroid runtime classpath.

## 15. Network and AI

`AiFix.kt` — optional text fixing via Gemini / Claude / OpenAI / Groq / OpenRouter /
LanguageTool, all user-keyed, all off by default. `ScriptEngine.kt` and `Plugins.kt` can issue
HTTPS requests for plugins. GIF search uses user-supplied Klipy/Giphy keys.

None of this is on the typing path: `KeyboardService.kt:2848` returns early when incognito or a
password field is active.

## 16. Missing information / limitations

1. **The 26-feature research report is not in the repository** and was not supplied with this
   brief. Stage 2 (rescoring) cannot use "the original criterion values" — see
   `FEATURE_SCORING_CORRECTED.md`.
2. No instrumentation tests and no device in the build environment, so no claim in these
   documents rests on observed runtime behaviour.
3. `onCreateInlineSuggestionsRequest` could not be confirmed from the Android reference pages
   (both URL variants returned 404 at fetch time). It is confirmed from the compiling source
   with `@RequiresApi(Build.VERSION_CODES.R)`. See `ANDROID_API_VERIFICATION.md` §5.
4. Behaviour of specific host apps (Chrome, WhatsApp, Termux) is **not** verified — none were run.
