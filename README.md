# GenZ Keyboard

A real Android keyboard (IME) built with Kotlin and Jetpack Compose, in the Material 3
Expressive design language. Fast, quiet and private: it types into any app, it does not
watch what you type, and it has no analytics, no ads and no tracking SDKs.

**Created by Umaiz Sufiyan.**

[![Android CI](https://github.com/sufiyan-sabeel/genZ-keyboard/actions/workflows/android.yml/badge.svg)](https://github.com/sufiyan-sabeel/genZ-keyboard/actions/workflows/android.yml)

---

## What it is

GenZ Keyboard is a fork of [Expressive Keyboard](https://github.com/kotlalipopus/Expressive-Keyboard)
by **kotlalipopus** (MIT), rebranded and hardened. The IME engine, the layout data, the
dictionaries and the theme system come from that project and remain its credit — see
[NOTICE.md](NOTICE.md). This fork contributes its own identity, icon, brand palette, a
security pass and its own build pipeline.

It is a genuine `InputMethodService`, not a mockup: it registers with the system, appears in
*Settings → System → Languages & input → On-screen keyboard*, and inserts text into any
application through a real `InputConnection`.

## Features

**Typing**

- 138 built-in layouts, including English variants, Hindi, Marathi, Bengali, Tamil, Telugu,
  Urdu, Arabic, Persian, Hebrew, Greek, Cyrillic, CJK (pinyin and bopomofo), Japanese, Korean
  (Hangul composer), Thai, Vietnamese (Telex) and many more
- Word prediction, autocorrection and next-word suggestions backed by frequency dictionaries
  and a bigram model, plus a personal dictionary that learns your words
- Glide (swipe) typing
- Shift and caps lock, long-press for accented characters and symbols, number row optional
- One-handed mode, adjustable keyboard height, key size, spacing and corner radius
- Emoji keyboard with search across four icon styles (Twemoji, Noto, Blobmoji, OpenMoji)
- Clipboard history with a user-controlled retention window
- Voice input through the system speech recogniser
- Haptic feedback and key sounds, both tunable, both off or on by choice
- Text shortcuts: type a short trigger, get a longer phrase
- Emoji suggestions: type "lol", "vibes" or "fire" and the matching emoji is offered as a
  suggestion chip you can tap (a small curated list, so the bar stays quiet)

**Look and feel**

- Nine ready-made styles — Material 3 Expressive, Material You, Liquid Glass, Classic, Neon,
  Neumorphism, Minecraft, Samsung One UI and Nothing — each fully customisable
- Custom themes: background, key colours, accent colour, key shape, shadows and font
- AMOLED-friendly dark theme and a light theme
- Material You wallpaper colours, or the GenZ brand violet by default
- Onboarding flow, a keyboard preview and a searchable settings screen
- A layout editor for rearranging keys, and a JavaScript plugin engine (Rhino) for custom
  panels, keys and events

**Privacy**

- No typed text leaves the device. There is no keystroke logging, no remote logging and no
  analytics of any kind
- No ads and no tracking SDKs
- Clipboard contents are never uploaded
- All settings stay in local `SharedPreferences`
- Screenshot capture into the clipboard history is **off by default** and requires an explicit
  permission grant

See [PRIVACY.md](PRIVACY.md) for the full policy and the exact list of permissions.

## Screenshots

No screenshots are committed yet. Rather than publish mockups, the repository only ships
images taken from a real device build. To add yours:

1. Install the debug APK (see below) and enable the keyboard.
2. Take screenshots of the keyboard, the theme picker and the settings home.
3. Put them in `docs/screenshots/` and reference them here.

## Install

### From a build (recommended for now)

1. Download `app-debug.apk` from the latest successful **Android CI** run (Artifacts →
   `genz-keyboard-debug-apk`), or build it yourself.
2. Open the APK on the device and allow installation from your browser/file manager.
3. Open **GenZ Keyboard** from the app drawer and follow onboarding.
4. Enable it: **Settings → System → Languages & input → On-screen keyboard → Manage
   keyboards → GenZ Keyboard**.
5. Select it: tap any text field, then the keyboard switcher in the navigation bar.

## Build

Requirements: JDK 17, Android SDK with platform 35 and build-tools 35, Gradle wrapper
(included).

```bash
git clone https://github.com/sufiyan-sabeel/genZ-keyboard.git
cd genZ-keyboard
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests, no device needed
./gradlew assembleRelease        # minified release build
```

The release build is signed with the debug key unless you provide your own keystore through
`local.properties` (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`). `local.properties` is git-ignored, and no keystore is committed to
this repository — a public repository must never contain a signing key.

Optional: GIF search and the optional AI text-fix feature read their keys from
`local.properties` as `KLIPY_API_KEY` / `GIPHY_API_KEY`. Both features are off unless a key is
present; the app is fully usable without either.

## Architecture

Kotlin, Jetpack Compose, Material 3 (`material3` 1.5 expressive APIs), single `:app` module,
`minSdk` 26, `targetSdk` 35.

```
app/src/main/java/com/genz/keyboard/
├── KeyboardService.kt      InputMethodService: the IME lifecycle, key handling, commit path
├── KeyboardState.kt        Runtime state shared by the keyboard UI
├── KeyboardUi.kt           Compose tree of the keyboard surface
├── KeyView.kt / KeyPalette.kt / KeyboardSurfaces.kt   Key rendering and press feedback
├── Languages.kt            The layout table (138 languages) and per-language composers
├── Dictionaries.kt         Word lists, rare words, bigram model, personal dictionary
├── WordPredictor.kt        Prediction, autocorrect, suggestion ranking
├── GlideTyping.kt          Swipe typing
├── Emoji.kt / Panels.kt    Emoji, clipboard, GIF and tool panels
├── EmojiSuggest.kt         Keyword → emoji suggestions in the suggestion bar
├── Theme.kt / ThemeUi.kt / Skins.kt / ThemeOptions.kt / Design.kt   Colours and styles
├── ExpressiveShapes.kt     Material 3 expressive shape presets
├── Settings*.kt            Settings screens, search index, backup and restore
├── LayoutEditor.kt         Custom key placement
├── Plugins.kt / ScriptEngine.kt   JavaScript plugin engine
├── Clipboard.kt            Clipboard history with local-only storage
├── HapticsEngine.kt / KeySounds.kt   Feedback
├── Onboarding.kt           First-run flow
└── MainActivity.kt         Launcher activity and settings host
```

Design notes:

- The keyboard UI is Compose inside a `ComposeView` owned by the `InputMethodService`; text is
  committed through `InputConnection`, so IME features (composing regions, autocorrect
  spans, password fields) behave natively.
- Settings are a single immutable `KeyboardSettings` data class read from
  `SharedPreferences`, so every screen re-composes from one source of truth.
- Themes are pure data (seed colour → tonal palette), which is why a style can be applied
  without touching layout code.

## Testing

```bash
./gradlew testDebugUnitTest
```

JVM unit tests cover the correction pipeline and glide-swipe scoring; they need no device.
Instrumentation tests and on-device behaviour (haptics, IME connection edge cases) are not
covered by automated tests in this repository — those need a device or emulator.

CI runs the unit tests and builds the debug APK on every push to `main`
(`.github/workflows/android.yml`) and uploads the APK as an artifact.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Bug reports with a device model, Android version and
reproduction steps are welcome; please do not attach logs containing anything you typed.

## License and attribution

MIT — see [LICENSE](LICENSE). Upstream attribution and the list of bundled third-party assets
are in [NOTICE.md](NOTICE.md).

GenZ Keyboard is a fork of Expressive Keyboard © 2026 kotlalipopus, used under the MIT License.
This fork © 2026 Umaiz Sufiyan.
