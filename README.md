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

- `standard` — what GitHub Releases ship. Includes the anime artwork: ten character themes and
  six wallpaper themes.
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
`app/src/standard/assets/anime/` — a `standard`-flavour source set, which is why the `fdroid`
build cannot contain it — is drawn behind the real keyboard through the same path that handles a
user-picked background, so cover-cropping, dimming, blur and transparency all apply.

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

The anime illustrations are supplied by the project owner and are **not** covered by the MIT
licence in `LICENSE` — that licence covers the code. They depict characters and styles owned by
third parties, and two carry other artists' signatures. Anyone redistributing this app publicly
should confirm they hold the rights to those images, or replace them. The code works with any
image: drop a replacement into `app/src/standard/assets/anime/` under the same filename.

[`docs/artwork-provenance.md`](docs/artwork-provenance.md) is the authoritative record: one row
per image, with its source, its rights holder, the licence or permission and the attribution
required. Every image is currently marked `UNVERIFIED — DO NOT DISTRIBUTE`, which is why the
`fdroid` flavour ships none of them. Adding an image without recording its rights fails CI.

**Privacy**

- The keyboard itself never sends what you type. There is no keystroke logging, no remote logging,
  no analytics and no crash reporting. Text leaves the device only through an action you take:
  AI text fixing, the translate panel, or publishing a theme — each documented in
  [PRIVACY.md](PRIVACY.md), none of them on by default
- No ads and no tracking SDKs
- Clipboard contents are never uploaded
- All settings stay in local `SharedPreferences`; API keys are encrypted under a key held by the
  Android Keystore
- Screenshot capture into the clipboard history is **off by default** and requires an explicit
  permission grant

One request is automatic: when the settings screen opens, the app asks GitHub for the latest
release so it can offer an update. It sends no identifier, runs at most every twelve hours, and
never blocks typing. Every other network path is listed in [PRIVACY.md](PRIVACY.md), with what is
sent and when.

## Screenshots

No screenshots are committed yet. Rather than publish mockups, the repository only ships images
taken from a real device build — a render or an image from an older build would misrepresent the
app, which is why the previous two were removed.

[`docs/screenshots/README.md`](docs/screenshots/README.md) is the capture checklist: the seven
screens needed, the file names, the sizes, and the two places each file goes (the README and the
F-Droid listing). It is the one outstanding item that needs a device rather than a commit.

## Install

### From a release (easiest)

1. Open [Releases](https://github.com/sufiyan-sabeel/genZ-keyboard/releases) and download the
   latest `GenZKeyboard-vX.Y.Z.apk`.
2. Open the APK on the device and allow installation from your browser/file manager.
3. Open **GenZ Keyboard** from the app drawer and follow onboarding.
4. Enable it: **Settings → System → Languages & input → On-screen keyboard → Manage
   keyboards → GenZ Keyboard**.
5. Select it: tap any text field, then the keyboard switcher in the navigation bar.

> **Signing.** Release APKs are signed with a production keystore supplied to CI through
> repository secrets, and CI verifies the certificate fingerprint against
> `RELEASE_CERT_SHA256` before publishing. The key is stable, so a later release installs over an
> earlier one without uninstalling. The workflow refuses to publish at all when the keystore or
> the expected fingerprint is missing — there is no debug-key fallback, because a differently
> signed APK under the same application id would break every future update.

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

Version name and code come from [`version.properties`](version.properties) at the repository
root — the single place either is defined — and can be overridden per build with
`-PversionName=` and `-PversionCode=`. CI passes the tag it is building and
`scripts/check-release-version.sh` fails the build when the tag, the properties file and the
changelog for that version code disagree.

A local release build is **unsigned** unless you provide a keystore through `local.properties`
(`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`).
That is deliberate: an unsigned APK cannot be installed by mistake, and a debug-signed one would
look installable while breaking every future update. `local.properties` is git-ignored, and no
keystore is committed to this repository — a public repository must never contain a signing key.

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

JVM unit tests cover the correction pipeline, glide-swipe scoring, theme and plugin parsing, the
community rules, the privacy policies and the update decision; they need no device.
Instrumentation tests and on-device behaviour (haptics, IME connection edge cases) are not
covered by automated tests in this repository — those need a device or emulator.

Two checks guard the parts that drift:

```bash
bash scripts/check-release-version.sh          # tag, versionName and F-Droid changelog agree
python3 scripts/check-artwork-provenance.py            # consistency; unverified art only warns
python3 scripts/check-artwork-provenance.py --release  # release gate: fails on unverified art
python3 -m unittest discover -s scripts/tests          # tests for the gate itself
```

CI runs both, the unit tests and the debug build on every push to `main`
(`.github/workflows/android.yml`) and uploads the APK as an artifact.

## Documentation

| Document | What it covers |
| --- | --- |
| [PRIVACY.md](PRIVACY.md) | Every network path, every permission, incognito, the community backend |
| [SECURITY.md](SECURITY.md) | How to report a vulnerability privately |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Ground rules, build steps, code style |
| [PLUGINS.md](PLUGINS.md) | The plugin and theme file format, with the 45 bundled definitions |
| [docs/README.md](docs/README.md) | Index of the design, audit and roadmap documents |
| [docs/artwork-provenance.md](docs/artwork-provenance.md) | Rights position of every bundled image |
| [NOTICE.md](NOTICE.md) | Third-party asset licences and the attribution they require |
| [fdroid/SUBMISSION.md](fdroid/SUBMISSION.md) | State of the F-Droid submission |

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
- **Network.** There is no telemetry and no heartbeat, and nothing you type is sent anywhere by
  the keyboard itself. Seven paths use `INTERNET`, and PRIVACY.md documents each one: the
  automatic GitHub Releases update check (no identifier, at most every 12 hours, the only call
  the app makes on its own); language dictionary downloads; GIF search with your own Klipy or
  Giphy key; AI text fixing and voice cleanup with your own provider key; the translate panel,
  which sends the text you asked to translate to a translation service; the optional community
  backend, which is absent from every release APK; and plugins you install, whose requests are
  HTTPS-only and rate-limited.
- **Permissions.** `VIBRATE`; `INTERNET`; `RECORD_AUDIO` only when you tap the microphone;
  and the media permissions only if you switch on "screenshots in clipboard", which is off by
  default. When it is on, the app asks the media store for images marked as screenshots that are
  newer than its last scan and less than a day old, and ignores everything else.
- **Optional services.** Every other network feature is off unless you configure it. The app is
  fully usable as a keyboard with no account, no key and no network.

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
excluded from the F-Droid flavour for that reason. Per-image details are in
[`docs/artwork-provenance.md`](docs/artwork-provenance.md), and the third-party asset notices are
in [NOTICE.md](NOTICE.md).

## F-Droid maintenance

Notes for whoever maintains the F-Droid listing:

| | |
| --- | --- |
| Application ID | `com.genz.keyboard` |
| Source | https://github.com/sufiyan-sabeel/genZ-keyboard |
| Issue tracker | https://github.com/sufiyan-sabeel/genZ-keyboard/issues |
| Licence | MIT |
| Release tags | `vMAJOR.MINOR.PATCH`, e.g. `v1.0.3` |
| Versioning | both come from `version.properties`; the tag must equal `versionName`, and `versionCode` is bumped by hand for **every** published build, including a rebuild of the same version |
| Build flavour | `fdroid` (`gradle: - fdroid`), no proprietary dependencies, no artwork of unresolved rights (emoji and font assets are separately licensed — see [NOTICE.md](NOTICE.md)) |
| Update check | `Tags` |
| Auto-update | `Version` |

`versionCode` is never reused for a different binary: v1.0.3 shipped as versionCode 7 and was
rebuilt as versionCode 8 when its palette bug was fixed. F-Droid rejects a second binary for a
code it has already built, which is why the increment is a deliberate step rather than something
Gradle derives. `scripts/check-release-version.sh` fails the build when the tag, `versionName`
and the changelog for the current `versionCode` disagree, and `scripts/check-artwork-provenance.py`
fails it when an image has no recorded rights position.

A standard release is currently blocked by design: sixteen bundled illustrations are recorded as
`UNVERIFIED — DO NOT DISTRIBUTE`, so the release job stops before it builds or publishes anything.
`assembleStandardDebug` and the `fdroid` flavour are unaffected. See
[`docs/artwork-provenance.md`](docs/artwork-provenance.md) for the gate and how to clear an asset.

## License

MIT — see [LICENSE](LICENSE). The copyright notice of the open-source project this codebase
derives from, plus the licences of every bundled third-party asset, are in [NOTICE.md](NOTICE.md).

© 2026 Umaiz Sufiyan.
