# GenZ Dictionary — LLD

**Status:** design only. Nothing implemented.

## 1. What exists

- `Dictionaries.kt` + `assets/dict/*.txt` — bundled word lists, bigrams, typo lists, with download
  support for additional languages.
- `WordPredictorEngine` — learning, bigrams, autocorrect, `personalShortcuts`.
- `Plugins.kt` — a plugin-supplied `shortcuts` map (capped at 500).

**There is no GenZ vocabulary, no definitions, no searchable glossary.** This feature is additive;
it must not disturb the existing predictor.

## 2. Scope — two separate things, deliberately

| | Prediction dictionary | GenZ glossary |
| --- | --- | --- |
| Purpose | Make typing faster | Explain and insert slang |
| Content | words + frequencies | term, meaning, example, category |
| Consumer | `WordPredictorEngine` | a panel the user browses |
| Risk | wrong suggestion | wrong *definition* presented as fact |

Conflating them is the main design risk: a glossary entry has prose, and prose in the prediction
path would slow typing and produce absurd suggestions. **Keep them in separate stores.**

## 3. Data model

```kotlin
data class SlangEntry(
    val term: String,
    val meaning: String,
    val example: String?,
    val category: SlangCategory,   // EVERYDAY, INTERNET, SHORTFORM, GAMING, SOCIAL,
                                   // CONVERSATION, REACTION, FRIENDLY, ABBREVIATION
    val pronunciation: String?,    // optional, only where genuinely useful
    val lang: String = "en"
)
```

Storage: `assets/slang/en.json`, loaded like an existing dictionary pack. Favourites and recents in
`filesDir/slang/`.

## 4. Editorial policy — the part that matters

The brief warns: *"Avoid presenting invented or uncertain meanings as established facts."* Slang
ages fast and definitions vary by community. Therefore:

1. **Every entry carries a provenance field** in the source file (`source: "editorial"`, or a
   citation). Unattributed entries are a review failure.
2. **No entry claims to be universal.** The UI says "common usage", not "the definition".
3. **Contested terms are marked**, not silently resolved.
4. **Review is required before merge** — this is content work with a factual-accuracy bar, not code.
5. **No slang that is a slur, or that targets a group.** The panel is user-facing.

This policy is why the feature needs a content reviewer, not only an engineer.

## 5. Search and browsing

Offline only. Search over term + meaning + example, ranked: exact term → prefix → substring →
meaning match. Deterministic ordering (a unit-testable pure function), because a search that
reshuffles on every keystroke feels broken.

Filters by category; recents; favourites; quick insert at the cursor (one undoable action).

## 6. Privacy
Local. **Browsing and search do not record history.** Favourites persist (explicit user data).
Recents are suppressed under `effectiveIncognito` via `PrivacyWriteRouter`, consistent with emoji
recents.

## 7. Interaction with prediction
Slang terms may optionally be added to the prediction dictionary as *words* (no meanings), so typing
"fr" can suggest "fr". That path goes through the existing dictionary, and the precedence question
(slang vs autocorrect vs shortcuts) belongs to the phrase-trigger design in
`GENZ_KEYBOARD_1_1_0_LLD.md` §6 — **it must be resolved there, not reinvented here.**

## 8. Tests
Unit: JSON schema, provenance present for every entry, search ranking determinism, category filter,
duplicate-term detection, mixed-language isolation. Content: a review checklist run over the pack
(accuracy, no slurs, no contested-as-fact).

## 9. Acceptance criteria
1. Every entry has meaning, category and provenance.
2. Search is deterministic and offline.
3. No entry is presented as a universal definition.
4. Recents suppressed in incognito; favourites persist.
5. Typing latency unchanged with the pack loaded.
6. A content review has been recorded before merge.
