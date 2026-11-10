# Submitting GenZ Keyboard to F-Droid

The metadata file in this directory is ready. What remains is a merge request against
[fdroiddata](https://gitlab.com/fdroid/fdroiddata), which needs a GitLab account — it cannot be
opened from the build environment that prepared this repository.

Everything below is the exact sequence. It takes about ten minutes.

## 1. Prerequisites

- A GitLab account (gitlab.com; the F-Droid forge is not GitHub).
- SSH or HTTPS access to your own fork.
- Optional but recommended: `fdroidserver` locally, so `fdroid lint` can catch typos before the
  reviewers do:

  ```bash
  pip install --user fdroidserver
  ```

## 2. Fork and clone fdroiddata

```bash
# fork https://gitlab.com/fdroid/fdroiddata with the Fork button, then:
git clone git@gitlab.com:<your-gitlab-user>/fdroiddata.git
cd fdroiddata
git checkout -b add-com.genz.keyboard
```

## 3. Add the metadata file

```bash
cp /path/to/genZ-keyboard/fdroid/metadata/com.genz.keyboard.yml metadata/com.genz.keyboard.yml
```

The file must sit at `metadata/com.genz.keyboard.yml` in the **root** of fdroiddata, named exactly
after the application id. Nothing else is copied — screenshots and descriptions come from the
app's own repository through `fastlane/metadata/android/en-US/`, which is already in place.

## 4. Validate

```bash
fdroid readmeta            # parses every metadata file; must exit 0
fdroid rewritemeta com.genz.keyboard   # normalises field order and quoting
fdroid lint com.genz.keyboard          # must report no errors
git diff                   # review what rewritemeta changed, then commit that version
```

`fdroid lint` needs a checkout of fdroiddata to resolve the category and anti-feature
vocabularies, which is why it has to be run inside this clone rather than anywhere else.

## 5. Optional but worth it: build it yourself first

```bash
fdroid build com.genz.keyboard:9
```

This uses the same recipe the reviewers will use. If it fails, fix it here rather than in review.

## 6. Commit and push

```bash
git add metadata/com.genz.keyboard.yml
git commit -m "New app: GenZ Keyboard (com.genz.keyboard)"
git push -u origin add-com.genz.keyboard
```

GitLab prints a link to open a merge request from the push. If not:

> https://gitlab.com/fdroid/fdroiddata/-/merge_requests/new

Target `fdroid/fdroiddata` `master`, source your branch.

## 7. What to write in the merge request

Keep it short, but paste the evidence — reviewers ask for it anyway.

```
New app: GenZ Keyboard (com.genz.keyboard)

Android IME (keyboard) in Kotlin + Jetpack Compose, MIT licensed.

  Repository : https://github.com/sufiyan-sabeel/genZ-keyboard
  Release    : v1.0.5  (versionName 1.0.5, versionCode 10)
  Source     : c111eac0b88de808b31533b8528aac760ef8a8e3
  APK sha256 : 9118922a6f15111cfe9d3b0ddaf9d6f9ebabff1fe7b6cf72f1c7099b3a2a4403

Build
- Gradle flavour `fdroid`, task `assembleFdroidRelease`, no submodules, no NDK.
- `fdroid readmeta` parses this file cleanly; all required fields present.

Free software
- No proprietary dependencies on the fdroid runtime classpath (668 artifacts resolved,
  checked automatically on every push).
- Google ML Kit (on-device Gemini Nano proofreading) was removed specifically for F-Droid.
- CI fails the build if a known proprietary group (mlkit, firebase, gms, play, ad SDKs)
  reappears on that classpath.

Assets
- The `standard` flavour used for GitHub releases bundles ten anime illustrations whose
  redistribution rights are unresolved; the `fdroid` flavour excludes them, so this
  metadata declares no NonFreeAssets. Themes fall back to colour palettes.
- CI verifies the split: standard build has 10 artwork files, fdroid build has 0, and fails
  otherwise.
- Emoji: Twemoji CC-BY 4.0, Noto/Blobmoji Apache 2.0, OpenMoji CC BY-SA 4.0.
  Font: Pixelify Sans, SIL OFL 1.1 (full text in licenses/). Dictionaries: MIT / CC BY 3.0.
  All documented in NOTICE.md.

AntiFeatures
- Declared NonFreeNet: optional GIF search and AI text fixing use third-party services with
  a user-supplied key. Off by default, no keys bundled, no analytics, no tracking.
```

## 7a. Verification already performed on this repository

Useful if a reviewer asks "how do you know?":

| Check | Result |
| --- | --- |
| Published APK metadata (read from the APK, not the build log) | `com.genz.keyboard`, versionName 1.0.5, versionCode 10, minSdk 26, targetSdk 35, v2 signed |
| Release asset checksum | `9118922a…` matches the digest GitHub reports for the asset |
| Tag → commit | `v1.0.5` → `c111eac0…` (the commit this metadata names) |
| v1.0.4 left untouched | its APK still hashes to `3785e86e…`, unchanged since publication |
| Proprietary dependency gate | deliberately reintroducing `com.google.mlkit:genai-proofreading` fails CI at the dependency step, naming `mlkit:common` and `genai-common` as the transitive hits |
| Flavour separation gate | deliberately adding one artwork file to the shared source set fails CI with `standard 11 / fdroid 1` |
| Metadata | `fdroid readmeta` exits 0; required fields all present |

Both gates were tested by breaking them on throwaway branches, confirming the failure, then
deleting the branches — so they are known to be live checks, not decoration.

## 8. Expect these review questions

- **"Is the app useful without the non-free services?"** Yes — every network feature is opt-in
  and the keyboard works with no key and no network.
- **"Are the emoji and dictionary assets free?"** Yes: Twemoji CC-BY 4.0, Noto/Blobmoji Apache
  2.0, OpenMoji CC BY-SA 4.0, word frequencies MIT, Google Books Ngram CC BY 3.0, Pixelify Sans
  OFL 1.1. All documented in `NOTICE.md`, with the OFL text in `licenses/`.
- **"Why two flavours?"** So the published source and the F-Droid build are the same code, with
  only the unresolved artwork excluded.
- **"Screenshots?"** The listing currently has the icon and a feature graphic; real screenshots
  will be added from a device build.

## 9. After it is merged

- F-Droid builds and signs its own APK. Never ask users to install the GitHub APK as a
  substitute.
- The listing appears at `https://f-droid.org/packages/com.genz.keyboard/` only after the build
  lands in the index, which is usually the next cycle.
- Future releases need no new merge request: `UpdateCheckMode: Tags` picks up `vX.Y.Z` tags
  automatically, as long as `versionCode` increases.

**Nothing in this repository should be read as "available on F-Droid".** Acceptance is the
maintainers' decision, and a merge request is not acceptance.
