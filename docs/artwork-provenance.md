# Artwork provenance inventory

This is the authoritative record of every image file in the repository that can end up in a
built app, and of the three that are held back. It exists because the MIT licence in
[LICENSE](../LICENSE) covers the source code only: an image needs its own permission, and a
permission that nobody wrote down cannot be checked.

**Nothing in this file is a licence grant.** Where no licence or written permission is
documented, none is implied, and the asset is marked `UNVERIFIED — DO NOT DISTRIBUTE`.

## How to read the status column

| Status | Meaning |
| --- | --- |
| `CLEARED` | A licence or a written permission is documented in the *Licence or permission* column, and the attribution it requires is recorded. |
| `UNVERIFIED — DO NOT DISTRIBUTE` | No licence or written permission is documented. Distribution is a rights risk that the project owner carries. |

Two rules follow from this, and both are enforced in CI by
[`scripts/check-artwork-provenance.py`](../scripts/check-artwork-provenance.py):

1. Every image file in `app/src/standard/assets/anime/` and `docs/pending-themes/artwork/` must
   have a row in the tables below.
2. No image may be added to the `fdroid` flavour. That flavour ships the keyboard without any of
   these files, which is what makes it distributable while the rights question is open.

## The release gate

A development build and a published release are not treated the same. The script has three modes,
and only one of them can stop a release:

| Mode | Where it runs | What it does |
| --- | --- | --- |
| default | every push and pull request | Checks that rows and files agree, and that the fdroid flavour carries no artwork. Unverified artwork in the standard flavour is a **warning**, so ordinary work is not blocked. |
| `--release` | the `release` job, before signing, before the build, before `gh release create`; also in the signed-APK job | Everything above, plus: any image in the standard source set that is not `CLEARED` **fails the job**. There is no override flag and no workflow input that skips it. |
| `--apk PATH` | after the release APK is built, before it is uploaded | Opens the APK, lists `assets/anime/*` inside it, and fails if any of those images is unverified or has no row. This checks the artifact that would be published, not just the tree it came from. |

The `fdroid` release path is checked by the same inventory: the flavour must bundle no artwork at
all, and the built fdroid APK is inspected the same way. Its expected result is zero images.

**Consequence, stated plainly:** until at least the sixteen shipped images are `CLEARED`, the
`release` job fails at its third step and no GitHub Release can be created. That is the intended
state, not a broken build. The `build` job, `assembleStandardDebug` and every development path
continue to work.

**How to unblock it:** record the rights for an image and change its status to `CLEARED` with the
licence or written permission in the row. Nothing else — no flag, no environment variable, no
workflow input — will do it.

## The finding this file makes visible

All nineteen images are currently `UNVERIFIED`. Sixteen of them are nonetheless distributed, in
the `standard` flavour of every release. That gap is the single largest risk in the project, and
this inventory exists so that it is stated in one place instead of implied across three
documents that disagree with each other.

Removing artwork, regenerating it or editing out an artist's signature is **not** something this
document does or recommends doing silently. What it records is the state of the evidence. What
to do about it is the owner's decision.

## Shipped images — `app/src/standard/assets/anime/`

These sixteen files are packaged into the `standard` flavour (GitHub Releases) and are absent
from the `fdroid` flavour.

| # | File | Theme | MD5 (12) | Source | Creator / rights holder | Licence or permission | Required attribution | Rights status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `crimson.webp` | Crimson Split | `025bdc955117` | Not recorded | Unknown | None documented | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 2 | `ego.webp` | Ego | `33df77d17e39` | Not recorded | Unknown | None documented | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 3 | `flame.webp` | Flame Dance | `ebf1b68238ff` | Not recorded | Unknown | None documented | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 4 | `ink.webp` | Ink Demon | `9053d422a4ee` | Not recorded | Unknown | None documented | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 5 | `manga.webp` | Never Give Up | `d038e8118e23` | Not recorded | Unknown | None documented | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 6 | `rose.webp` | Red Rose | `ccaad3be133b` | Not recorded | Unknown | None documented | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 7 | `sage.webp` | Sage of the Leaf | `b195ea99376e` | Not recorded | Unknown | None documented | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 8 | `sky.webp` | Straw Hat Sky | `8687413c0754` | Not recorded | Unknown | None documented | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 9 | `split.webp` | Two Halves | `aa6e8b56c401` | Not recorded | Unknown | None documented | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 10 | `violet.webp` | Violet Storm | `5ed354abad3c` | Not recorded | Unknown | None documented | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 11 | `art_rainytokyo.webp` | Rainy Tokyo | `759560389e03` | Supplied by the project owner | Unknown — no visible signature | Owner assertion only; no licence document supplied | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 12 | `art_pastel.webp` | Pastel Garden | `d50343be8031` | Supplied by the project owner | Unknown — no visible signature | Owner assertion only; no licence document supplied | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 13 | `art_sunset.webp` | Anime Sunset | `d85c47751071` | Supplied by the project owner | Unknown — no visible signature | Owner assertion only; no licence document supplied | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 14 | `art_cabin.webp` | Moonlit Cabin | `db3a28715249` | Supplied by the project owner | Unknown — no visible signature | Owner assertion only; no licence document supplied | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 15 | `art_snowfall.webp` | Snowfall Pines | `5c98102cd28b` | Supplied by the project owner | Unknown — no visible signature | Owner assertion only; no licence document supplied | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 16 | `art_blueroad.webp` | Blue Hour Road | `1f55c5eb59fa` | Supplied by the project owner | Unknown — no visible signature | Owner assertion only; no licence document supplied | None recorded | `UNVERIFIED — DO NOT DISTRIBUTE` |

Rows 1–10 are the ten character illustrations. [`NOTICE.md`](../NOTICE.md) records that **two of
them carry another artist's signature**; the repository does not record which two, and the
signatures have not been removed. Rows 11–16 are the six wallpaper illustrations added in 1.0.7,
recorded as supplied by the owner with no third-party mark visible.

## Withheld images — `docs/pending-themes/artwork/`

Not packaged in either flavour. They sit in `docs/` so the work is not lost and the rights
question stays visible.

| # | File | Theme | MD5 (12) | Source | Creator / rights holder | Licence or permission | Required attribution | Rights status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 17 | `art_windmill.webp` | Midnight Windmill | `11723cc4bafc` | Supplied by the project owner | Signed `SNIPER` — the mark is visible in the image | None. Permission not obtained | As the signing artist requires, once known | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 18 | `art_samurai.webp` | Ink Samurai | `4ffc94ee203f` | Supplied by the project owner | Signed `Dream Render` — the mark is visible in the image | None. Permission not obtained | As the signing artist requires, once known | `UNVERIFIED — DO NOT DISTRIBUTE` |
| 19 | `art_sakura.webp` | Sakura Night | `fed7ac273ffe` | Supplied by the project owner | Signed `AFERA XV` — the mark is visible in the image | None. Permission not obtained | As the signing artist requires, once known | `UNVERIFIED — DO NOT DISTRIBUTE` |

## `artwork-sources.json` — why it is not a provenance record

[`artwork-sources.json`](../artwork-sources.json) lists ten image URLs on a Google-hosted CDN,
under the keys `dark`, `sakura`, `samurai`, `blue`, `crimson`, `kawaii`, `manga`, `moonlight`,
`purple` and `tokyo`. It is **not** usable as the provenance record for any file above, for three
reasons, each checkable against the file:

- the keys are theme nicknames, not filenames, and no mapping between them and the shipped files
  is recorded anywhere in the repository;
- three keys — `dark`, `kawaii`, `purple` — correspond to no file in either directory;
- ten URLs are listed against nineteen images.

The URLs are therefore recorded here as a lead for the owner, not as evidence. If a key is later
established to be the source of a specific file, record that in the *Source* column of that row
and say who generated it, under what account, and what terms apply. Generated images are not
automatically free of third-party rights either: the terms of the generator govern their use, and
an image that reproduces a recognisable character still needs the character owner's permission.

## Third-party assets that are not artwork

Emoji sets, the bundled font and the dictionary data are separate works with their own licences,
which require attribution. They are recorded in [NOTICE.md](../NOTICE.md) and are not part of
this inventory.

## Clearing an asset

To move a row from `UNVERIFIED — DO NOT DISTRIBUTE` to `CLEARED`, fill in all of:

1. **Source** — where the file came from, specifically enough to check.
2. **Creator / rights holder** — the person or entity that can grant permission.
3. **Licence or permission** — the licence name, or a written permission held by the project
   owner. "The owner says it is fine" is an assertion; a licence or a message granting
   distribution is evidence.
4. **Required attribution** — the exact credit the licence or the artist requires.

Then update the row and run `python3 scripts/check-artwork-provenance.py`.
