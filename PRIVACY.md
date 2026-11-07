# GenZ Keyboard — Privacy Policy

GenZ Keyboard is an input method editor (IME). An IME sees everything you type, which is why
this policy is deliberately short and specific.

## The one-line version

Nothing you type leaves your device. There is no analytics, no telemetry, no advertising and
no remote logging. Settings stay in local storage.

## What the app does NOT do

- It does not record, store or transmit your keystrokes.
- It does not collect a typing history, and it does not build a profile of you.
- It does not send clipboard contents anywhere.
- It contains no advertising SDK, no analytics SDK and no crash-reporting service.
- It does not store passwords. In password fields the keyboard runs in a reduced mode: no
  suggestions, no learning, no clipboard panel.
- It does not request or use your location, contacts, camera, SMS or call state.

## What it stores on your device

All of the following is stored locally, inside the app's private storage, and is removed when
you uninstall the app or clear its data.

| Data | Why | Where |
| --- | --- | --- |
| Keyboard settings (theme, height, haptics, layouts…) | Remember how you set it up | `SharedPreferences` |
| Personal dictionary — words you taught it | Better predictions | App-private storage |
| Clipboard history | So you can paste something you copied earlier | App-private storage; retention window is configurable; can be cleared at any time |
| Learned typing statistics | Prediction quality | App-private storage |

The app sets `android:allowBackup="false"`, so none of this is uploaded to Google's backup
service, and it is excluded from device-to-device transfer.

## Network use — what, when, and only on request

The app declares the `INTERNET` permission, and it is used in exactly four situations. None
of them is automatic, and none of them involves what you type unless you explicitly trigger
it:

1. **Dictionary downloads.** When you add a language that has no bundled word list, the app
   downloads that language's word-frequency file. The request contains the language code and
   nothing else.
2. **GIF search.** Only if you configured a Klipy or Giphy API key yourself. Your search query
   goes to that provider. Without a key the panel is unavailable.
3. **AI text fixing.** Only when you tap the fix action on a selected piece of text. That text
   is sent to the provider you configured — Google's on-device AICore (no network at all) or
   an API endpoint you supplied with your own key. Nothing is sent in the background, and no
   text is ever sent for any other purpose.
4. **Plugin assets.** If you install a plugin that fetches remote resources, that plugin's
   request goes out under its own behaviour. Plugins are opt-in and installed by you.

There is no other network code path in the app. There is no update-check, no ping, no
heartbeat.

## Permissions and why each exists

| Permission | Purpose | When asked |
| --- | --- | --- |
| `VIBRATE` | Haptic feedback on key press | Install time (normal permission) |
| `INTERNET` | The four user-initiated cases above | Install time (normal permission) |
| `RECORD_AUDIO` | Voice typing through the system speech recogniser | Only when you tap the microphone |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED` / `READ_EXTERNAL_STORAGE` | Offer a screenshot you just took as a clipboard entry. **Off by default.** | Only when you switch the feature on |

The media permission is the broadest one the app can ask for, so it is opt-in and defaults to
off. If you never turn it on, the app never reads your photo library.

The app does **not** declare `REQUEST_INSTALL_PACKAGES`. An earlier upstream version updated
itself by downloading an APK; this fork removed that path, so the app cannot install software.
Updates are ordinary GitHub Releases that you install yourself.

## Children

The app is a general-purpose keyboard and collects no data from anyone, including children.

## Changes

Material changes to this policy will be committed to this repository, so the history is
public and auditable.

## Contact

Open an issue at https://github.com/sufiyan-sabeel/genZ-keyboard/issues. Please do not paste
anything you typed into an issue.

*Last updated: 7 November 2026.*
