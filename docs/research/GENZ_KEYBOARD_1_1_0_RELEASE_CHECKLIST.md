# GenZ Keyboard 1.1.0 — Release Checklist

Nothing here has been executed for 1.1.0. This is the gate, not a record of passing it.
Each line is either ticked with evidence or it is not done.

---

## 1. Version and changelog

- [ ] `versionCode` incremented beyond 10 and **never reused** (1.1.0 → 11).
- [ ] `versionName` set to `1.1.0` in `app/build.gradle.kts`.
- [ ] `fastlane/metadata/android/en-US/changelogs/11.txt` written, ≤ 500 chars, user-facing.
- [ ] F-Droid metadata updated: `versionName`, `versionCode`, full 40-char `commit`, `CurrentVersion`,
      `CurrentVersionCode`.
- [ ] No version code reused for a different build.

## 2. Metadata and branding

- [ ] `fastlane` title, short description (≤ 80 chars), full description current.
- [ ] Description mentions new features and makes **no credential-storage claim**.
- [ ] `PRIVACY.md` updated for every feature that stores data, with the incognito column filled in.
- [ ] `NOTICE.md` and the in-app licence list updated for every new font, quote pack, sticker set
      and dictionary source.
- [ ] Branding unchanged except where the release notes say otherwise.

## 3. Icons and screenshots

- [ ] Icon present at required densities.
- [ ] Feature graphic present.
- [ ] **Real screenshots captured on a device** — settings, themes gallery, keyboard with a theme,
      emoji panel, GIF panel. No mockups presented as screenshots.
- [ ] Screenshots show the current UI, not an older build.

## 4. Builds

- [ ] `assembleStandardRelease` succeeds.
- [ ] `assembleFdroidRelease` succeeds.
- [ ] `assembleStandardDebugAndroidTest` compiles.
- [ ] AAB built for any Play distribution path.
- [ ] APK sizes recorded and compared with 1.0.5 (budget: +4 MB).
- [ ] Dependency policy gate green (no proprietary group on the fdroid runtime classpath).
- [ ] Flavour separation verified (`standard` artwork present, `fdroid` absent).

## 5. F-Droid compatibility and reproducibility

- [ ] No proprietary dependency introduced.
- [ ] Any new asset is MIT/OFL/Apache/public-domain compatible, or excluded from the `fdroid`
      flavour by source set.
- [ ] `fdroid readmeta` passes on the updated metadata.
- [ ] Builds from a clean clone at the tagged commit.
- [ ] Anti-feature declarations still accurate (`NonFreeNet` for GIF/AI; add more only if true).
- [ ] Reproducibility: F-Droid builds and signs its own APK. **A developer-signed APK is never a
      substitute.**

## 6. Dependency and licence audit

- [ ] Every new dependency reviewed for licence and necessity.
- [ ] Every bundled font has its licence text in `licenses/` and an entry in `NOTICE.md`.
- [ ] Every quote and slang pack has documented provenance and a compatible licence.
- [ ] Sticker and GIF provider attribution requirements satisfied.
- [ ] No new permission added; if one is, it is justified in `PRIVACY.md` and in the release notes.

## 7. Signing and artifact verification

- [ ] Release signing configured from secrets, not committed to the repository.
- [ ] APK signature verified (`apksigner verify`).
- [ ] SHA-256 of each artifact recorded in the release notes.
- [ ] `zip -T` / integrity check passes.
- [ ] Install over the previous version succeeds (upgrade path), not only a fresh install.

## 8. Testing before release

- [ ] Full JVM suite green — record the exact count and failures.
- [ ] `docs/research/DEVICE_TEST_CHECKLIST.md` executed on API 26–29 **and** API 30+.
- [ ] Password-field behaviour verified in a browser and a password manager.
- [ ] Incognito verified from **both** entry points (toolbar and Settings) without changing fields.
- [ ] Inline autofill verified with a provider configured and with none.
- [ ] GIF search verified in normal and private modes, with network observation.
- [ ] Typo-Undo verified after cursor movement, expiry, and in an app that rewrites its text.
- [ ] Rotation and view recreation verified with a chip visible.
- [ ] Fresh install on a clean device/profile.
- [ ] Upgrade from 1.0.5 with existing settings, learned words and clipboard history intact.
- [ ] Accessibility pass with TalkBack over the new panels.

## 9. Release notes and assets

- [ ] Release notes written for users: what is new, what changed, what is deliberately not included.
- [ ] No claim of "production ready" or "credential manager support".
- [ ] Assets uploaded: APK, SHA-256, changelog.
- [ ] F-Droid merge request updated if the metadata changed (see `fdroid/SUBMISSION.md`).
- [ ] Existing releases and tags preserved; no history rewritten.

## 10. Explicitly out of scope for 1.1.0

- Credential Manager as a client or provider; any password vault.
- Any credential logging, transmission or caching.
- Automatic decorative text transforms.
- Feature flags and partially shipped panels.

---

## Sign-off

| Item | Owner | Evidence |
| --- | --- | --- |
| Version + changelog | | |
| Builds green | | |
| Device checklist | | |
| Licence audit | | |
| Signing verified | | |
| Release notes | | |

A release ships when every row has evidence attached — not when the list is written.
