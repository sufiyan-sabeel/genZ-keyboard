# Phase 1 Technical Design — GenZ Keyboard v1.1

Five features, all built by **extending what already exists**. Two of them (#3 Typo-Undo and #23
Inline Autofill) are already partly implemented; the design says so rather than pretending
otherwise. Nothing here removes or replaces existing behaviour.

Governing rule from the brief, applied throughout: *incognito takes priority over every learning,
personalisation, clipboard-retention and optional-AI pathway.*

---

## 0. The one structural change everything depends on

Today "is the keyboard private right now?" is answered by reading `settings.incognito` — a stored
boolean — at eight separate call sites (`KeyboardService.kt:447, 497, 881, 1132, 1315, 1316, 2539,
2848`).

Adding automatic incognito by editing eight conditions would guarantee a missed one. Instead:

**Introduce one derived value, computed once per input session, and make every existing site read
it.**

```
effectiveIncognito : Boolean  =  manual || PASSWORD_FIELD || NO_PERSONALIZED_LEARNING || PACKAGE_RULE
```

- Owned by the service, recomputed in `resetForInput(info, restarting)` (`KeyboardService.kt:878`)
  and on manual toggle.
- The eight existing checks change from `settings.incognito` to `effectiveIncognito`. Same
  semantics, wider trigger.
- `settings.incognito` remains the *manual* switch and keeps its current meaning and persistence.

This is the smallest change that makes auto-incognito trustworthy: a new reason cannot be honoured
in one place and forgotten in another, because there is only one place.

---

## 1. #13 Zero-Trace Auto-Incognito

### Reuse
- `computeFlags()` (`KeyboardService.kt:847`) already detects password fields — no new detection.
- The suppression machinery already exists and is already correct (see audit §9).
- The toolbar already has an `incognito` icon and toggle (`KeyboardHeader.kt:648`).

### New components
- `IncognitoReason` — `MANUAL`, `PASSWORD_FIELD`, `NO_PERSONALIZED_LEARNING`, `PACKAGE_RULE`.
- `PrivacyState` — holds `reasons: Set<IncognitoReason>` and `sinceFieldKey: String`. Exposed to
  Compose through `KeyboardState` so the indicator recomposes; the *decision* stays in the service.
- A read of `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` in `computeFlags`, added to `FieldFlags`.

### Data flow
```
EditorInfo ──> computeFlags() ──> FieldFlags(password, noPersonalizedLearning, …)
                                        │
settings.incognito (manual) ────────────┤
                                        ▼
                            PrivacyState.reasons  ──> effectiveIncognito
                                        │                    │
                                        │                    ├──> 8 existing suppression sites
                                        └──> toolbar indicator (reason shown on tap)
```

### State and lifecycle
- Reset on every `onStartInputView` and whenever `fieldKey(info)` changes.
- Not persisted: it is derived from the current field, so process death cannot leave it stale.
- Manual incognito persists exactly as today.
- A reason is removed when its field is left — never accumulates across apps.

### Errors and edges
- `info == null` → no automatic reasons; manual only. Fails safe toward *existing* behaviour.
- Restart of the same field (`restarting = true`, same `fieldKey`) keeps the reasons.

### What is deliberately not promised
The indicator says what the keyboard does. It does not claim the host app, the OS or another IME
retains nothing — see `ANDROID_API_VERIFICATION.md` §1 and §8.

---

## 2. #3 Typo-Undo Pill

### Reuse — this already exists in part
`UndoAutoCorrectState(originalWord, committedText)` (`WordPredictor.kt:157`), the `undo` field
(`KeyboardService.kt:235`, set 1023/1197), and `SuggestionChipModel.isUndo` (`WordPredictor.kt:165`)
with its chip already rendered by the strip.

### What is missing
No expiry, no cancellation on cursor movement, and no verification that the editor still holds
the text being undone.

### Change
Extend the existing class rather than add a parallel one:

```kotlin
data class UndoAutoCorrectState(
    val originalWord: String,
    val committedText: String,
    val fieldKey: String,        // new: never offer an undo in a different field
    val expiresAtMillis: Long,   // new: bounded lifetime
)
```

### Cancellation rules
| Event | Action |
| --- | --- |
| cursor/selection moved | clear (via existing `onUpdateSelection`, line 965) |
| field changed | clear |
| undo tapped | clear after applying |
| lifetime elapsed | clear lazily on next read |
| backspace pressed | clear |

### Applying the undo — the risky part
Before replacing anything, re-read the editor:

1. `getTextBeforeCursor(committedText.length, 0)`
2. if it does not end with `committedText`, **abandon silently** and clear the state
3. otherwise `setComposingText(originalWord, 1)` then `finishComposingText()`
4. if any `InputConnection` call returns `false`, clear state and do nothing

No destructive replacement when the editor state is uncertain — this is the brief's explicit rule
and the reason step 2 exists.

### Performance
One `getTextBeforeCursor` of ≤ 32 characters on a user tap. Not on the typing path.

---

## 3. #1 Inline Math Evaluator

### Reuse
The suggestion strip's 3-slot model (`SuggestionChipModel`, `KeyboardHeader.kt:251`).

### New components (pure Kotlin, no new dependency)
- `MathToken` / `MathTokenType` — numbers, operators, parentheses.
- `MathParser` — shunting-yard to RPN. Deterministic; no recursion depth risk beyond token count.
- `MathEvaluator` — RPN evaluation with explicit failures:
  `DIVIDE_BY_ZERO`, `OVERFLOW`, `SYNTAX`, `UNBALANCED_PARENS`, `TOO_LONG`.
- `MathSuggestion` — `(expression, formattedResult)`.

### Rules that keep it safe
- **Never inserts on its own.** The result appears only as a chip; the user taps to commit. This
  is the brief's hard requirement.
- Input is capped (64 chars) before tokenising — bounded work, bounded regex, no pathological
  input.
- Only attempted when the composing text plausibly is an expression: contains a digit and one of
  `+ - * / × ÷ % ^ ( )`, and does not end with an operator being typed.
- Suppressed in password and incognito sessions (it is a suggestion; incognito wins).
- Formatting: integers without a decimal point; otherwise up to 6 significant digits; `1e9`
  style only above 10^9.

### Numeric policy
Use `Double` with explicit finite checks (`isFinite`) and pre-checks for division by zero; report
`OVERFLOW` rather than returning `Infinity`. A `BigDecimal` path is not warranted for keyboard
arithmetic and would slow the common case.

### Unit conversion
Kept **out of Phase 1**. It needs a unit table and a grammar extension; it is listed in the
implementation plan as a follow-up so the parser ships small and correct first.

---

## 4. #4 Smart Entity Action Chips

### Reuse
`kb.clipOffer: String?` (`KeyboardState.kt:194`) and its TTL/clear logic
(`KeyboardService.kt:685, 1992`), and the existing clipboard listener (line 275).

### Change
Replace the bare string with a typed model:

```kotlin
data class ClipEntity(
    val kind: EntityKind,          // URL, EMAIL, PHONE, OTP, ADDRESS, OTHER
    val text: String,              // in memory only, never persisted
    val confidence: Float,         // 0..1
    val sensitive: Boolean,        // EXTRA_IS_SENSITIVE, API 33+
)
```

### Classification order
1. API 31+: `ClipDescription.getClassificationStatus()`; if complete, read
   `getConfidenceScore(...)` for the relevant entity types.
2. API 33+: also read `EXTRA_IS_SENSITIVE` — **used to suppress preview, never to certify safety**.
3. API 26–30, or when classification is unavailable: bounded regex fallback.

### Bounded and safe
- Inspect at most the first 512 characters.
- Regexes are anchored, fixed-length and linear; no nested quantifiers.
- Classification runs off the main thread on the existing `serviceScope`; the chip appears when
  ready, and a stale result is discarded by generation counter.
- **Never** log, never persist, never put an OTP's digits in a chip label. An OTP chip reads
  "Paste code", not the code.
- Never auto-inserts. Every chip is an explicit user action.

### Actions offered
URL → Open / Copy · EMAIL → Mail / Copy · PHONE → Dial / Copy · OTP → Paste code · OTHER → Paste.

---

## 5. #23 Android 11+ Inline Autofill

### Already implemented — this is hardening, not construction
`KeyboardService.kt:617` builds a full `InlineSuggestionUi` style and an
`InlinePresentationSpec` bounded to 64×34dp … 200×38dp.

### Phase 1 work
1. **Fallback for API 26–29:** no inline suggestions exist there. The existing clipboard offer
   must remain the mechanism, and must not be suppressed by autofill code paths.
2. **Availability detection:** if the host supplies no autofill service, behave exactly as today —
   no empty chip, no reserved space.
3. **Lifecycle:** clear request/response state in `onFinishInputView` (line 667) and on field
   change. Cleanup is by dropping the reference and letting the view detach; the platform
   `InlineContentView` has **no `release()`/`destroy()`** (verified — see
   `ANDROID_API_VERIFICATION.md` §5.2). `onDetachedFromWindow` is the only legitimate hook.
4. **Never block rendering:** the request is built on the service thread and is already wrapped in
   `runCatching`; failures must degrade to "no suggestions", never to a broken keyboard.

### Explicitly out of scope
Re-implementing the chip rendering. The current implementation already matches the keyboard's
palette via `KeyboardGeometry.palette`.

---

## 6. State ownership summary

| State | Owner | Lifetime | Survives process death |
| --- | --- | --- | --- |
| `settings.incognito` (manual) | `KeyboardPrefs` | until user changes | yes |
| `PrivacyState.reasons` | service | per input session/field | no (derived) |
| `undo` | service | per word, ≤ few seconds | no |
| math expression/result | `KeyboardState` (compose) | per composing word | no |
| `ClipEntity` | `KeyboardState` | per clip, TTL | no |
| autofill request/response | service | per field | no |

Only user *choices* are persisted. Everything derived from a field or a clip is memory-only,
which is what makes "zero-trace" true of the keyboard's own storage.

## 7. Threading

- Typing path: main thread, unchanged.
- Classification, math evaluation above the cap, and dictionary work: `serviceScope` (IO).
- Every background result carries a generation/field key and is discarded if stale — the existing
  code already does this for suggestions (`precomputedFor`), and the new work follows the same
  pattern.

## 8. Error handling policy

Every new failure mode degrades to *previous behaviour*, never to a crash and never to a
half-applied edit:

| Failure | Behaviour |
| --- | --- |
| `InputConnection` returns false/null | abandon the edit, clear state |
| editor text does not match expectation | abandon silently |
| classification unavailable | fall back to regex, then to plain paste |
| math parse fails | show nothing |
| autofill request throws | no suggestions |

## 9. Permissions

**No new permissions.** Verified against the manifest: the five features need none beyond what is
already declared. `INTERNET` is untouched and no feature transmits typed text.

## 10. Performance budgets (targets, not measurements)

| Path | Budget | How it will be measured |
| --- | --- | --- |
| Incognito decision | < 1 ms | `System.nanoTime()` around `resetForInput`, logged only in a debug build |
| Math evaluation | < 1 ms for ≤ 64 chars | JVM unit benchmark over a fixed expression corpus |
| Entity classification | off main thread, ≤ 512 chars | trace section around the classifier |
| Typo-undo apply | one `getTextBeforeCursor` on tap | trace section |
| Autofill request build | must not appear in a frame trace | Macrobenchmark frame timing on device |

**None of these are claimed as achieved.** There is no device or emulator in the environment that
produced this document, so nothing here has been measured.
