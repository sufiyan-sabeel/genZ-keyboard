#!/usr/bin/env python3
"""Artwork provenance gate.

Fails when an image can reach a build without a recorded rights position.

The rules come from docs/artwork-provenance.md:

  1. every image under app/src/standard/assets/anime/ and docs/pending-themes/artwork/ has a row
     in the inventory;
  2. every row in the inventory names a file that exists (no dead rows, no typos);
  3. no artwork is present in the fdroid flavour, which is what lets that build ship while the
     rights question is open;
  4. the inventory still states a status for every row, and the status is one of the two the
     document defines.

This does not judge whether a licence is real. It makes the state of the evidence impossible to
forget, which is the part a script can do.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INVENTORY = ROOT / "docs" / "artwork-provenance.md"
SHIPPED_DIR = ROOT / "app" / "src" / "standard" / "assets" / "anime"
PENDING_DIR = ROOT / "docs" / "pending-themes" / "artwork"
FDROID_DIRS = [
    ROOT / "app" / "src" / "fdroid" / "assets",
    ROOT / "app" / "src" / "fdroid",
]
IMAGE_SUFFIXES = {".webp", ".png", ".jpg", ".jpeg", ".gif"}

STATUS_VERIFIED = "CLEARED"
STATUS_UNVERIFIED = "UNVERIFIED \u2014 DO NOT DISTRIBUTE"
VALID_STATUSES = {STATUS_VERIFIED, STATUS_UNVERIFIED}

errors = []
notes = []


def die(msg):
    errors.append(msg)


def images_in(directory):
    if not directory.is_dir():
        return {}
    return {p.name: p for p in sorted(directory.iterdir())
            if p.is_file() and p.suffix.lower() in IMAGE_SUFFIXES}


def parse_inventory(text):
    """Return {filename: status} from the markdown tables.

    A row is a table line whose second cell is a backticked filename and whose last cell is a
    status. Cell order is fixed by the document, so the last cell is read as the status.
    """
    rows = {}
    for line in text.splitlines():
        line = line.strip()
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip("|").split("|")]
        if len(cells) < 4:
            continue
        m = re.match(r"^`([^`]+\.(?:webp|png|jpg|jpeg|gif))`$", cells[1])
        if not m:
            continue
        status = cells[-1].strip().strip("`").strip()
        rows[m.group(1)] = status
    return rows


def main():
    if not INVENTORY.is_file():
        print("::error::docs/artwork-provenance.md is missing", file=sys.stderr)
        return 1

    rows = parse_inventory(INVENTORY.read_text(encoding="utf-8"))
    if not rows:
        die("the inventory contains no asset rows")

    on_disk = {}
    for d in (SHIPPED_DIR, PENDING_DIR):
        on_disk.update(images_in(d))

    # 1. every file on disk is recorded
    for name in sorted(set(on_disk) - set(rows)):
        die(f"artwork file has no row in the inventory: {on_disk[name].relative_to(ROOT)}")

    # 2. every row names a real file
    for name in sorted(set(rows) - set(on_disk)):
        die(f"inventory row names a file that does not exist: {name}")

    # 3. no artwork in the fdroid flavour
    for d in FDROID_DIRS:
        if not d.is_dir():
            continue
        for name, path in images_in(d).items():
            die(f"artwork must not ship in the fdroid flavour: {path.relative_to(ROOT)}")

    # 4. every row states a valid status
    for name, status in sorted(rows.items()):
        if status not in VALID_STATUSES:
            die(f"row for {name} has an unrecognised status: {status!r}")

    unverified = [n for n, s in rows.items() if s == STATUS_UNVERIFIED]
    cleared = [n for n, s in rows.items() if s == STATUS_VERIFIED]
    shipped_unverified = [n for n in unverified if n in images_in(SHIPPED_DIR)]

    print(f"artwork: {len(rows)} rows, {len(on_disk)} files on disk")
    print(f"  cleared: {len(cleared)}")
    print(f"  unverified: {len(unverified)}")
    if shipped_unverified:
        notes.append(
            f"{len(shipped_unverified)} unverified image(s) still ship in the standard flavour: "
            + ", ".join(sorted(shipped_unverified))
        )

    for n in notes:
        print(f"::warning::{n}")

    if errors:
        for e in errors:
            print(f"::error::{e}", file=sys.stderr)
        return 1

    print("artwork provenance is consistent with the inventory")
    return 0


if __name__ == "__main__":
    sys.exit(main())
