# Third-Party Artwork Notices

This file records the provenance of every image bundled with, or considered for, the GenZ
Keyboard theme collection. It is the authoritative record for artwork rights.

**Nothing here is a licence grant.** Where a licence is not documented, none is implied. Where a
work carries another artist's mark, it is treated as that artist's work until they say otherwise.

---

## Status summary

| # | Theme | Wallpaper asset | In distributed APK | Rights status |
|---|---|---|---|---|
| 1 | Rainy Tokyo | `art_rainytokyo.webp` | **yes** (standard flavour) | no third-party mark; supplied by project owner |
| 2 | Pastel Garden | `art_pastel.webp` | **yes** | no third-party mark; supplied by project owner |
| 3 | Anime Sunset | `art_sunset.webp` | **yes** | no third-party mark; supplied by project owner |
| 4 | Moonlit Cabin | `art_cabin.webp` | **yes** | no third-party mark; supplied by project owner |
| 5 | Snowfall Pines | `art_snowfall.webp` | **yes** | no third-party mark; supplied by project owner |
| 6 | Blue Hour Road | `art_blueroad.webp` | **yes** | no third-party mark; supplied by project owner |
| 7 | Midnight Windmill | `art_windmill.webp` | **no** | signed **`SNIPER`** — permission not obtained |
| 8 | Ink Samurai | `art_samurai.webp` | **no** | signed **`Dream Render`** — permission not obtained |
| 9 | Sakura Night | `art_sakura.webp` | **no** | signed **`AFERA XV`** — permission not obtained |

---

## 1–6. Cleared for distribution

**Source:** supplied to this project by the repository owner for use as keyboard wallpapers.
**Creator:** not identified in the image; no third-party signature present.
**Licence:** no separate licence document was supplied. Distribution in the `standard` flavour is
on the repository owner's instruction, and the owner is recorded as asserting the right to do so.
**Action for the owner:** keep a record of where these six images came from. If any turns out to be
third-party work, move it to the pending list below.

These six ship in `app/src/standard/assets/anime/` and appear in the Theme Studio gallery. The
`fdroid` flavour ships **no** artwork of any kind, so none of this reaches that build.

## 7. Midnight Windmill — `SNIPER`

**Mark:** the text `SNIPER` is visible in the lower-right corner of the image.
**Meaning:** a signature or watermark, i.e. strong evidence the work belongs to another artist.
**Licence:** unknown. No permission has been obtained or recorded.
**Status:** **withheld.** The theme definition and artwork are retained in `docs/pending-themes/`
and are deliberately excluded from the distributed APK.

## 8. Ink Samurai — `Dream Render`

**Mark:** the text `Dream Render` is visible in the lower-left area, alongside two red seal stamps.
**Meaning:** a signature or watermark. The seal stamps are consistent with an artist's mark.
**Licence:** unknown. No permission obtained.
**Status:** **withheld**, retained in `docs/pending-themes/`.

## 9. Sakura Night — `AFERA XV`

**Mark:** the text `AFERA XV` is visible at the bottom edge, between decorative rules.
**Meaning:** a signature or watermark.
**Licence:** unknown. No permission obtained.
**Status:** **withheld**, retained in `docs/pending-themes/`.

---

## What would let 7–9 ship

One of the following, recorded here with a date and a name:

1. **Written permission** from the artist named on the image, stating that redistribution of the
   artwork inside this application is allowed.
2. **Replacement artwork** for that theme, with its own provenance entry, that is either original
   to this project or under a licence permitting redistribution (for example CC0, CC BY, or an
   explicit commercial-use grant).
3. **Removal of the artwork**, keeping the theme as a palette-only preset with no wallpaper.

Until one of those happens, the three themes remain in `docs/pending-themes/`: present in the
repository so the design is not lost, absent from every build.

## A note on attribution

Attribution is not a licence. Crediting `SNIPER`, `Dream Render` or `AFERA XV` in this file does
**not** make redistributing their work lawful, and no such claim is made here. Attribution is
recorded so the correct artist can be approached and so that any future use is correctly credited.

---

## Emoji artwork

Only families whose redistribution rights have been verified are offered. Apple, Samsung One UI,
Segoe UI, WhatsApp, HarmonyOS, Flyme and JoyPixels are excluded: their artwork is proprietary and the
repositories that repackage it (for example `lzcapp/Emoji-PNG`, which carries no licence statement)
grant no redistribution rights.

| Family | Upstream | Licence | Status |
|---|---|---|---|
| Twemoji | `jdecked/twemoji` (formerly Twitter) | CC-BY 4.0 | offered — attribution required |
| Noto Emoji | `googlefonts/noto-emoji` | Apache-2.0 | offered |
| Blobmoji | `C1710/blobmoji` | Apache-2.0 | offered |
| Fluent Emoji | Microsoft `fluentui-emoji` | MIT | offered |
| OpenMoji | `hfg-gmuend/openmoji` | CC BY-SA 4.0 | **deferred** — share-alike obligations under review |

No Magisk module, root access, system font replacement or device modification is used. `Magisk-iOS-Emoji`
was inspected only to establish that Apple's artwork cannot be redistributed; none of its scripts or
assets are included.
