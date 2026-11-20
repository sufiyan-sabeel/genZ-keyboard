# Signing and updates

This document records what is true about release signing, because the repository previously
described a pipeline that did not exist yet and a stability that the artifacts do not have.

## 1. The state of the published releases

Every release published so far — v1.0.0 to v1.0.6 — was signed with a **freshly generated debug
key**, one per release. That was established by parsing the APK Signing Block of each published
APK and extracting the signer certificate:

| Release | Signer certificate (SHA-256, first 32 hex) | Certificate valid from |
| --- | --- | --- |
| v1.0.0 | `2916051c2a5040894299272aa192f945` | 2026-11-08 07:38:51Z |
| v1.0.1 | `f9da059954805cee96f5186bca9bcac4` | 2026-11-08 09:56:42Z |
| v1.0.2 | `d48e9abeb700b9d31863e0ce9ffa1c3b` | 2026-11-09 07:49:31Z |
| v1.0.3 | `581e2b9497a26b1d892e408a95c71db2` | 2026-11-09 15:13:19Z |
| v1.0.4 | `aac23647b09c497f70fbe3cfb951e2d8` | 2026-11-10 03:21:25Z |
| v1.0.5 | `6a29135be4c1f231f580579d78f70a20` | 2026-11-10 04:05:08Z |
| v1.0.6 | `02d069b2d38a291cb8e63d1ba3daef57` | 2026-11-17 15:55:17Z |

Seven releases, seven distinct certificates, each with subject `CN=Android Debug, O=Android, C=US`
and a thirty-year validity starting at its own build time. A stable keystore produces one
certificate every time; this is what a per-build generated key looks like.

The cause is a date: commit `aee4db9` (*build(signing): remove every silent debug fallback from
production releases*, 18 November 2026) added the keystore requirement and the certificate pin.
It landed one day after v1.0.6 was tagged, so no release has yet been built by the hardened
pipeline.

## 2. What the pipeline enforces now

- `RELEASE_KEYSTORE_B64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
  `RELEASE_KEY_PASSWORD` must be present, or the release job exits before building.
- `RELEASE_CERT_SHA256` must be set, and the built APK's signer certificate must match it, or the
  job exits before publishing.
- `app/build.gradle.kts` sets no signing config when no keystore is provided, so a release build
  without one is **unsigned** rather than debug-signed. An unsigned APK cannot be installed by
  accident.
- One APK is selected, once (`scripts/select-release-apk.sh`), and that exact file is
  checksummed, artwork-checked, certificate-checked and uploaded. The job refuses to pick between
  two APKs.

Whether the repository secrets actually contain a stable production keystore is not knowable from
the repository, and this document does not claim it. The first release built by this pipeline will
settle it, and the `signature:` line in its notes will be the evidence.

## 3. Consequence for existing users

Because every release so far has a different key, **no release can be installed over an earlier
one**. Android rejects the install with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and there is nothing
the app can do about that after the fact — the check happens in the package manager.

This is not recoverable retroactively. There is no bridge release that fixes it, because a bridge
would have to be signed with the key the user already has, and every user's key is different.

What is recoverable is the user's data.

## 4. Migration for a user on any release up to v1.0.6

1. Open **Settings → Backup** and export a backup to a file you can reach later. It contains
   settings, the personal dictionary, themes, wallpapers and clipboard history.
2. Uninstall GenZ Keyboard. This deletes the app's private storage, which is why step 1 exists.
3. Install the new APK.
4. Open **Settings → Backup** and import the file.

The in-app update dialog does this part of the work for you: when the release notes declare a
`signature:` that differs from the installed app's certificate, the dialog is titled *Update
Requires Reinstall* and says plainly that settings, learned words and clipboard history will be
lost unless a backup is exported first. It does not present an uninstallable update as routine.

## 5. Checklist for the first production-signed release

Do these in order, once, and the problem stops recurring:

1. Generate the keystore once, offline, with a long validity and a real password manager entry.
   Never commit it, never paste it into an issue, never regenerate it because it is inconvenient.
2. `base64 -w0 release.keystore` into the `RELEASE_KEYSTORE_B64` secret, and the other three
   signing secrets alongside it.
3. Build any release with that keystore, run
   `apksigner verify --print-certs <apk>`, and put the printed SHA-256 into the repository
   variable `RELEASE_CERT_SHA256`.
4. Back up the keystore somewhere that is not this repository and not one laptop. Losing it means
   every future release needs another uninstall cycle.
5. Publish. The release notes will carry `signature:` and `sha256:` lines; check that `signature:`
   matches step 3.
6. From then on, updates install over the previous version normally, and the dialog returns to
   *Update Available*.

## 6. What the updater reads

The release body written by `.github/workflows/android.yml` contains, near the top:

```
versionCode: 12
signature: 02d069b2…
sha256: f6b67697…
```

`UpdateChecker` parses `versionCode` and `signature`. `InstalledSignature` computes the SHA-256 of
the running app's signing certificate through the package manager. `UpdateChecker.installability`
compares them and returns `UNKNOWN`, `COMPATIBLE` or `INCOMPATIBLE`; an unknown on either side is
never treated as incompatible, because a warning that fires on missing evidence trains people to
ignore warnings.

## 7. If the production keystore is lost

The same migration in section 4 applies to every user, again. That is the whole cost of losing it,
and it is worth writing down before it happens rather than after.
