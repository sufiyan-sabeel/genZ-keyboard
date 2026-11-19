# GenZ Keyboard — Privacy Policy

GenZ Keyboard is an input method editor (IME). An IME sees everything you type, which is why
this policy is deliberately short and specific. Every statement below was checked against the
source in this repository; where a behaviour depends on a setting or a build, it says so.

## The one-line version

There is no analytics, no telemetry, no advertising and no remote logging. What you type stays on
your device unless you deliberately use a network feature — GIF search, translation, AI text
fixing, a plugin you installed, or the community features in a build that has them configured —
and each of those is described below, in full, including the ones that are switched off by
default.

## What the app does NOT do

- It does not record, store or transmit your keystrokes.
- It does not collect a typing history, and it does not build a profile of you.
- It does not send clipboard contents anywhere.
- It contains no advertising SDK, no analytics SDK and no crash-reporting service.
- It does not store passwords. In password fields the keyboard runs in a reduced mode: no
  suggestions, no learning, no clipboard panel.
- It does not request or use your location, contacts, camera, SMS or call state.
- It does not install software. It declares no `REQUEST_INSTALL_PACKAGES`, and an update is a
  download you install yourself.

## What it stores on your device

All of the following is stored locally, inside the app's private storage, and is removed when
you uninstall the app or clear its data.

| Data | Why | Where |
| --- | --- | --- |
| Keyboard settings (theme, height, haptics, layouts…) | Remember how you set it up | `SharedPreferences` |
| Personal dictionary — words you taught it | Better predictions | App-private storage |
| Clipboard history | So you can paste something you copied earlier | App-private storage; retention window is configurable; can be cleared at any time |
| Learned typing statistics | Prediction quality | App-private storage |
| API keys you enter for optional services | Calling that service on your behalf | Encrypted with AES-GCM under a key held by the Android Keystore; never written in plain text |

The app sets `android:allowBackup="false"`, so none of this is uploaded to Google's backup
service, and it is excluded from device-to-device transfer.

## Incognito

Incognito is a switch on the keyboard toolbar and in Settings. Turning it on does not encrypt
anything and does not hide the screen — it stops the keyboard from remembering and from sharing.

### What incognito stops

While incognito is active — switched on by you, or forced by the editor — the keyboard:

- **does not learn.** Words, word pairs, CJK phrases and "don't correct this again" markers are
  not written to the personal dictionary.
- **does not remember the clipboard.** Copied text is not added to clipboard history, is not
  persisted, and is not offered as a paste suggestion. New screenshots are not scanned into it
  either.
- **does not record emoji.** Your recently-used emoji list is left alone.
- **does not send anything to a configured AI provider**, including voice cleanup and the
  "fix text" action.
- **does not search GIFs over the network.** The built-in GIF catalogue still works and is
  filtered on the device.
- **does not ask for autofill suggestions.** The inline suggestion strip is closed before any
  request is built, and any response that arrives later is discarded.
- **does not deliver key or text events to plugins.** See below.

The same rules apply automatically in a password field, and in any field that asks the keyboard
not to learn from it (`IME_FLAG_NO_PERSONALIZED_LEARNING`).

### What still works in incognito

Everything you need to type: all layouts, themes, haptics, glide typing, emoji, the built-in GIF
catalogue, the clipboard *paste* action for something you copy now, and predictions from the
dictionary that already exists on your device. Incognito means "do not remember", not
"cannot suggest".

### What plugins receive

Plugins (mods) are user-installed scripts. In incognito the keyboard stops delivering the events
that carry what you typed — key presses, committed text, words, autocorrect, glide results, voice
transcriptions, clipboard contents, and even touch coordinates and cursor positions, because both
can be used to reconstruct the text.

Events that say nothing about your typing — panel changes, language changes, sensor levels for
tilt effects — still reach plugins, so a mod that only draws effects keeps working.

### What happens to work already in progress

Scripts run synchronously: the keyboard hands an event to a plugin and waits for it to return,
with a 300 ms budget. There is no background event queue, so there is nothing pending to discard
when you switch incognito on — the next event is simply not delivered.

### What cannot be undone

Two limits are worth stating plainly, because the keyboard cannot fix them:

- **Data already given to a plugin cannot be recalled.** If a script received text before you
  enabled incognito, that text is inside the script's memory and no API can take it back. The
  keyboard guarantees no *further* events are delivered; it cannot erase what was already passed.
- **Nothing on screen is hidden.** Incognito does not blank the display or stop the target app
  from receiving what you type — the app you are typing into always sees its own text field.

### What incognito does not delete

Turning incognito on does not erase what is already stored. Words learned earlier, clipboard
entries captured earlier, emoji recents and pinned clips remain until you clear them. Use
"Clear clipboard" and the personal-dictionary reset in Settings if you want them gone.

The keyboard's guarantee is about what happens *while incognito is active*, not a retroactive
erasure.

## Network use — the complete list

The app declares `INTERNET`. That permission is used by the seven paths below and by nothing
else. Six are off until you configure or trigger them. One — the update check — runs by itself,
and is described first because it is the one you did not ask for.

### 1. Update check (automatic)

When the app's settings screen opens, the app asks GitHub for the latest release of this
repository. It runs in the background, never blocks typing, and a failure is silent: with no
network the keyboard works exactly as before.

| | |
| --- | --- |
| Request | `GET https://api.github.com/repos/sufiyan-sabeel/genZ-keyboard/releases/latest` |
| Sent | Nothing about you, your device or your typing. No account, no identifier, no cookie. The only extra header is the user agent `GenZKeyboard-UpdateCheck`. |
| Frequency | At most once every 12 hours; the response is cached. |
| Result | If a newer stable release exists, one dialog offers it, once. Dismissing that version hides it for 7 days. |
| On accept | Your browser opens the APK download URL — only ever from this repository's own release downloads, and refused otherwise. The app downloads and installs nothing itself. |

This is the one network call the app makes without you starting it. It is not telemetry: GitHub
learns that *someone* checked, in the same way it learns that someone loaded a web page, and the
app sends nothing that distinguishes you. There is no setting to switch it off today — that is a
known limitation of this release, stated here rather than left out.

### 2. Dictionary download (when you add a language)

Adding a language whose word list is not bundled downloads that language's word-frequency file
from `raw.githubusercontent.com/hermitdave/FrequencyWords`. The request contains the language
code and nothing else. No text you have typed is involved.

### 3. GIF search (your key, off by default)

If you enter a Klipy or Giphy API key, the GIF panel searches that provider: your query and the
key go to `api.klipy.com` or `api.giphy.com`, and the chosen image is loaded from the provider's
media host. Without a key the panel offers only the catalogue bundled in the app, filtered on the
device. In incognito the network search is skipped entirely.

### 4. AI text fixing and voice cleanup (your key, on an explicit action)

If you configure an AI provider, two actions can send text to it: tapping the fix action on a
selected piece of text, and — when you have finished a dictation and a key is set — cleaning up
that transcription. Nothing is sent in the background, nothing is sent while incognito is on, and
nothing is sent for any other purpose.

Providers you can choose, each called with the key you supplied: Gemini
(`generativelanguage.googleapis.com`), Claude (`api.anthropic.com`), OpenAI (`api.openai.com`),
Groq (`api.groq.com`), OpenRouter (`openrouter.ai`), LanguageTool (`api.languagetool.org`), and
any OpenAI- or Anthropic-compatible service you add yourself — including a server on your own
network, which is the one case where the app may speak plain HTTP, to an address you typed.
Where the device supports it, the on-device Gemini Nano path is used instead and nothing leaves
the phone.

### 5. Translation (when you tap translate)

The translate panel sends the text you asked to translate — and nothing else — to a translation
service. The primary endpoint is Google's `translate.googleapis.com`, with two fallbacks
(`clients5.google.com` and `api.mymemory.translated.net`) used when the first fails. These are
public endpoints that need no key; the app sends the text, the source and target language, and a
browser-style user agent. The text is translated on a third party's server, so treat it as
disclosed to that provider. This path is not used by the keyboard itself, only by the panel you
open, and not at all while incognito is on.

### 6. Community features (only in a build that is configured for them)

The source in this repository also implements an optional community layer: an account, themes and
designs published by other people, reviews, likes, favourites, comments and reports. It is
backed by Supabase (PostgreSQL plus storage), and it is **switched off in every build unless the
builder supplies an endpoint and a key**. Official release APKs do not carry them, so in the
build you download from GitHub Releases these features do not exist.

When a build *is* configured, and only then: creating an account sends your email and password to
that Supabase project; publishing sends the theme or design you chose to publish; and posts,
comments, reviews and reports you write are stored there, attached to your account. Nothing you
type into other apps is involved, and row-level security in the database restricts each row to
its owner. If you run your own instance, that instance's operator sees the data.

### 7. Plugins you install

A plugin can fetch remote resources through the plugin API. Those requests come from the plugin's
own code, under its own behaviour — read a plugin before you install it. The API the keyboard
provides is restricted: HTTPS only, requests to private and loopback addresses refused, and a
rate limit of 60 requests a minute.

There is no other network code path in the app. There is no ping, no heartbeat, and no
background sync.

## Voice input

Voice typing hands the microphone to the **system speech recogniser** — the same service Android
uses elsewhere, chosen by your device, not by this app. The app receives only the resulting text
and inserts it. Whether the audio is processed on the device or sent to a server is decided by
that recogniser and by your device settings; Google's recogniser, for example, processes audio on
its servers for many languages. If that matters to you, check which recognition service your
device uses. The app itself never stores the audio and never sends it anywhere.

## Clipboard and screenshots

Clipboard history is local. Copied text is captured only while the keyboard is running, kept for
the retention window you choose, and can be cleared at any time. Nothing from the clipboard is
uploaded — there is no code path that would do it.

"Screenshots into the clipboard" is a separate feature and **off by default**. When you turn it
on and grant the media permission, the app watches the system media store for *new images whose
name or folder marks them as screenshots*, skips anything older than 24 hours, takes at most
five at a time, and ignores every other image. It reads the name, type and date of images to
decide that; it does not read your photo library into the app. In incognito, and in password
fields, the scan does not run.

## Permissions and why each exists

| Permission | Purpose | When asked |
| --- | --- | --- |
| `VIBRATE` | Haptic feedback on key press | Install time (normal permission) |
| `INTERNET` | The seven cases above | Install time (normal permission) |
| `RECORD_AUDIO` | Voice typing through the system speech recogniser | Only when you tap the microphone |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED` / `READ_EXTERNAL_STORAGE` | Offer a screenshot you just took as a clipboard entry. **Off by default.** | Only when you switch that feature on |

The media permission is the broadest one the app can ask for, which is why the feature it serves
is opt-in and defaults to off. If you never turn it on, the app never queries the media store.

The app does **not** declare `REQUEST_INSTALL_PACKAGES`. An earlier upstream version updated
itself by downloading an APK; this fork removed that path, so the app cannot install software.
Updates are ordinary GitHub Releases that you install yourself.

## Children

The app is a general-purpose keyboard and collects no data from anyone, including children.

## Changes

Material changes to this policy will be committed to this repository, so the history is public
and auditable.

## Contact

Open an issue at https://github.com/sufiyan-sabeel/genZ-keyboard/issues. Please do not paste
anything you typed into an issue, and report a security problem privately — see
[SECURITY.md](SECURITY.md).

*Last updated: 19 November 2026.*
