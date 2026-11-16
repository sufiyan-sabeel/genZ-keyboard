# Phase 1 Implementation Plan — GenZ Keyboard v1.1

Dependency-ordered. Every file path was read from the repository at commit `78630d0`; nothing is
guessed. Steps are ordered so that each one leaves the app compiling and shippable, because a
keyboard that half-works is worse than one that does not have the feature yet.

**No source code is modified by this document.** It is the plan the brief asked for.

---

## Step 0 — Establish a safety net (before touching anything)

**Why first:** there is no `androidTest` source set at all. Any change to commit/undo behaviour is
currently unverifiable.

| Action | File |
| --- | --- |
| Add `app/src/androidTest/` with an `EditText` host and a fake-bound IME test | new: `app/src/androidTest/java/com/genz/keyboard/ImeHarnessTest.kt` |
| Add the instrumentation runner to Gradle | `app/build.gradle.kts` |
| Add a `FakeInputConnection` test double for L2 tests | new: `app/src/test/java/com/genz/keyboard/FakeInputConnection.kt` |

**Complexity:** medium (one-time). **Risk:** none to shipped behaviour — test-only.
**Rollback:** delete the source set.

---

## Step 1 — Single source of truth for privacy (#13 prerequisite)

Everything else in #13 depends on this and nothing else depends on #13.

| Change | File | Detail |
| --- | --- | --- |
| Add `noPersonalizedLearning` to `FieldFlags`; read `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` | `KeyboardService.kt` (`computeFlags`, ~847) | flag is API 26+, always available |
| Add `IncognitoReason` enum and `PrivacyState` | new: `PrivacyState.kt` | ~60 lines, no dependencies |
| Compute `effectiveIncognito` in `resetForInput` (~878) | `KeyboardService.kt` | one assignment |
| Replace 8 reads of `settings.incognito` with `effectiveIncognito` | `KeyboardService.kt:447, 497, 881, 1132, 1315, 1316, 2539, 2848` | behaviour-preserving; each site keeps its other conditions |
| Expose reasons to Compose | `KeyboardState.kt` | one new observable field |
| Show reason on the existing incognito icon | `KeyboardHeader.kt:648` | reuse the existing icon slot |

**Complexity:** low-medium. **Risk:** medium — this touches eight suppression sites. Mitigated by
being behaviour-preserving when no automatic reason fires: with only `MANUAL`, `effectiveIncognito`
is identical to `settings.incognito` today.

**Verification:** tests 13.1–13.9 pass; a manual check that manual incognito still behaves exactly
as before.

**Rollback:** revert the eight substitutions; `PrivacyState` becomes unused.

---

## Step 2 — Auto-incognito activation (#13 completion)

Depends on Step 1.

| Change | File |
| --- | --- |
| Map `FieldFlags` + package rule to `Set<IncognitoReason>` | `PrivacyState.kt` |
| Clear reasons on field change; keep on same-field restart | `KeyboardService.kt` (`onStartInputView` 586, `fieldKey` 875) |
| Optional package rule (off by default, empty list) | `KeyboardSettings.kt` + settings UI `SettingsTyping.kt` |

**Complexity:** low. **Risk:** low — a missing signal degrades to today's behaviour.
**Rollback:** stop adding non-`MANUAL` reasons; Step 1 remains harmless.

---

## Step 3 — Typo-Undo hardening (#3)

Independent of Steps 1–2; can be done in parallel.

| Change | File |
| --- | --- |
| Extend `UndoAutoCorrectState` with `fieldKey`, `expiresAtMillis` | `WordPredictor.kt:157` |
| Set the new fields at the two assignment sites | `KeyboardService.kt:1023, 1197` |
| Cancel on selection change | `KeyboardService.kt` (`onUpdateSelection`, 965) |
| Cancel on backspace / field change / expiry | `KeyboardService.kt` |
| Verify editor text before replacing; abandon on mismatch | `KeyboardService.kt` (undo apply path) |

**Complexity:** medium — the verification step is where bugs hide.
**Risk:** medium. Mitigated by the abandon-on-mismatch rule: the worst case is a missing undo, not
corrupted text.

**Rollback:** the existing chip keeps working; revert the apply-path change first.

---

## Step 4 — Inline Math Evaluator (#1)

Independent; pure Kotlin, testable without a device.

| Change | File |
| --- | --- |
| Tokenizer + shunting-yard + evaluator + error type | new: `MathEval.kt` (~250 lines) |
| Unit tests (cases 1.1–1.11, 1.15) | new: `app/src/test/java/com/genz/keyboard/MathEvalTest.kt` |
| Offer a chip when composing text is an expression | `KeyboardService.kt` (suggestion assembly, ~1190–1240) |
| Suppress in password/incognito | same site — reuses `flags`/`effectiveIncognito` |

**Complexity:** medium; the parser is self-contained and fully unit-testable.
**Risk:** low — the feature only ever *adds a chip*.
**Rollback:** delete `MathEval.kt` and the one insertion site.

**Deliberately deferred:** unit conversion (needs a unit table and grammar extension). Shipping the
arithmetic parser alone keeps the surface small and correct.

---

## Step 5 — Entity classification for clipboard (#4)

Independent of #1; depends on nothing but the existing clipboard path.

| Change | File |
| --- | --- |
| `ClipEntity` model + `EntityKind` | new: `ClipEntity.kt` |
| Classifier: API 31+ platform, 33+ sensitive flag, regex fallback | new: `ClipClassifier.kt` |
| Replace `kb.clipOffer: String?` with `ClipEntity?` | `KeyboardState.kt:194` |
| Update the offer renderer and TTL/clear sites | `KeyboardService.kt:685, 1992`; panel renderer in `Panels.kt` |
| Bounded inspection (512 chars) and generation counter | `ClipClassifier.kt` |

**Complexity:** medium-high — touches the panel UI as well as the service.
**Risk:** medium. The current single-string offer is simple and works; the replacement must keep
the "tap to paste" behaviour identical for `OTHER` clips.

**Rollback:** keep `clipOffer` and add `ClipEntity` alongside, then switch the renderer back.

---

## Step 6 — Inline Autofill hardening (#23)

Already implemented; this step is small.

| Change | File |
| --- | --- |
| Clear request/response state in `onFinishInputView` and on field change | `KeyboardService.kt:667` |
| Guarantee API 26–29 fallback keeps the clipboard offer working | `KeyboardService.kt` (offer path) |
| Ensure failure never reserves layout space | `KeyboardService.kt:617` region |
| Instrumented availability test | `androidTest` from Step 0 |

**Complexity:** low. **Risk:** low.
**Rollback:** the feature already works today; revert to the current `runCatching` behaviour.

---

## Step 7 — Cross-feature verification

| Check | Method |
| --- | --- |
| Incognito beats every pathway | tests 13.10, 13.11, 1.14, 4.12 |
| No new permissions | diff the manifest |
| Both flavours compile | CI already builds `assembleStandardRelease` and `assembleFdroidRelease` |
| Proprietary dependency gate still green | existing CI gate |
| No sensitive data persisted | inspect prefs/files after the manual matrix |
| Typing latency | Macrobenchmark before/after |

---

## Risks, ranked

| # | Risk | Impact | Mitigation |
| --- | --- | --- | --- |
| 1 | A missed `settings.incognito` site leaks learning | **high** — privacy claim becomes false | Step 1 makes it one value; grep the string after the change and expect zero hits outside `PrivacyState` |
| 2 | Undo corrupts text in an unusual editor | high | abandon-on-mismatch; never replace without verifying the preceding text |
| 3 | Clipboard classification blocks or hangs | medium | off-main-thread, 512-char cap, linear regexes, generation counter |
| 4 | Math chip appears inside code editors | medium | require digit + operator; suppress in incognito/password; document as known |
| 5 | Inline autofill API misuse | medium | verified surface in `ANDROID_API_VERIFICATION.md` §5.2 — no `release()`, no platform `setInlineContentCallback` |
| 6 | No device to test on | **high** — all of it | Step 0 creates instrumentation; manual matrix required before release |

## Rollback strategy

Each step is independently revertable because each adds either (a) a new file plus one call site,
or (b) a behaviour-preserving substitution. Nothing requires a database or preference migration, so
no rollback can strand user data.

## Complexity summary

| Step | Complexity | Can run without a device |
| --- | --- | --- |
| 0 Safety net | medium | creates the capability |
| 1 Privacy source of truth | medium | unit tests yes, behaviour no |
| 2 Auto-incognito | low | unit tests yes |
| 3 Typo-undo | medium | unit tests yes |
| 4 Math | medium | **fully** |
| 5 Entity chips | medium-high | unit tests yes |
| 6 Autofill hardening | low | no |
| 7 Verification | — | no |

**Recommendation:** implement in the order 0 → 1 → 2 → 4 → 3 → 6 → 5. Step 4 (math) is fully
verifiable here and delivers user-visible value early; Step 5 is last because it is the largest UI
change for the least privacy benefit.
