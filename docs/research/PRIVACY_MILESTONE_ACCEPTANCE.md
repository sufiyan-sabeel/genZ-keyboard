# Privacy Milestone — Final Acceptance Audit

**Audit date:** 2026-11-17
**Repository state audited:** `3e5f4fc5ff6adf6ee0e306e242412b3e8e6585e4` (branch `main`)
**Working tree at audit start:** clean (0 modified files)
**Fix commit:** `d5f6c708e72dfe60d8a6f3af07fce765a3c8193f`
**Working tree after the fix:** clean

## Verdict summary

| Area | Status |
| --- | --- |
| §1 Incognito entry points | **PASS WITH LIMITATIONS** — the ordering defect was confirmed and is fixed in `d5f6c708`; device verification still outstanding |
| §2 Privacy boundary (six paths) | **PASS WITH LIMITATIONS** |
| §3 Transition effects applied | **PASS WITH LIMITATIONS** |
| §4 UI recomposition | **PASS WITH LIMITATIONS** (source-level only) |
| §5 Build/test verification | **PASS** for the audited commit |
| §6 Physical-device verification | **NOT VERIFIED** — never performed |

---

## 1. Incognito entry points — FAIL (defect confirmed)

### Entry points found

Exactly **two** code paths write the `incognito` preference. Both were located by searching every
`putBoolean("incognito", …)` in the repository:

| Path | Location | Routed through the transition? |
| --- | --- | --- |
| Keyboard toolbar | `KeyboardHeader.kt` → `actions.setIncognito(next)` → `KeyboardService.setIncognito` | yes |
| Settings screen | `SettingsTyping.kt` → `KeyboardPrefs.edit` | yes, via the preference listener registered in `onCreate` |

No other UI, service, onboarding, reset or import path writes this key.

### The defect

`KeyboardPrefs.edit` uses `Editor.apply()`, which notifies listeners in **unspecified order**
(`SharedPreferencesImpl` iterates a `WeakHashMap`). The service's listener called
`refreshPrivacyFromPrefs()`, which read the new value back through `KeyboardPrefs.current()`.

`current()` only refreshes when its internal `dirty` flag is already set:

```kotlin
if (dirty && Looper.myLooper() == Looper.getMainLooper()) refresh(prefs(context))
return state.value
```

So if the service's listener ran **before** `KeyboardPrefs`' own listener, `dirty` was still false
and `current()` returned the **old** value. The early return in `refreshPrivacyFromPrefs` then
compared the old value with the cached one, found no change, and applied **nothing**.

Consequence: `ScriptHost.privateMode` and the inline generation could remain stale from the moment
incognito was switched on until something next read `privacy` — in practice the next keystroke.
That is precisely the interval this milestone exists to close. The same race applied to the toolbar
path, because `setIncognito` also read the value back immediately after writing it.

### Fix applied — `d5f6c708e72dfe60d8a6f3af07fce765a3c8193f`

The transition now takes the value as a parameter and never re-reads it:

- `setIncognito(on)` → `applyIncognito(on)` — the value it just wrote.
- `incognitoWatcher` → `applyIncognito(p.getBoolean("incognito", false))` — the listener is handed
  the preferences that changed, so the value is authoritative regardless of listener order.

The decision was also extracted into a pure `IncognitoTransition.apply(manual, spec, previous)` so
the invariant is testable without an `InputMethodService`. `IncognitoTransitionTest` (8 cases) pins
it: the supplied value wins even when a cache still holds the old one, and the stale-to-stale
comparison that made the bug silent is asserted explicitly.

**Verified by CI:** 203 tests, 0 failures; both flavours build; instrumentation compiles;
dependency and flavour-separation gates pass.

**What the test does not cover:** the Android listener ordering itself. The test pins the *shape*
of the fix — a value is supplied rather than read back — not the platform's notification order,
which cannot be exercised without a device.

---

## 2. Privacy boundary — PASS WITH LIMITATIONS

Each path traced through the actual execution code.

| Path | Gate | Evidence |
| --- | --- | --- |
| Plugin content events | `ScriptHost.emit` refuses content events when `privateMode`; call sites also check `PluginEventPolicy.allows` | `ScriptEngine.kt:530`, ten call sites in `KeyboardService.kt` |
| ScriptHost text accessors | every accessor re-checks `privateMode` at call time | `textBefore`/`textAfter` 994–995, `allText` 1330, app info 1122, selection 1328, pointer 1116, mic 1111, `dict.learn` 1515 |
| Inline autofill request | returns `null` before any style or spec is built | `onCreateInlineSuggestionsRequest`, `InlineAutofillPolicy.isAllowed` |
| Inline autofill response | re-checks privacy **and** generation at publish | `InlineAutofillPolicy.mayPublish` |
| Emoji recents | `PrivacyWriteRouter.rememberEmoji` | real write only inside the sink |
| Dictionary writes | `PrivacyWriteRouter.rejectCorrection`; `learn()` gated internally | same |
| Typo-Undo persistence | both `rejectCorrection` and `learn` routed as above | apply path in `pickSuggestion` |
| GIF network | `GifCatalog.fetch(allowNetwork=false)` returns before any key is read | single caller in `Panels.kt` |

**The interval after enabling and before the next keystroke** is the one the §1 defect affected. In
the audited state it was **not** guaranteed; with the prepared fix it is, because the transition no
longer depends on a cache being refreshed first.

Residual exposure, unchanged and unavoidable: text already handed to a script before the toggle
remains in that script's memory. No host API can retract it.

---

## 3. Transition effects — PASS WITH LIMITATIONS

`applyPrivacyEffects` in `KeyboardService.kt` maps each effect to production code:

| Effect | Production code | Assessment |
| --- | --- | --- |
| `SetScriptPrivate(value)` | `ScriptHost.privateMode = effect.value` | field is `@Volatile` (`ScriptEngine.kt:242`) and read by 15 sites; visible to script threads immediately |
| `DropInlineSuggestions` | `kb.inlineSuggestions = emptyList()` | `mutableStateOf` on `KeyboardState`; consumed by `HeaderBar`'s `inline` gate |
| `InvalidatePendingInline` | `inlineGeneration++` | `publish` compares the captured generation against it via `mayPublish` |

**What the tests actually verify — stated precisely.** `PrivacyEffectsTest` executes
`PrivacyTransitions.effectsOf`, i.e. the *decision* about which effects a transition produces, and
`PrivacyWriteRouter` against a recording fake. **No test executes `applyPrivacyEffects` itself**,
because it needs a bound `InputMethodService`. The claim "the service applies the list correctly" is
**source-verified only**, not unit-tested. Earlier wording that implied otherwise is corrected here.

---

## 4. UI recomposition — PASS WITH LIMITATIONS (source-level only)

The observation chain, read from source:

```
KeyboardPrefs.state (mutableStateOf)
  → KeyboardPrefs.observe()            // Compose State
    → KeyboardUi.kt:161  val prefs by KeyboardPrefs.observe()
      → KeyboardUi.kt:162  remember(prefs, …) { … }      // new value on change
        → KeyboardUi.kt:178  settings (remember keyed on raw)
          → HeaderBar(settings = settings)
            → mayShow(settings.incognito, state.privateField)
```

Because `settings.incognito` is read during composition from an observed state, a toggle produces a
new `settings` object and recomposition is guaranteed **at the source level**. The strip is hidden
by `mayShow`, and the underlying list is cleared by `DropInlineSuggestions`, so a stale
`remember` cannot keep a credential visible: the gate re-evaluates from the same state.

**Runtime evidence: none.** No Compose UI test exists, and no device ran. This is source-level
reasoning about Compose semantics, not an observation.

---

## 5. Verification executed

For the audited commit `3e5f4fc5`, from CI:

| Check | Result |
| --- | --- |
| JVM unit tests | **203 tests, 0 failures, 0 skipped** |
| Standard flavour build | pass |
| F-Droid flavour build | pass |
| Instrumentation test compilation | pass |
| Dependency policy gate | pass (no proprietary group on the fdroid classpath) |
| Flavour separation | pass (`standard` 10 artwork files / `fdroid` 0) |

Suite breakdown: `AnimeThemesTest` 8, `AutocorrectEval` 2, `ContractionsTest` 3, `CustomAiTest` 3,
`EditorFieldSpecTest` 17, `EmojiSuggestTest` 5, `GifSearchPolicyTest` 9, `GlideEval` 2,
`InlineAutofillPolicyTest` 26, `KeySoundsTest` 1, `LearnedTypoTest` 1, `ModEffectsTest` 10,
`IncognitoTransitionTest` 8, `MyanmarInputTest` 11, `PhraseEval` 2, `PluginEventPolicyTest` 14, `PredictionTest` 4,
`PrivacyEffectsTest` 15, `PrivacyStateTest` 23, `SuggestionChipModelTest` 9, `ThemePaletteTest` 5,
`UndoGuardTest` 20, `UserLayoutsTest` 5.

These results are from the fix commit `d5f6c708`, which supersedes the audited state
(195 tests there; the fix adds `IncognitoTransitionTest` with 8 cases).

Lint is not wired into CI (`checkReleaseBuilds = false`, a pre-existing material3 decision), so the
only static analysis enforced is the Kotlin compiler.

---

## 6. Physical-device scenarios not executed

None of the following has been run. The checklist in `DEVICE_TEST_CHECKLIST.md` covers them in
detail; summarised:

1. Incognito from the toolbar, then verify no learning, no clipboard capture.
2. Incognito from Settings, then verify the same **without** changing fields — this is the path the
   §1 defect affected, and it should be the first thing retested after the fix.
3. Password field entry and exit in a browser and a password manager.
4. Inline autofill visibility, and a response arriving after a toggle.
5. Plugin content events before and after a transition, with a logging mod installed.
6. GIF search in normal and private modes, with the network observed.
7. Typo-Undo after cursor movement, expiry, and in an app that rewrites its own text.
8. Rotation and view recreation with a chip visible and incognito on.

---

## 7. Remaining limitations

1. **The §1 fix is verified by CI but not on a device.** The listener ordering that caused the bug
   is a platform behaviour the tests cannot reproduce.
2. **Previously delivered plugin data cannot be recalled.** Stated as a limit, not a defect.
3. **No device or emulator exists** in this environment. Every runtime claim is absent, not
   unfavourable.
4. **`applyPrivacyEffects` is untested by unit tests** — its correctness rests on reading.
5. **Pinned clips and `last_language` persist in incognito** by design; both are explicit user data
   or non-content, and both are documented in `PRIVACY.md`.
6. **Lint is not enforced**, so the compiler is the only static check.
