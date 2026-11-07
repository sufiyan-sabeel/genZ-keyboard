# Attribution and upstream notice

GenZ Keyboard is a fork of **Expressive Keyboard** by **kotlalipopus**, used under the MIT License.

- Upstream repository: https://github.com/kotlalipopus/Expressive-Keyboard
- Upstream commit this fork starts from: `94594a19afb200a482f01f6f76f8a6901b83635d`
- Upstream license: MIT (see `LICENSE`)

The MIT License requires that the original copyright notice and permission notice be
retained. Both are kept in `LICENSE`. GenZ Keyboard adds its own copyright line for the
changes made in this fork; it does not remove or weaken the upstream notice.

## What this fork changes

- New application identity: `com.genz.keyboard`, app name *GenZ Keyboard*, new launcher
  icon, new brand palette and a creator credit for Umaiz Sufiyan.
- Removed the built-in OTA updater and the `REQUEST_INSTALL_PACKAGES` permission that went
  with it. Updates are distributed through GitHub Releases instead, installed by the user.
- Screenshot capture into the clipboard history is off by default.
- Split the very large Russian rare-word list into two assets so it can be shipped and
  maintained in reasonable file sizes.
- Added this repository's own CI workflow, README, privacy policy and contribution guide.

Everything else — the IME engine, the layouts, the theme system, the dictionaries, the
plugin engine — is upstream work and remains upstream's credit.

## Third-party assets bundled in the app

| Asset | Source | License |
| --- | --- | --- |
| Twemoji | Twitter / jdecked | CC-BY 4.0 |
| Noto Emoji, Blobmoji | Google | Apache 2.0 |
| OpenMoji | HfG Schwäbisch Gmünd | CC BY-SA 4.0 |
| Emoji names | Unicode CLDR | Unicode License |
| Pinyin data | mozillazg/pinyin-data, OpenCC | MIT / Apache 2.0 |
| Word frequencies | hermitdave/FrequencyWords | MIT |
| Russian word pairs | Google Books Ngram Viewer | CC BY 3.0 |
| English word pairs | Peter Norvig, count_1w / count_2w | as published |
