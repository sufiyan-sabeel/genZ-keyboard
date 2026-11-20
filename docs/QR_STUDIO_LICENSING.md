# QR Studio — licensing decision

## Reference project

`nylxar/curium` — **GNU Affero General Public License v3.0**.

Verified by reading the repository's `LICENSE` file, which is the AGPL-3.0 text (version 3,
19 November 2007).

## Why no Curium code can be used

GenZ Keyboard is distributed under the MIT licence. AGPL-3.0 is a strong copyleft licence: any work
"based on" the Program must be licensed as a whole under AGPL-3.0 when conveyed. Importing Curium
source — even a single rendering routine — would relicense the entire application, including the
keyboard engine, and would conflict with:

- the project's existing MIT licence and its copyright notices;
- the F-Droid submission, which records the app's licence metadata;
- the existing release history, which cannot be retroactively relicensed.

AGPL §13 would also extend obligations to network use.

## Decision

**Independent implementation. No Curium source, assets, artwork, branding, string resources or
generated files are copied.** Curium is used only as a *functional* reference: the list of payload
types, the general idea of style variants, and the feature set are facts about what the software does,
not copyrightable expression.

QR encoding itself is not taken from Curium. It is either implemented here or provided by a
permissively licensed library.

## Third-party components

| Component | Licence | Why acceptable |
|---|---|---|
| ZXing core (QR encoding/decoding) | Apache-2.0 | permissive, MIT-compatible, no network use |

Any dependency added for QR Studio must be Apache-2.0, MIT or BSD. No GPL or AGPL component may be
introduced into the app.

## What this means for the feature list

The requested pixel/eye/pupil style names (Sharp, Liquid, Hexagon, Heart, …) are implemented as
**this project's own drawing code** over a standard QR module matrix. Style names describe shapes, not
Curium's implementation, and none of its drawing code is reproduced.

## Status

- [x] Reference licence read and identified
- [x] Compatibility analysis
- [x] Decision recorded before any implementation
- [ ] Any dependency added must be recorded in `THIRD_PARTY_NOTICES.md`
