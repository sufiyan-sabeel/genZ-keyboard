# Android API Verification — v1.1 Phase 1

Every API claim used by the Phase 1 design is listed here with how it was verified. Claims that
could **not** be verified are marked, not smoothed over.

Legend: **[doc]** fetched from the official Android reference during this audit ·
**[src]** read from the compiling repository · **[unverified]** not confirmed.

---

## 1. `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING`

- **Verified [doc]** — `android.view.inputmethod.EditorInfo`, constant value `16777216`
  (`0x01000000`), **added in API level 26**.
- Documented purpose: asks the IME not to learn from what is typed — no personalised
  suggestions, no new words added to the dictionary, no stored input history.

**The caveat that shapes feature #13.** The reference states the flag *"is not a guarantee, and
some IMEs may not respect it"*. It is a request from the editor to the IME, honoured only because
this IME chooses to honour it. Two consequences for the design:

1. Reading the flag is a **useful signal**, and honouring it is correct behaviour for *our* IME.
2. It must never be described to users as a guarantee about the host app or the system. Copy must
   say what the keyboard itself does, nothing more.

Min SDK is 26, so this flag is available on every supported version — no version guard needed.

## 2. `EditorInfo.inputType` / `TYPE_MASK_CLASS` / `TYPE_MASK_VARIATION`

- **Verified [src]** — already used in `KeyboardService.kt:847-877` (`computeFlags`), covering
  `TYPE_CLASS_TEXT`, `TYPE_CLASS_NUMBER`, the three text password variations, the numeric
  password variation, the two email variations and `TYPE_TEXT_VARIATION_URI`.
- **Verified [doc]** — the masks and variations are documented members of `InputType` /
  `EditorInfo`; no API-level guard needed (all predate API 26).

Reusable as-is. #13 needs no new detection code for password/address fields; it needs to *act*
on what `computeFlags` already computes.

## 3. `EditorInfo.packageName` as a trust boundary

- **Verified [doc]** — the reference warns that the package name should not be trusted on API 22
  and earlier, and that from Android M the system verifies the package actually has focus.
- **Design consequence:** a package allowlist for auto-incognito is a *convenience*, not a
  security control. An app can present an innocuous package name while showing a sensitive field.
  The design therefore treats package identity as one weak signal among several, and never as the
  sole reason to leave learning enabled.

## 4. `ClipDescription.EXTRA_IS_SENSITIVE` and clipboard classification

- **Verified [doc]** — `ClipDescription.EXTRA_IS_SENSITIVE` is **added in API level 33**.
- **The critical documented limitation:** adding this extra *"does not change clipboard behaviour
  or add additional security to the ClipData. Its purpose is essentially a rendering hint from the
  source application, asking that the data within be obfuscated or redacted."*
- **Design consequence for #4:** the flag can suppress a preview, and should. It cannot be used
  to decide that content is safe — its absence proves nothing.
- **Also verified [doc]:** `ClipDescription.getClassificationStatus()` and
  `getConfidenceScore(TextClassifier.EntityType)` (**API 31**) expose the platform text
  classifier's entity types, including `TYPE_OTP`, `TYPE_URL`, `TYPE_EMAIL`, `TYPE_PHONE`,
  `TYPE_ADDRESS`, `TYPE_DATE_TIME`. Reading a score before classification completes raises
  `IllegalStateException`, so the status must be checked first.
- **Design consequence:** platform classification is preferable to regex for URL/email/phone/OTP,
  with regex only as the API 26–30 fallback. Both are advisory; the user still confirms the action.

## 5. Inline autofill — the platform and AndroidX surface

### 5.1 `InputMethodService.onCreateInlineSuggestionsRequest`

- **Verified [src]** — `KeyboardService.kt:617`, annotated `@RequiresApi(Build.VERSION_CODES.R)`,
  signature `override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest?`.
  It compiles against `compileSdk 35`, so the method exists with that signature.
- **Partially verified [doc]** — the reference pages for `InputMethodService` returned **404** at
  fetch time in both the Java and Kotlin URL forms. The API-level claim (30 / `R`) therefore rests
  on the source annotation rather than on a fetched page. Treat as **API 30, high confidence, not
  doc-confirmed in this audit.**

### 5.2 `InlineContentView` — the invented-API trap

The brief warns: *"Do not invent cleanup methods or lifecycle callbacks. Verify actual API
signatures."* The official reference for `android.widget.inline.InlineContentView` was fetched and
its complete public surface is:

| Member | Kind |
| --- | --- |
| `getSurfaceControl()` | public method |
| `isZOrderedOnTop()` | public method |
| `setZOrderedOnTop(boolean)` | public method |
| `setClipBounds(Rect)` | public method |
| `setSurfaceControlCallback(SurfaceControlViewHost.SurfaceControlCallback)` | public method |
| `onLayout(...)` | public method |
| `onAttachedToWindow()` / `onDetachedFromWindow()` | **protected** — overridable |

**Findings that contradict common assumptions:**

- There is **no `release()` and no `destroy()`** on `InlineContentView`. Any design that calls one
  is wrong. Cleanup happens by dropping the view and letting it detach; `onDetachedFromWindow` is
  the only legitimate hook, and it is protected.
- There is **no `setInlineContentCallback`** on the *platform* view. `InlineContentCallback` is an
  AndroidX concept that belongs to `androidx.autofill.inline`, not to this class. Mixing the two
  up is the most likely source of a non-compiling design.

### 5.3 AndroidX inline UI

- **Verified [src]** — `androidx.autofill.inline.v1.InlineSuggestionUi.newStyleBuilder()`,
  `setSingleIconChipStyle`, `setChipStyle`, `setTitleStyle`, `setSubtitleStyle`,
  `UiVersions.newStylesBuilder()`, `InlinePresentationSpec.Builder(width, height)` are all in use
  in `KeyboardService.kt:617-640` and compile against `androidx.autofill:autofill:1.1.0`.
- These are the APIs the design must use; the platform `InlineContentView` is what a *host* would
  render, not what the IME constructs.

## 6. Clipboard listening

- **Verified [src]** — `ClipboardManager.OnPrimaryClipChangedListener` implemented at
  `KeyboardService.kt:275`, registered at 467.
- **Verified [doc]** — on Android 10 (API 29) and later, clipboard read access is restricted to
  the app with input focus or the default IME; background apps cannot read the clipboard. Since
  this service is an IME reading while its input view is shown, reads are permitted — but the
  design must still tolerate `getPrimaryClip()` returning `null`.
- **Design consequence:** the listener can fire while the keyboard is not visible. Any
  classification work must be gated on "input view shown", and must not assume the clip is
  readable.

## 7. Composing-text editing

- **Verified [src]** — `setComposingText` (`KeyboardService.kt:1081`), `finishComposingText`
  (1017, 1668, 1855, 1927, 1977, 2081), `getTextBeforeCursor` (938).
- **Verified [doc]** — `InputConnection` methods can return `false` or `null`; the editor may
  ignore a request, and the connection can be invalidated between calls. `getTextBeforeCursor`
  returns `CharSequence?`.
- **Design consequence for #3:** an undo that replaces text must verify the expected text is still
  there immediately before replacing it, and must abandon silently if the editor disagrees. No
  destructive replacement on an uncertain editor state.

## 8. Claims deliberately **not** made

| Claim | Why not |
| --- | --- |
| "Incognito guarantees nothing is retained by the host app or system" | The keyboard cannot control the host, the OS, or any other IME. Only the keyboard's own behaviour can be promised. |
| "`EXTRA_IS_SENSITIVE` marks content as secret" | It is a rendering hint, per the docs; its absence means nothing. |
| "`InlineContentView.release()` frees the surface" | No such method exists on that class. |
| "Package name proves the field is safe" | Only verified from Android M, and an app controls its own fields regardless. |
| "Platform classification is always available" | API 31+, and it is asynchronous with an explicit status to check first. |

## 9. References

- `android.view.inputmethod.EditorInfo` — `IME_FLAG_NO_PERSONALIZED_LEARNING`, `packageName`
  (fetched during this audit).
- `android.content.ClipDescription` — `EXTRA_IS_SENSITIVE`, `getClassificationStatus`,
  `getConfidenceScore` (fetched during this audit).
- `android.widget.inline.InlineContentView` — full public/protected member list (fetched during
  this audit).
- `android.view.inputmethod.InputMethodService` — **fetch returned 404**; claim carried by source
  annotation only.
- Repository sources cited by path and line in `REPOSITORY_AUDIT.md`.
