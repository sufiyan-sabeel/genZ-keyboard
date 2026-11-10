# GenZ Keyboard

A real Android keyboard (IME) built with Kotlin and Jetpack Compose, in the Material 3
Expressive design language. Fast, quiet and private: it types into any app, it does not
watch what you type, and it has no analytics, no ads and no tracking SDKs.

**Created by Umaiz Sufiyan** · [@sufiyan-sabeel](https://github.com/sufiyan-sabeel)

[![Android CI](https://github.com/sufiyan-sabeel/genZ-keyboard/actions/workflows/android.yml/badge.svg)](https://github.com/sufiyan-sabeel/genZ-keyboard/actions/workflows/android.yml)

> **F-Droid: not yet available on the official F-Droid repository.** The app is being prepared
> for inclusion — it now builds entirely from free software and has a metadata draft in
> [`fdroid/metadata/`](fdroid/metadata/com.genz.keyboard.yml). Until a merge request is accepted
> and the build is published by F-Droid, install from GitHub Releases.

---

## F-Droid

GenZ Keyboard is being prepared for official F-Droid inclusion. What that took, and what is
still outstanding:

| Requirement | Status |
| --- | --- |
| Free-software licence | MIT |
| Builds from public source | yes, CI builds both flavours on every push |
| No proprietary dependencies | yes — Google ML Kit was removed; see below |
| No developer keys needed to build | yes, API keys are optional and empty by default |
| Reproducible build inputs | Gradle wrapper, JDK 17, SDK 35, no downloaded build assets |
| F-Droid store metadata | `fastlane/metadata/android/en-US/` |
| Build recipe | [`fdroid/metadata/com.genz.keyboard.yml`](fdroid/metadata/com.genz.keyboard.yml) |
| Submitted for review | **no** — the merge request still has to be opened |

**Two distributions.** The Gradle project defines two product flavours from one codebase:

- `standard` — what GitHub Releases ship. Includes the ten anime artworks.
- `fdroid` — identical keyboard, but **without** those artworks, because their redistribution
  rights are unresolved. The ten themes still ship; with no bundled picture each one falls back
  to the palette that was sampled from that artwork, so it stays a distinct colour theme rather
  than a broken one.

F-Droid builds the `fdroid` flavour (`gradle: - fdroid` in the metadata) and signs the APK
itself. A developer-signed APK is never substituted for an F-Droid source build.

**Removed for F-Droid:** `com.google.mlkit:genai-proofreading` (on-device Gemini Nano
proofreading). It is a proprietary Google binary that cannot be shipped in a free-software
distribution, and an optional feature does not make its dependency acceptable. The remaining AI
options — Gemini, Claude, OpenAI, Groq, OpenRouter, LanguageTool — are ordinary network clients
using the user's own key, and the keyboard is fully usable with none of them.

---

## What it is

GenZ Keyboard is a complete, production-shaped Android keyboard: an `InputMethodService`, not a
mockup. It registers with the system, appears in *Settings → System → Languages & input →
On-screen keyboard*, and inserts text into any application through a real `InputConnection`.

It is built on an MIT-licensed open-source keyboard engine, which this project carries forward
under its own name, identity, icon, palette, security pass and build pipeline. The licence and
the third-party notices this codebase is legally required to keep are in [LICENSE](LICENSE) and
[NOTICE.md](NOTICE.md).

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
- Ten anime themes in their own gallery section (see below)
- English interface only, with a System option that follows the device
- One-tap dark mode switch on the settings home, plus the auto / light / dark / AMOLED picker
- Onboarding flow, a keyboard preview and a searchable settings screen
- A layout editor for rearranging keys, and a JavaScript plugin engine (Rhino) for custom
  panels, keys and events

## Anime Theme System

Ten anime themes ship with the app, each built around its own illustration. Open
*Settings → Themes → Anime themes*. Every card shows the artwork, with Preview and Apply
buttons that work.

| Theme | Artwork | Palette |
| --- | --- | --- |
| Ink Demon | ink demon on warm washi paper | paper keys, ink-brown accent |
| Sage of the Leaf | white-haired sage in leaf shadow | bone keys, ember orange |
| Crimson Split | split face over alarm red | dark keys, hard crimson |
| Never Give Up | grey manga page, heavy ink | paper keys, black accent |
| Violet Storm | violet lightning in the dark | near-black keys, purple glow |
| Flame Dance | blade and fire on cream | pale keys, flame gold |
| Ego | newsprint collage | paper keys, slate accent |
| Two Halves | warm orange against cool teal | dark keys, orange accent |
| Straw Hat Sky | straw hat against summer sky | bright keys, brick red |
| Red Rose | rain, crows, one red rose | monochrome keys, one red accent |

Each theme is a **skin definition plus a bundled image**, not a screenshot: the JSON in
`app/src/main/assets/plugins/anime_*.json` sets key and function-key fills, text and pressed
colours, spacebar and Enter styling, popup, suggestion chips, toolbar buttons, panel, corner
radius and press animation, and names its picture in `backgroundImage`. The artwork in
`app/src/main/assets/anime/` is drawn behind the real keyboard through the same path that
handles a user-picked background, so cover-cropping, dimming, blur and transparency all apply.

The palettes are not hand-picked: each theme's accent and key colours are sampled from its own
artwork (dominant vivid hue, falling back to the picture's ink when it is monochrome), and every
pairing was checked for WCAG contrast — body text sits at 13:1 or better, accent-on-accent at
4.7:1 or better.

**Your own background.** *Settings → Colors → Background image* uses the Android system photo
picker — one image, granted to this app only, no media-library permission — then offers dimming,
blur and transparency sliders and a reset. The image is copied into app-private storage; it is
never uploaded, and no network call is involved.

**Persistence and performance.** The selected theme is stored as a skin id in the existing
preferences, so it survives app, keyboard and device restarts. Artwork is decoded once per theme
on a background thread, downsampled to 2048 px and cached, never on the typing path.

### Artwork rights

The ten anime illustrations are supplied by the project owner and are **not** covered by the MIT
licence in `LICENSE` — that licence covers the code. They depict characters and styles owned by
third parties, and two carry other artists' signatures. Anyone redistributing this app publicly
should confirm they hold the rights to those images, or replace them. The code works with any
image: drop a replacement into `app/src/main/assets/anime/` under the same filename.

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

### From a release (easiest)

1. Open [Releases](https://github.com/sufiyan-sabeel/genZ-keyboard/releases) and download the
   latest `GenZKeyboard-vX.Y.Z.apk`.
2. Open the APK on the device and allow installation from your browser/file manager.
3. Open **GenZ Keyboard** from the app drawer and follow onboarding.
4. Enable it: **Settings → System → Languages & input → On-screen keyboard → Manage
   keyboards → GenZ Keyboard**.
5. Select it: tap any text field, then the keyboard switcher in the navigation bar.

> **Signing caveat.** Until release signing secrets are configured (see *Build* below), the
> release APK is signed with the CI runner's debug key. CI generates that key fresh on every
> run, so a *later* release cannot update over an earlier install — you would have to uninstall
> first. For a genuinely updatable app, set the four signing secrets once.

### From F-Droid

Not available yet. When the listing is published it will be at
`https://f-droid.org/packages/com.genz.keyboard/`. This section will link to it only after the
F-Droid build is actually published — not when a merge request merely exists.

### From a debug build

Download `app-debug.apk` from the latest successful **Android CI** run (Artifacts →
`genz-keyboard-debug-apk`), or build it yourself.

## Publishing a release

`.github/workflows/android.yml` has a `release` job that builds the minified release APK and
publishes it as a GitHub Release. It only runs when triggered by hand:

1. **Actions → Android CI → Run workflow**
2. Set `publish_release` to `true` and `version` to the tag you want (e.g. `v1.0.1`).
3. Run it. The job creates the tag, builds `assembleRelease`, and attaches the APK to the
   release (or replaces the asset if that tag already exists).

To sign releases with your own key instead of the runner's debug key, add these four
repository secrets (**Settings → Secrets and variables → Actions**):

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_B64` | your keystore, base64-encoded (`base64 -w0 release.keystore`) |
| `RELEASE_STORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias |
| `RELEASE_KEY_PASSWORD` | key password |

Create the keystore once, locally, and keep it safe — losing it means you can never update
installed copies again:

```bash
keytool -genkeypair -v -keystore release.keystore -alias genz \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass 'YOUR_STORE_PASSWORD' -keypass 'YOUR_KEY_PASSWORD' \
  -dname "CN=GenZ Keyboard, O=Umaiz Sufiyan, C=IN"
base64 -w0 release.keystore   # paste into the RELEASE_KEYSTORE_B64 secret
```

Never commit the keystore or the passwords; `.gitignore` already excludes `*.keystore`,
`*.jks` and `local.properties`.

## Build

Requirements: JDK 17, Android SDK with platform 35 and build-tools 35, Gradle wrapper
(included). Nothing else is downloaded at build time and no API keys are needed.

On a Linux box or in Termux:

```bash
pkg install openjdk-17 gradle git          # Termux
export ANDROID_HOME=$HOME/android-sdk
# platform-tools, platforms;android-35 and build-tools;35.0.0 via sdkmanager
./gradlew assembleStandardDebug
```

```bash
git clone https://github.com/sufiyan-sabeel/genZ-keyboard.git
cd genZ-keyboard
./gradlew assembleStandardDebug        # app/build/outputs/apk/standard/debug/
./gradlew assembleStandardRelease      # minified release build with the anime artwork
./gradlew assembleFdroidRelease        # the F-Droid flavour, without that artwork
./gradlew testStandardDebugUnitTest    # JVM unit tests, no device needed
```

The release build is signed with the debug key unless you provide your own keystore through
`local.properties` (or the CI secrets described above) (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
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

## Privacy

The full policy is in [PRIVACY.md](PRIVACY.md). In short:

- **Input.** Keystrokes are handled in the keyboard's own process and committed to the target
  app through `InputConnection`. Nothing is logged, stored or transmitted. Password fields run
  in a reduced mode with no suggestions and no learning.
- **Local storage.** Settings, the personal dictionary and clipboard history live in app-private
  storage. `android:allowBackup="false"`, so none of it reaches Google's backup service.
- **Network.** `INTERNET` is used in four user-triggered cases only: downloading a language
  dictionary, GIF search (your own Klipy/Giphy key), AI text fixing (the provider you configure),
  and assets a plugin fetches. There is no telemetry, no update check and no heartbeat.
- **Permissions.** `VIBRATE`; `INTERNET`; `RECORD_AUDIO` only when you tap the microphone;
  and the media permissions only if you switch on "screenshots in clipboard", which is off by
  default and uses the system photo picker rather than broad library access.
- **Optional services.** Every network feature is off unless you configure it. The app is fully
  usable as a keyboard with no account, no key and no network.

## Open-source licences

| Component | Licence |
| --- | --- |
| GenZ Keyboard code | MIT ([LICENSE](LICENSE)) |
| Upstream keyboard engine | MIT, notice retained in [LICENSE](LICENSE) |
| Jetpack Compose, AndroidX, Material 3 | Apache 2.0 |
| Coil, OkHttp, Jackson (via the Anthropic SDK) | Apache 2.0 |
| Mozilla Rhino | MPL 2.0 |
| Anthropic Java SDK | Apache 2.0 |
| Twemoji | CC-BY 4.0 |
| Noto Emoji, Blobmoji | Apache 2.0 |
| OpenMoji | CC BY-SA 4.0 |
| Unicode CLDR emoji names | Unicode License |
| Word frequencies (FrequencyWords) | MIT |
| Russian word pairs (Google Books Ngram) | CC BY 3.0 |
| Pixelify Sans font | SIL OFL 1.1 |
| Anime theme artwork | **not** MIT — see below |

The anime artwork is supplied by the project owner and is not covered by the code licence; it is
excluded from the F-Droid flavour for that reason. Details in [NOTICE.md](NOTICE.md).

## F-Droid maintenance

Notes for whoever maintains the F-Droid listing:

| | |
| --- | --- |
| Application ID | `com.genz.keyboard` |
| Source | https://github.com/sufiyan-sabeel/genZ-keyboard |
| Issue tracker | https://github.com/sufiyan-sabeel/genZ-keyboard/issues |
| Licence | MIT |
| Release tags | `vMAJOR.MINOR.PATCH`, e.g. `v1.0.3` |
| Versioning | `versionName` follows the tag; `versionCode` increments on **every** build, including rebuilds of the same version |
| Build flavour | `fdroid` (`gradle: - fdroid`), no proprietary dependencies, no bundled third-party artwork |
| Update check | `Tags` |
| Auto-update | `Version` |

`versionCode` is never reused for a different binary: v1.0.3 shipped as versionCode 7 and was
rebuilt as versionCode 8 when its palette bug was fixed. The next release starts at 9.

## License

MIT — see [LICENSE](LICENSE). The copyright notice of the open-source project this codebase
derives from, plus the licences of every bundled third-party asset, are in [NOTICE.md](NOTICE.md).

© 2026 Umaiz Sufiyan.
