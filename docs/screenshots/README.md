# Screenshots — capture checklist

The repository deliberately contains **no** screenshots. The two that used to sit in
`fastlane/metadata/android/en-US/images/phoneScreenshots/` were captured from 1.0.0/1.0.1 and
showed a label and a settings UI that no longer exist, so they were removed rather than left to
misrepresent the app.

Nothing here is generated. A mockup, a render or an image from an older build is not a screenshot
and must not be published as one — the README says so, and this checklist exists so that the
honest option is also the easy one.

## What is needed

| # | File name | Screen |
| --- | --- | --- |
| 1 | `keyboard-main.png` | The keyboard open in any text field, light theme, an anime theme applied |
| 2 | `keyboard-dark.png` | The same, AMOLED or dark theme |
| 3 | `keyboard-themes.png` | Settings → Themes, the style gallery |
| 4 | `keyboard-anime.png` | Settings → Themes → Anime themes, the artwork cards |
| 5 | `keyboard-customization.png` | Settings → Colors, with the background-image sliders visible |
| 6 | `keyboard-emoji.png` | The emoji panel |
| 7 | `keyboard-settings.png` | The settings home, dark mode switch and sections |

Two are mandatory for the F-Droid listing to show the app properly; four or more is the target.
`featureGraphic.png` and `icon.png` are already present and correct.

## How to capture them

1. Install the current release, or build `assembleStandardDebug`, on a real device.
2. Enable the keyboard: **Settings → System → Languages & input → On-screen keyboard → Manage
   keyboards → GenZ Keyboard**.
3. Fill the keyboard with real-looking text — a sentence you would actually type — not
   `asdfghjkl`. Empty suggestion strips and placeholder text are the most obvious tell of a fake.
4. Take the screenshot with the device's own capture (Power + Volume Down), then crop to the
   keyboard, or to the settings screen, without the status bar.
5. Save as PNG, width 1080 px or more, portrait.
6. Put the same file in both places:
   - `docs/screenshots/` — for the README
   - `fastlane/metadata/android/en-US/images/phoneScreenshots/` — for the store listing, named
     `1.png`, `2.png`, … in the order above
7. Reference them from the README's *Screenshots* section.

## Before you commit

- Check every visible string is English, not Russian. Two strings are currently untranslated
  (`Сначала сервис с ключом, потом LanguageTool`, and the language-switching hint in Settings);
  avoid those screens or fix the strings first.
- Check no personal text, notification or account name is in frame. A keyboard screenshot is a
  screenshot of whatever you were typing.
- Check the theme name and version label match the current build.
