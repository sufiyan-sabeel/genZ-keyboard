# Privacy and Regression Audit — Features #13, #3, #23

- **Audited commits:** `f8f2a759` (#13), `0177b2ea` (#3), `dfc844b0` (#23)
- **Fix commit produced by this audit:** `9d19041d4200c9660e684cbdc898872b6bc1c66e`
- **Method:** source-level reading of every call site, plus the JVM suite executed in CI.
- **Not performed:** any run on a device or emulator. Nothing below claims device verification.

## Verdict

| Feature | Status | Why |
| --- | --- | --- |
| #13 Effective Incognito | **PASS WITH LIMITATIONS** | five leaks found and fixed; one out-of-scope network bypass documented, not fixed |
| #3 Typo-Undo | **PASS WITH LIMITATIONS** | one residual race inherent to `InputConnection`, narrowed but not eliminable |
| #23 Inline Autofill | **PASS WITH LIMITATIONS** | lifecycle/concurrency defects fixed; inflate-and-render path unverified without a device |

No feature is FAIL. No feature is PASS without qualification, because none has run on hardware.

---

## 1. Sources audited

| File | What was examined |
| --- | --- |
| `KeyboardService.kt` | all 13 `privacy.isIncognito` sites; `resetForInput`; `onUpdateSelection`; `onCreateInputView`; `onFinishInputView`; `onCreateInlineSuggestionsRequest`; `onInlineSuggestionsResponse`; `pickSuggestion`; `replaceTapWord`; `commitEmoji`; `syncClipboard`; `aiRun`; `aiFix`; `lateCorrect`; `commitWithBoundary`; `icDeleteBefore` |
| `PrivacyState.kt` | `derive`, `isIncognito`, `forcedByField` |
| `EditorFieldSpec.kt` | `of`, `isPrivate` |
| `UndoGuard.kt` | `isLive`, `matchesEditor`, `TTL_MILLIS` |
| `InlineAutofillPolicy.kt` | `isAllowed`, `bounds`, `suggestionCount`, `accepts`, `mayShow` |
| `WordPredictor.kt` | `UndoAutoCorrectState`, `SuggestionChipModel` |
| `KeyboardState.kt` | `inlineSuggestions`, `privateField` |
| `KeyboardHeader.kt` | the `inline` strip gate |
| `ScriptEngine.kt` | every `privateMode` check |
| `AndroidManifest.xml` | permission set, IME declaration |

---

## 2. Privacy interaction matrix

Verified by reading the code path for each combination. "Suppressed" means no persistent write and
no network call.

| | Manual off, normal field | Manual off, password | Manual off, `NO_PERSONALIZED_LEARNING` | Manual on, normal field | Manual on, password |
| --- | --- | --- | --- | --- | --- |
| Word learning | allowed | suppressed | suppressed | suppressed | suppressed |
| `rejectCorrection` write | allowed | **was leaking → fixed** | **was leaking → fixed** | **was leaking → fixed** | fixed |
| CJK learning | allowed | suppressed | suppressed | suppressed | suppressed |
| Emoji recents | allowed | allowed (not field-gated by design) | allowed | **was leaking → fixed** | fixed |
| Clipboard capture | allowed | suppressed | suppressed | suppressed | suppressed |
| Clipboard offer chip | allowed | suppressed | suppressed | suppressed | suppressed |
| AI fix / AI run / voice cleanup | allowed | suppressed | suppressed | suppressed | suppressed |
| Plugin `word`/`press`/`key` events | allowed | suppressed | suppressed | **was stale → fixed** | fixed |
| Plugin text reading | allowed | suppressed | suppressed | stale → fixed | fixed |
| Inline autofill request | allowed | blocked | blocked | blocked | blocked |
| Inline strip visible | shown | hidden | hidden | **stayed visible → fixed** | fixed |
| System spellcheck | allowed | suppressed | suppressed | suppressed | suppressed |

### Defects found and fixed

**P1 — `ScriptHost.privateMode` went stale when the switch flipped mid-field.**
It was written only in `resetForInput`. Turning incognito on while a field stayed active left
plugins with `privateMode = false` until the next field change — plugins could keep reading text
after the user asked for privacy. This is the audit's most serious finding, because the brief
correctly warns against assuming a call site is safe because another one checks. Fixed by
re-synchronising the flag whenever the derived state is recomputed.

**P2 — three `rejectCorrection` writes bypassed privacy.**
The "do not correct this word again" marker is written to the dictionary. Three call sites
(`replaceTapWord`, the backspace-undo path, the keep-exact chip path) called it directly rather
than through the gated `learn()` helper, so the word persisted in incognito.

**P3 — emoji recents were recorded in incognito.** Which emoji a person sends is user data.

**P4 — a late autofill response could become visible after privacy changed.**
`isAllowed` was checked when the response arrived, but inflation is asynchronous; switching
incognito on mid-inflation still published the views. Now re-checked at publish time.

**P5 — already-visible inline suggestions survived a manual toggle.** The strip's render gate did
not consider privacy at all. Now gated on `InlineAutofillPolicy.mayShow(manual, privateField)`,
so the two rules cannot drift.

### Documented, deliberately not fixed

**GIF search is not gated by privacy.** `ActivePanel.GIF_SEARCH` performs a network query with the
user's search text and has no `privacy.isIncognito` check. This is a genuine bypass of the
"incognito" promise, but it belongs to a feature outside #13/#3/#23, and blocking it is a product
decision (some users want GIFs while in incognito). Flagged for a decision rather than changed
silently.

**The suggestion strip still shows dictionary predictions in incognito.** Incognito means "does not
remember", not "cannot suggest". Learning and persistence are suppressed; display is not. Changing
it would remove working functionality.

---

## 3. Typo-Undo safety findings

| Requirement | Result |
| --- | --- |
| Valid only for the matching field | **PASS** — `UndoGuard.isLive` compares `fieldKey` |
| Expired state cannot be displayed | **PASS** — `liveUndo()` returns null past the deadline, so no chip is built |
| Expired state cannot be applied | **PASS** — the apply path calls `liveUndo()` again |
| Cursor movement invalidates | **PASS** — pre-existing `onUpdateSelection` clear, retained |
| Editor switching invalidates | **PASS** — `resetForInput` clear, plus field identity |
| Stale chip cannot insert text | **PASS** — an undo chip with no live state returns instead of falling through (which would have committed a bare space) |
| No unsafe deletion after content change | **PASS** — `matchesEditor` verifies the tail before deleting |
| Incognito undo writes no learned data | **PASS** — both `rejectCorrection` and `learn` skipped; the P2 fix closed the second path |

### The residual race — cannot be eliminated

The check and the deletion are two separate IPC calls to the host app:

```
getTextBeforeCursor(n)      → check the tail matches
deleteSurroundingText(n, 0) → delete n characters
```

`InputConnection` exposes no atomic compare-and-delete, and the host is a different process that
may edit its own text between the two calls. `deleteSurroundingText` deletes **by count**, not by
content, so if the app changed the text the wrong characters go.

What the implementation does about it:

1. The read now happens **inside** `beginBatchEdit()`/`endBatchEdit()`, so the editor is not
   applying its own edits between the check and the delete. This narrows the window but is not a
   guarantee — batch edit is advisory.
2. The check and delete run in the same main-thread turn with no suspension between them.
3. If the tail does not match, nothing is deleted and the offer is dropped.

**Residual risk:** an app that rewrites its text from another thread within that window can still
have the wrong span deleted. This is a limitation of the platform API, not of this code, and it is
recorded here rather than papered over.

---

## 4. Inline Autofill lifecycle and concurrency findings

| Requirement | Result |
| --- | --- |
| Generation captured at the start of each response | **PASS** — `val gen = ++inlineGeneration` is the first statement |
| Every async completion checks the generation | **PASS** — both the inflate callback and the failure branch compare `gen` |
| Older responses cannot overwrite newer ones | **PASS** — a stale generation cannot publish; the list is cleared when a new response starts |
| Field change clears internal and rendered state | **PASS** — `resetForInput` clears the list and bumps the generation |
| Input-view recreation invalidates previous work | **PASS** — `onCreateInputView` clears and bumps (the old composition's views are gone) |
| Privacy change invalidates pending responses | **PASS** — publish re-checks `isAllowed` (P4) |
| Privacy change hides rendered suggestions | **PASS** — `mayShow` gate in `HeaderBar` (P5) |
| Empty response degrades safely | **PASS** — `accepts(0)` is false; the strip is cleared, nothing reserved |
| Malformed / failed response degrades safely | **PASS** — per-suggestion `runCatching`; a throwing `inflate` decrements the counter and the rest still publish |
| Request builder failure degrades safely | **PASS** — the whole builder is wrapped in `runCatching { }.getOrNull()`, returning null = "no suggestions" |
| State updates on the right thread | **PASS** — `inflate` is given `mainExecutor`; `kb` state is written only from those callbacks and from service callbacks already on the main thread |
| Unsupported API levels degrade | **PASS** — both overrides are `@RequiresApi(30)`; below 30 the platform never calls them and the clipboard offer remains |

### Callback ordering — checked, and it matters

`onCreateInlineSuggestionsRequest` can be invoked **before** `onStartInputView` has run
`resetForInput`. The earlier implementation would therefore have decided privacy from a stale
cached `fieldSpec` — the previous field's. The inline paths now derive privacy with
`privacyForCurrentField()`, which reads `currentInputEditorInfo` at call time, so the
decision does not depend on callback order. This is verified by reading the ordering, not by
observing it on a device.

---

## 5. Tests executed and exact results

Executed in CI on the fix commit `9d19041d`. Counts parsed from the uploaded Gradle report.

**153 tests, 0 failures, 0 skipped.**

| Suite | Tests | Failures |
| --- | ---: | ---: |
| `AnimeThemesTest` | 8 | 0 |
| `AutocorrectEval` | 2 | 0 |
| `ContractionsTest` | 3 | 0 |
| `CustomAiTest` | 3 | 0 |
| `EditorFieldSpecTest` | 17 | 0 |
| `EmojiSuggestTest` | 5 | 0 |
| `GlideEval` | 2 | 0 |
| `InlineAutofillPolicyTest` | 22 | 0 |
| `KeySoundsTest` | 1 | 0 |
| `LearnedTypoTest` | 1 | 0 |
| `ModEffectsTest` | 10 | 0 |
| `MyanmarInputTest` | 11 | 0 |
| `PhraseEval` | 2 | 0 |
| `PredictionTest` | 4 | 0 |
| `PrivacyStateTest` | 23 | 0 |
| `SuggestionChipModelTest` | 9 | 0 |
| `ThemePaletteTest` | 5 | 0 |
| `UndoGuardTest` | 20 | 0 |
| `UserLayoutsTest` | 5 | 0 |

Six tests were added by this audit, for invariants that were genuinely uncovered:

- `forcedByFieldIsTrueWhenManualAndAFieldReasonCoexist`
- `turningTheManualSwitchOffKeepsTheFieldReasonMarkedAsForced`
- `aFieldOnlyStateIsAlwaysForced`
- `theStripIsHiddenByEitherPrivacySignal`
- `theStripVisibilityRuleAgreesWithTheRequestGate`
- `everyReasonBlocksInlineAutofill`

CI steps also green: instrumentation-test compilation, dependency policy gate, F-Droid flavour
build, flavour separation (`standard` 10 artwork files / `fdroid` 0).

**Not run:** `connectedAndroidTest` (no device, no emulator, no KVM in the environment). Lint is
not wired into CI — `checkReleaseBuilds = false` is a pre-existing project decision for
material3 1.5, so the only static analysis enforced is the Kotlin compiler.

### What the tests do not cover

The five fixed defects are **side-effect gating in the service** — "does this call happen in
incognito". There is no unit-test seam for that: it needs a bound `InputMethodService` and a fake
`InputConnection`. Those fixes are verified by reading the code and by compilation, not by test.
Stating this plainly is more useful than implying coverage that does not exist.

---

## 6. Remaining risks

| # | Risk | Severity | Needs |
| --- | --- | --- | --- |
| 1 | Undo check→delete race with a host that edits concurrently | medium | accepted; documented above |
| 2 | GIF search bypasses incognito | medium | product decision |
| 3 | Nothing has run on hardware | **high** | device or emulator |
| 4 | Inline autofill inflate-and-render never observed | high | device + autofill service |
| 5 | `ScriptHost.privateMode` is now written from a property getter | low | review; it is a volatile boolean write, thread-safe, but it is a side effect in a getter |
| 6 | Emoji recents remain enabled in a password field (by design, not field-gated) | low | confirm intended |

### Device testing still required

1. Password field in Chrome, WhatsApp and a password manager: no suggestions, no inline chip, no
   learned words after the session.
2. Toggle incognito **while a field is active** and confirm: plugin text access stops immediately,
   the inline chip disappears, no dictionary write occurs.
3. Typo-undo: correct, tap undo, confirm exact restoration; then move the cursor and confirm the
   chip is gone.
4. Typo-undo with an app that rewrites its text (Termux, a code editor): confirm no corruption.
5. Inline autofill with Google Password Manager: chip appears and is themed; with no autofill
   service: nothing appears and no space is reserved.
6. Rotation with a chip visible: confirm no stale view survives.

Until these are run, the three features are **PASS WITH LIMITATIONS**, not PASS.
