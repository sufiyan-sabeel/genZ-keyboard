# GenZ Keyboard 1.1.0 — Roadmap

Design is in `GENZ_KEYBOARD_1_1_0_LLD.md`; the ship gate is in
`GENZ_KEYBOARD_1_1_0_RELEASE_CHECKLIST.md`. This document sequences the work and states what
"done" means for each feature.

**Nothing in 1.1.0 is implemented.** This is a plan.

---

## 1. Version strategy

Current release is **1.0.5** (versionCode 10). The next release must use a higher code; codes are
never reused — 1.0.3 shipped as 7 and was rebuilt as 8, 1.0.4 is 9, 1.0.5 is 10.

| Release | versionCode | Content |
| --- | --- | --- |
| 1.1.0 | 11 | The seven features below, whatever is complete |
| 1.1.x | 12+ | Fixes only; no new features |

**Feature flags are not part of the plan.** A feature ships complete or does not ship; half-built
panels are worse than absent ones. Anything not finished by the code freeze moves to 1.2.0.

Changelog: `fastlane/metadata/android/en-US/changelogs/11.txt`, user-facing, no internal jargon.

---

## 2. Dependency map

```
Privacy layer (shipped) ──┬──> every feature (write gating, event gating)
                          │
Emoji catalogue ──────────┼──> Emoji favourites/combos
                          └──> (no other feature depends on it)

GifCatalog ───────────────┬──> GIF favourites
                          └──> Sticker packs (needs SAF import + attribution)

FontRepository ───────────┼──> Font catalogue
                          └──> Decorative text (needs a font that covers the glyphs)

Dictionaries ─────────────┴──> Slang packs
                               Quick phrases ──┐
Plugins.shortcuts ─────────────────────────────┼──> PhraseTrigger precedence
WordPredictorEngine ───────────────────────────┘    (must ship WITH slang, not after)

Skins/ThemeUi ────────────> Theme editor ──> Theme import/export (shares validation)

(nothing) ────────────────> Quote pack  (independent; can run in parallel)

InlineAutofill (shipped) ─> Autofill hardening only. No Credential Manager work.
```

**The one hard ordering constraint:** `PhraseTrigger` precedence must land in the same change as
slang packs. Adding slang without a defined precedence against autocorrect and shortcuts will
silently mangle words, and precedence is far harder to retrofit once users have phrase data.

---

## 3. Implementation sequence

### Phase 0 — Foundations (must precede any feature)
1. Extend `PRIVACY.md` with the per-feature storage table from the LLD §9.1, so the policy is
   written before the code.
2. Add the shared JSON pack reader (schema version, size cap, unknown-field tolerance) used by
   slang, quotes, themes and sticker packs. One reader, four callers — do not write four parsers.
3. Add the contrast assertion test over bundled palettes.

**Exit:** the three items above merged, 0 test failures, both flavours build.

### Phase 1 — Independent, low-risk (parallelisable)
- **Quotes** (greenfield, no dependencies, small surface).
- **Emoji favourites and combos** (extends an existing panel).
- **Font catalogue** (extends an existing repository; licence work is the long pole).

### Phase 2 — Content features needing policy work
- **Slang packs + quick phrases + `PhraseTrigger` precedence** — one change, as above.
- **Decorative text** — depends on the font catalogue; opt-in per insert only.

### Phase 3 — Media features
- **GIF favourites** (small), then **sticker packs** (SAF import, attribution, size caps).

### Phase 4 — Themes
- **Theme editor**, then **import/export**. Import validation is the risk surface; ship the editor
  first so themes exist to import.

### Phase 5 — Autofill hardening
- The three small items in LLD §8.4. No Credential Manager integration at any point.

---

## 4. Risks

| # | Risk | Impact | Mitigation |
| --- | --- | --- | --- |
| 1 | Precedence between autocorrect, shortcuts, slang and phrases mangles words | **high** — silent text corruption | pure `PhraseTrigger.resolve`, exhaustive pair matrix, ship together |
| 2 | A bundled font or quote pack ships without a licence | **high** — legal exposure | licence metadata mandatory per entry, audited in the release checklist |
| 3 | Shared theme JSON becomes a network fetch vector | medium | reject remote URLs at import; local-only asset resolution |
| 4 | APK growth from fonts, stickers and quote packs | medium | 4 MB budget, size caps per pack |
| 5 | New persistent stores bypass the privacy layer | medium | every write through `PrivacyWriteRouter`; a code review checklist item |
| 6 | Sticker/GIF provider terms change | medium | attribution module + a terms review step in the checklist |
| 7 | Scope creep into credential handling | **high** — security burden | LLD §8.5 lists what must never be built |
| 8 | No device in the current environment | **high** — nothing runtime-verified | device checklist before release; no "production ready" claim without it |

---

## 5. Acceptance criteria per feature

### 1. Themes
- Editor changes apply live without restarting the keyboard.
- Imported theme validated; a malformed file is rejected with a message, never a crash.
- A theme referencing missing artwork renders its palette (both flavours).
- Bundled palettes pass the 4.5:1 contrast test.

### 2. Emoji
- Favourites persist across restarts; order is stable.
- A combination inserts as one unit and can be undone with one backspace.
- Recents remain suppressed in incognito; favourites persist (documented).

### 3. GIF
- Per-provider toggle disables that provider with no request made.
- Saved GIFs survive restart and render from the stored URL.
- Incognito: built-in catalogue only, zero network requests (proxy-verified).
- Sticker packs import from SAF and are size-capped.

### 4. Fonts
- Every catalogue entry has licence metadata and a licence file in `licenses/`.
- Switching fonts invalidates the cache correctly.
- Decorative transforms never apply in a password field or incognito.

### 5. Slang and phrases
- Precedence matches the documented order for every pair, unit-tested.
- A 5,000-entry pack does not measurably change typing latency.
- Phrase usage is not recorded in incognito.

### 6. Quotes
- Every bundled quote carries provenance.
- Favourites persist; search does not record history.
- Insertion is a single undoable action.

### 7. Autofill
- Behaviour unchanged from 1.0.5 except the documented hardening.
- With no autofill provider: no chip and no reserved space.
- In incognito or a password field: no request is constructed.
- **No credential storage, logging or transmission exists** — verifiable by inspection.

---

## 6. Code freeze and ship gate

1. Feature freeze; only fixes after that point.
2. Full JVM suite green; both flavours build; instrumentation compiles.
3. The device checklist executed on at least one API 26–29 and one API 30+ device, results recorded.
4. Licence and dependency audit complete.
5. Release checklist in `GENZ_KEYBOARD_1_1_0_RELEASE_CHECKLIST.md` fully ticked.

**A release is not "production ready" because the documents are complete.** It is ready when the
checklist is ticked with evidence.
