# Implementation Specifications — #23, #3, #13

Precise, code-level specifications for the three features that build on existing code. Written
against commit `78630d0` plus the test-foundation commit that added `EditorFieldSpec.kt`.

Every path and line number was read from the repository. API claims cite
`ANDROID_API_VERIFICATION.md`.

**Status of this document:** specification only. No feature behaviour has been implemented.

---

## 0. The test seam that now exists

`computeFlags()` was untestable: it was `private` on the service and needed a live `EditorInfo`.
The field rules have been extracted to **`app/src/main/java/com/genz/keyboard/EditorFieldSpec.kt`**
as a pure function:

```kotlin
EditorFieldSpecs.of(inputType: Int, imeOptions: Int, correctEverywhere: Boolean): EditorFieldSpec
```

`computeFlags` now delegates to it and maps the result onto the existing `FieldFlags`. Behaviour
is unchanged — same predicates, same order, same inputs — and `EditorFieldSpecTest` (18 cases)
pins it.

`EditorFieldSpec` also carries `noPersonalizedLearning`, read from
`EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` but **not yet consumed by anything**. That is the
deliberate seam for #13: the value is computed and tested before any behaviour depends on it.

---

## 1. Feature #13 — Zero-Trace Auto-Incognito

> **Status: IMPLEMENTED in v1.1 Phase 1 (see §1.8).** The specification below is kept as written
> so the implementation can be reviewed against the plan rather than against itself. #3 and #23
> remain unimplemented.

### 1.1 Existing implementation

| Piece | Location | State |
| --- | --- | --- |
| Manual incognito toggle | `KeyboardHeader.kt:648-650`, pref `incognito` | works |
| Password detection | `EditorFieldSpec.kt` (was `KeyboardService.kt:847`) | works, now unit-tested |
| Learning suppressed in incognito | `KeyboardService.kt:1315` | works |
| Plugin `word` event suppressed | `KeyboardService.kt:1316` | works |
| Clipboard history save suppressed | `KeyboardService.kt:2539` | works |
| Clipboard restore at boot suppressed | `KeyboardService.kt:447` | works |
| Screenshot capture suppressed | `KeyboardService.kt:497` | works |
| System spellcheck suppressed | `KeyboardService.kt:1132` | works |
| AI fix suppressed | `KeyboardService.kt:2848` | works |
| Script private mode | `KeyboardService.kt:881` | works |
| Wipe helper | `SecretBox.kt:97` `PrivateData` | works |

The **suppression** half is complete and correct. The **activation** half does not exist.

### 1.2 Missing behaviour

1. `IME_FLAG_NO_PERSONALIZED_LEARNING` is never acted on. It is now *read* by
   `EditorFieldSpec` and tested, but nothing consumes it.
2. Password fields are suppressed by `flags.isPassword` at some sites but not by the single
   `settings.incognito` check at others — so the two ideas are duplicated rather than unified.
3. No reason/indicator: the toolbar shows on/off, never *why*.
4. No package rule.
5. `settings.incognito` is read at eight sites; adding a reason means eight edits, which is the
   defect this feature must not repeat.

### 1.3 Files requiring modification

| File | Change |
| --- | --- |
| `EditorFieldSpec.kt` | none — already exposes `noPersonalizedLearning` |
| `KeyboardService.kt` | add `effectiveIncognito`; compute in `resetForInput` (878); replace the 8 reads at 447, 497, 881, 1132, 1315, 1316, 2539, 2848 |
| **new** `PrivacyState.kt` | `IncognitoReason` enum, reason set, `effectiveIncognito` derivation |
| `KeyboardState.kt` | one observable field carrying the reasons to Compose |
| `KeyboardHeader.kt:648` | show the active reason next to the existing icon |
| `KeyboardSettings.kt` | optional: package-rule list, empty by default |

### 1.4 API-level constraints

- `IME_FLAG_NO_PERSONALIZED_LEARNING` — **API 26+**, available on every supported version; no guard.
- Documented as *"not a guarantee"*: UI copy must describe what this keyboard does, never what the
  host or OS retains.
- `EditorInfo.packageName` is only trustworthy from Android M onward, and an app controls its own
  fields regardless — a package rule is a convenience, not a boundary.

### 1.5 Test cases

Unit (JVM, runnable now — `EditorFieldSpecTest`): 18 cases already cover password variations,
address fields, the no-suggestions flag, `correctEverywhere` precedence, capitalisation, and the
privacy flag including "unrelated IME options must not imply privacy".

Unit (to add with the feature): reason-set derivation — manual + password + flag combinations,
clearing on field change, retention on same-field restart, `EditorInfo == null` → manual only.

Instrumentation (to add): indicator shows a reason; learned-word storage is empty after an
incognito session.

### 1.6 Regression risks

- **Highest:** a missed site. After the change, `grep -c "settings.incognito" KeyboardService.kt`
  must be **0**; anything else means a path that can still learn.
- Manual incognito must behave identically when no automatic reason fires.
- `resetForInput` runs on every field start — it must stay cheap (budget < 1 ms).

### 1.7 Acceptance criteria

1. Password field ⇒ incognito, reason `PASSWORD_FIELD`.
2. `IME_FLAG_NO_PERSONALIZED_LEARNING` on a plain field ⇒ incognito, reason `NO_PERSONALIZED_LEARNING`.
3. Manual off + automatic reason ⇒ still incognito.
4. Leaving the field clears automatic reasons; manual persists.
5. Zero `settings.incognito` reads remain outside `PrivacyState`.
6. No new permission; no network use.

### 1.8 Implementation record

What was actually built, and how it differs from the plan above:

| Plan | Implemented |
| --- | --- |
| `PrivacyState.kt` with `IncognitoReason` | yes — `MANUAL`, `PASSWORD_FIELD`, `NO_PERSONALIZED_LEARNING` |
| One derived `effectiveIncognito` | yes — `PrivacyState.derive(manual, fieldSpec)`, exposed as the service's `privacy` |
| Replace the 8 reads | 9 sites routed through `privacy.isIncognito` (the plan counted 8; `ScriptHost.privateMode` was a ninth) |
| Package rule | **not built** — out of scope for this milestone, and a package name is a weak signal |
| Indicator showing the reason | **not built** — UI change, deliberately left for review |

Four additional paths were upgraded from `flags.isPassword` to `privacy.isIncognito` because they
transmit text off the device or retain it: AI voice cleanup, `aiRun`, `aiFix`, and the clipboard
paste offer. This **strengthens** privacy (previously manual incognito did not stop AI calls) and
is required by "incognito takes priority over every optional AI pathway".

Deliberately **not** changed: the suggestion strip still shows dictionary predictions in
incognito. Incognito has always meant "does not remember", not "cannot suggest"; hiding the strip
would remove working functionality the brief protects. Learning, plugin visibility and clipboard
retention are all suppressed.

`privacy` is cached on its two inputs (`settings.incognito` and the current `EditorFieldSpec`), so
repeated reads during typing allocate nothing, and a manual toggle is picked up on the next read
with no invalidation step to forget.

---

## 2. Feature #3 — Typo-Undo Pill

> **Status: IMPLEMENTED in v1.1 Phase 1 (see §2.8).** The specification below is kept as written so
> the implementation can be reviewed against the plan. #23 remains unimplemented.

### 2.1 Existing implementation

| Piece | Location | State |
| --- | --- | --- |
| Undo state holder | `WordPredictor.kt:157` `UndoAutoCorrectState(originalWord, committedText)` | exists |
| Service field | `KeyboardService.kt:235` `undo`, set at 1023 and 1197 | exists |
| Undo chip concept | `WordPredictor.kt:165` `SuggestionChipModel.isUndo` | exists, rendered |
| `isEmpty` excludes undo chips | `WordPredictor.kt:167` | exists, now unit-tested |
| Correction source | `WordPredictor.kt:1353` `autoCorrectionFor(...)` | exists |
| Selection tracking | `KeyboardService.kt:965` `onUpdateSelection` | exists |

A user-visible undo pill therefore already exists. What is missing is safety.

### 2.2 Missing behaviour

1. **No expiry** — the state survives indefinitely.
2. **No field identity** — an undo captured in one field can be offered in another.
3. **No editor verification** — applying an undo replaces text without checking the editor still
   holds it. This is the dangerous one.
4. No cancellation on cursor movement, backspace, or autocorrect being disabled mid-word.

### 2.3 Files requiring modification

| File | Change |
| --- | --- |
| `WordPredictor.kt:157` | add `fieldKey: String`, `expiresAtMillis: Long` to `UndoAutoCorrectState` |
| `KeyboardService.kt:1023, 1197` | populate the new fields (`fieldKey(info)` at 875) |
| `KeyboardService.kt` undo-apply path | verify `getTextBeforeCursor(n, 0)` ends with `committedText`; abandon on mismatch |
| `KeyboardService.kt:965` `onUpdateSelection` | clear on cursor/selection change |
| `KeyboardService.kt` backspace path | clear |
| `SuggestionChipModel` slot selection | suppress the undo chip when the state is stale |

### 2.4 API-level constraints

- `InputConnection.getTextBeforeCursor` returns `CharSequence?` and may return `null`.
- `setComposingText` / `finishComposingText` return `Boolean`; `false` means the editor ignored it.
- The connection can be invalidated between calls. All three must be tolerated on every API level
  (26+); there is no version-specific behaviour here.

### 2.5 Test cases

Unit (JVM, runnable now — `SuggestionChipModelTest`): 9 cases pin the three-slot contract, that an
undo chip is never "empty", and that `UndoAutoCorrectState` keeps both words distinct.

Unit (to add): expiry logic, field-key mismatch, cancellation table — as a pure function over the
state, which is why the two new fields belong on the data class rather than in the service.

Instrumentation (to add): apply against a real `EditText`; mismatch case must leave the text
untouched; `InputConnection` returning false must not throw.

### 2.6 Regression risks

- A wrong replacement corrupts user text — worse than having no undo. Mitigated by
  abandon-on-mismatch.
- Clearing too eagerly removes a working feature. The existing chip must keep working when the
  editor does match.
- Extra `getTextBeforeCursor` on a tap must not run on the typing path.

### 2.7 Acceptance criteria

1. Undo restores the original word when the editor state matches.
2. Undo is a no-op when the editor state does not match.
3. Chip disappears on cursor move, field change, backspace, or expiry.
4. No crash when the connection is null or returns `false`.
5. Typing latency unchanged.

### 2.8 Implementation record

What was actually built, and what the audit changed about the plan:

| Plan | Implemented |
| --- | --- |
| `fieldKey` + `expiresAtMillis` on `UndoAutoCorrectState` | yes, with defaults so existing call sites keep compiling |
| Expiry | `UndoGuard.TTL_MILLIS` = 6 s, enforced on every read via `liveUndo()` |
| Cancel on cursor move | **already existed** (`KeyboardService.kt` `onUpdateSelection` sets `undo = null`) — not duplicated |
| Cancel on editor switch | **already existed** (`resetForInput` sets `undo = null`) — field identity added as a second, independent guard |
| Verify the editor before replacing | `UndoGuard.matchesEditor(...)` against `getTextBeforeCursor(committedText.length, 0)`; mismatch ⇒ clear and change nothing |
| Stale chip must be inert | yes — an undo chip with no live state now returns instead of falling through, which would have committed a bare space |
| Incognito | the undo still works, but `rejectCorrection` and `learn(LEARNED_TYPO)` are skipped, so nothing about a private session is written to the dictionary |

Two findings from the audit worth recording:

1. **Most cancellation already worked.** `onUpdateSelection` already cleared `undo` when the cursor
   moved and `resetForInput` already cleared it on a field change. The plan proposed adding both;
   adding them again would have been duplicate logic, so only the *independent* guard (field
   identity) was added.
2. **The real defect was the apply path.** It deleted `committedText.length` characters with no
   check that those characters were still there. That is the data-loss bug this feature fixes.

The chip's appearance, layout and accessibility are untouched: no UI change was made.

---

## 3. Feature #23 — Android 11+ Inline Autofill

> **Status: IMPLEMENTED (hardening) in v1.1 Phase 1 (see §3.8).** The specification below is kept
> as written so the implementation can be reviewed against the plan.

### 3.1 Existing implementation

`KeyboardService.kt:617`, `@RequiresApi(Build.VERSION_CODES.R)`:

```kotlin
override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest?
```

Builds `androidx.autofill.inline.v1.InlineSuggestionUi` styles (single-icon chip, chip, title,
subtitle, start icon) and an `InlinePresentationSpec` bounded to `64×34dp` … `200×38dp`, tinted
from `KeyboardGeometry.palette` so the chip matches the active theme. Wrapped in `runCatching`.
Dependency `androidx.autofill:autofill:1.1.0` (`app/build.gradle.kts:157`).

**This feature is substantially built.** The specification is about correctness and fallback.

### 3.2 Missing behaviour

1. **No explicit cleanup on field end.** `onFinishInputView` (667) does not clear
   request/response state.
2. **Fallback not asserted.** API 26–29 has no inline suggestions; the clipboard offer path must
   remain the mechanism there, and nothing currently proves it is not suppressed.
3. **No availability signal** — behaviour when no autofill service is installed is untested.
4. **No instrumentation test at all** — the repo has never had an `androidTest` source set.

### 3.3 Files requiring modification

| File | Change |
| --- | --- |
| `KeyboardService.kt:667` | clear request/response state in `onFinishInputView` |
| `KeyboardService.kt:617` | keep `runCatching`; ensure failure never reserves layout space |
| `KeyboardService.kt` clipboard offer path | assert the API 26–29 fallback still runs |
| `app/src/androidTest/...` | new instrumentation coverage (scaffold now exists) |

### 3.4 API-level constraints — verified, not assumed

- `onCreateInlineSuggestionsRequest` — **API 30**; the repository's `@RequiresApi(R)` annotation is
  the evidence (the reference pages returned 404 during the audit).
- `android.widget.inline.InlineContentView` public surface is exactly `getSurfaceControl`,
  `isZOrderedOnTop`, `setZOrderedOnTop`, `setClipBounds`, `setSurfaceControlCallback`, `onLayout`,
  plus protected `onAttachedToWindow` / `onDetachedFromWindow`.
  **There is no `release()`, no `destroy()`, and no platform `setInlineContentCallback`.**
  Cleanup is dropping the reference and letting the view detach.
- API 26–29: no inline suggestions exist; the fallback is the existing clipboard offer.

### 3.5 Test cases

Unit (JVM): request-builder failure path must not throw out of the service; a null/absent autofill
service must produce "no suggestions", not an empty reserved chip.

Instrumentation (to add, device required): chip appears with an autofill service; no chip and no
reserved space without one; dismissal mid-request leaks nothing; field change mid-request discards
the stale response.

Regression: keyboard still renders if the request throws.

### 3.6 Regression risks

- Clearing state too aggressively could remove a working chip.
- A reserved-but-empty area would look like a layout bug — worse than no chip.
- Any call to a non-existent cleanup method would fail to compile; the verified surface above
  exists to prevent exactly that.

### 3.7 Acceptance criteria

1. API 30+ with an autofill service: chip appears, themed.
2. API 30+ without one: nothing appears, no space reserved.
3. API 26–29: no inline path; clipboard offer still works.
4. Dismissal and field change leave no stale state.
5. No use of an API that does not exist.

### 3.8 Implementation record

The audit found the feature **already built end to end**, including something the earlier design
document did not know about: an `onInlineSuggestionsResponse` override that inflates the system's
suggestion views into `KeyboardState.inlineSuggestions`, which `KeyboardHeader.kt` renders in the
suggestion row. So this milestone is genuinely hardening, not construction.

Three real defects were found and fixed (a fourth suspected issue turned out not to exist):

| Defect | Consequence | Fix |
| --- | --- | --- |
| **No privacy gate at all** | an autofill service was asked for stored credentials in password fields and in incognito — the most sensitive thing the keyboard could display | `InlineAutofillPolicy.isAllowed(privacy)` checked when building the request **and** again before showing a response; the request returns `null` (the documented opt-out) |
| **Suggestions not cleared on field change** | views inflated for field A could render in field B, because only `onFinishInputView` cleared them | `resetForInput` clears the list and bumps a generation counter; `onCreateInputView` does the same because the composition was disposed |
| **Overlapping responses could mix** | two in-flight responses both wrote the strip; the slower one won, showing stale credentials | monotonic `inlineGeneration`; a callback whose generation is stale is ignored |
| ~~Missing `@RequiresApi(30)`~~ | **not a defect** — the original already carried the annotation; my first pass duplicated it and CI-side review caught the duplicate | duplicate removed, annotation unchanged |

Privacy is derived with `privacyForCurrentField()`, which reads `currentInputEditorInfo` directly
rather than the cached `fieldSpec`. This matters: the platform can call
`onCreateInlineSuggestionsRequest` **before** `onStartInputView` has run `resetForInput`, so the
cached value may still describe the previous field. Using the live editor removes that ordering
assumption.

Sizes and the suggestion count now come from `InlineAutofillPolicy.bounds(density)` and
`suggestionCount(...)`, so the 64×34dp … 200×38dp window and the 6-suggestion cap exist in one
place and are covered by tests.

No UI was changed: the chip geometry, colours and the row it renders in are untouched.

---

## 4. Recommended order

`#13` (highest privacy value, smallest surface, tests already in place) → `#3` (safety-critical
undo) → `#23` (mostly hardening, needs a device to verify).

`#13` first also establishes `effectiveIncognito`, which #3 and the future #4 read for suppression.
