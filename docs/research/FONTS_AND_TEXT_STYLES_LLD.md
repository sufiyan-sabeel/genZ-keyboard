# Fonts and Text Styles — LLD

**Status:** design only. Nothing implemented.

## 1. What exists

`FontRepository.kt` resolves the active font family: `CUSTOM` (a user-installed `.ttf` in app
storage, version-keyed cache) or the bundled **Pixelify Sans** (`res/font/pixelify.ttf`, SIL OFL
1.1, licence text in `licenses/SIL-OFL-1.1.txt`). `SettingsAppearance.kt` exposes the choice.

So: one bundled font, one custom slot, working cache invalidation.

## 2. The platform limitation — state it, do not work around it

**A keyboard cannot change the font used by the receiving application.** What is typed into
WhatsApp or Instagram is rendered by that app with its own typography. The brief already says this;
the design must not quietly imply otherwise in UI copy.

What fonts *can* do:
1. Change the **keyboard's own** labels, suggestion strip and panel text.
2. Change what is **committed** only via Unicode transformations (a separate feature, below).

UI copy must say "Keyboard font", never "Changes your app's font".

## 3. Font catalogue

```kotlin
data class FontEntry(
    val id: String,
    val label: String,          // "Rounded", "Geometric" …
    val category: FontCategory, // SANS, ROUNDED, MINIMAL, GEOMETRIC, HANDWRITTEN,
                                // MONOSPACE, SERIF, DISPLAY, PIXEL, ELEGANT
    val asset: String,          // res/font/<name>.ttf
    val licence: String,        // "OFL-1.1" | "Apache-2.0" | "PD"
    val licenceFile: String,    // licenses/<file>.txt
    val sizeBytes: Int
)
```

Ten categories are requested; a curated set of ~8–10 families covers them without bloating the APK.
**Budget: 2 MB total for all bundled fonts.** Pixelify Sans already occupies part of it.

### Licensing gate (hard)
Every font must be OFL, Apache-2.0 or public domain, with its licence text committed to `licenses/`
and an entry in `NOTICE.md` **in the same commit**. The Pixelify Sans mislabelling earlier in this
project — the app credited "Nunito" for a year — is the cautionary example: attribution is a licence
condition, not a nicety.

Fonts that require attribution in the UI (some OFL families with Reserved Font Names) must surface
that in the font picker.

## 4. Text styles (Unicode transformations)

```kotlin
enum class TextStyleKind { BOLD, ITALIC, MONOSPACE, CIRCLED, SMALL_CAPS, SEPARATOR, AESTHETIC }

object DecorativeText {
    fun apply(kind: TextStyleKind, input: String): String
    fun supports(kind: TextStyleKind, input: String): Boolean
}
```

### Rules
- **Never automatic.** The user previews and chooses; nothing is transformed on the typing path.
- **Never in a password field**, and never while `effectiveIncognito` is active — a transform changes
  what is committed, so it must follow the same policy as suggestions. Route through
  `PrivacyState`/`PluginEventPolicy`, not a private check.
- **Preserve emoji and unsupported characters verbatim.** Mathematical alphanumerics exist only for
  Latin letters and digits; a naive map corrupts Devanagari, CJK and emoji. The transform is
  character-by-character with a passthrough fallback, unit-tested against mixed-script input.
- **Keep the original** visible in the preview so the user can compare.
- **Do not promise universal support.** Some apps, some fonts and some screen readers render
  mathematical alphanumerics poorly; a screen reader may read them as "mathematical bold capital A".
  That is an accessibility regression, which is why this stays opt-in and never applies to ordinary
  typing.

### Why not text expansion at commit time
A transform applied automatically would break autocorrect, undo (`UndoGuard` compares committed
text), and the suggestion strip contract. Opt-in insertion is the only design that does not
destabilise those three.

## 5. UI
Font picker with live preview per category, sample text, default reset, and a "not available"
state when a custom font file is missing. Text styles live in their own panel with preview → copy →
insert actions, consistent with the existing panel structure in `Panels.kt`.

## 6. Storage
Active font id: existing prefs (already there). Custom font file: existing `filesDir` slot. No new
store.

## 7. Tests
Unit: transform idempotence, mixed-script passthrough (Latin + Devanagari + CJK + emoji),
`supports()` correctness, licence metadata present for every `FontEntry`, licence file exists on
disk. Device: rendering at 12–24 sp per bundled font, preview parity with the keyboard.

## 8. Acceptance criteria
1. Every bundled font has a licence file and a `NOTICE.md` entry in the same commit.
2. APK growth from fonts ≤ 2 MB.
3. No UI text claims the keyboard changes other apps' fonts.
4. Transforms are opt-in, previewed, never applied in a password field or incognito.
5. Mixed-script input passes through unchanged except for supported characters.
6. Default font restoration works after removing a custom font.
