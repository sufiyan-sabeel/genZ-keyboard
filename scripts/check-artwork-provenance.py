#!/usr/bin/env python3
"""Artwork provenance gate.

Three modes, because a development build and a published release must not be treated the same:

  default      Consistency check. Every image has a row, every row names a real file, no artwork
               sits in the fdroid flavour, every status is valid. Unverified artwork that is still
               shipped produces a warning and exit code 0, so day-to-day work is not blocked.

  --release    Release licensing gate. Everything the default mode checks, plus: an image that is
               not CLEARED must not be present in a distributable flavour. Any unverified asset
               in the standard source set fails the gate. No override exists, by design — the way
               to pass is to record the rights, not to silence the check.

  --apk PATH   Inspect a built APK. Reports every image inside it that is artwork - anything
               under assets/anime/, and anything whose filename matches an inventory row anywhere
               in the archive - and fails when one of them is unverified or absent from the
               inventory. This is what makes the gate about the artifact that would actually be
               published, not only about the source tree it was built from.

  --report     Downgrade --apk findings to warnings. For steps that describe a build rather than
               gate it, such as the CI job reporting what the debug APK bundles. Inventory
               inconsistencies still fail: those are defects in the record, not in the artifact.

The rules come from docs/artwork-provenance.md:

  1. every image under app/src/standard/assets/anime/ and docs/pending-themes/artwork/ has a row;
  2. every row names a file that exists (no dead rows, no typos);
  3. no artwork is present in the fdroid flavour;
  4. every row states one of the two defined statuses.

The script does not judge whether a licence is real. It makes the state of the evidence impossible
to forget, and stops a release that contradicts it.
"""

import argparse
import re
import sys
import zipfile
from pathlib import Path

STATUS_VERIFIED = "CLEARED"
STATUS_UNVERIFIED = "UNVERIFIED \u2014 DO NOT DISTRIBUTE"
VALID_STATUSES = {STATUS_VERIFIED, STATUS_UNVERIFIED}
IMAGE_SUFFIXES = {".webp", ".png", ".jpg", ".jpeg", ".gif"}
APK_ASSET_PREFIX = "assets/anime/"


def repo_paths(root):
    return {
        "inventory": root / "docs" / "artwork-provenance.md",
        "shipped": root / "app" / "src" / "standard" / "assets" / "anime",
        "pending": root / "docs" / "pending-themes" / "artwork",
        # Artwork must never reach the fdroid flavour. The directory usually does not exist at
        # all; if someone creates it, this is what notices.
        "fdroid": [root / "app" / "src" / "fdroid" / "assets", root / "app" / "src" / "fdroid"],
    }


def add_error(errors, message):
    """Append an error once.

    The fdroid scan roots overlap (app/src/fdroid/assets is inside app/src/fdroid) and two
    different checks can both notice the same file, which produced the same line up to four times.
    One file, one error.
    """
    if message not in errors:
        errors.append(message)


def images_in(directory):
    if not directory.is_dir():
        return {}
    return {p.name: p for p in sorted(directory.iterdir())
            if p.is_file() and p.suffix.lower() in IMAGE_SUFFIXES}


def images_in_tree(directory):
    """Every image under a directory, recursively.

    The fdroid check needs this: artwork placed there would arrive as
    app/src/fdroid/assets/anime/name.webp, one level below the directory that is scanned.
    """
    if not directory.is_dir():
        return {}
    return {p.name: p for p in sorted(directory.rglob("*"))
            if p.is_file() and p.suffix.lower() in IMAGE_SUFFIXES}


def parse_inventory(text):
    """Return {filename: status} from the markdown tables.

    A row is a table line whose second cell is a backticked filename and whose last cell is a
    status. The column order is fixed by the document, so the last cell is read as the status.
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
        rows[m.group(1)] = cells[-1].strip().strip("`").strip()
    return rows


def check_inventory(paths, errors):
    """Mode-independent consistency: rows and files must describe the same set."""
    inventory = paths["inventory"]
    if not inventory.is_file():
        add_error(errors, "docs/artwork-provenance.md is missing")
        return {}, {}

    rows = parse_inventory(inventory.read_text(encoding="utf-8"))
    if not rows:
        add_error(errors, "the inventory contains no asset rows")
        return {}, {}

    # The fdroid tree is included here so that artwork placed there produces the specific
    # "must not ship in the fdroid flavour" error rather than a confusing dead-row error.
    on_disk = {}
    for key in ("shipped", "pending"):
        on_disk.update(images_in(paths[key]))
    for directory in paths["fdroid"]:
        on_disk.update(images_in_tree(directory))

    for name in sorted(set(on_disk) - set(rows)):
        add_error(errors, f"artwork file has no row in the inventory: {name}")
    for name in sorted(set(rows) - set(on_disk)):
        add_error(errors, f"inventory row names a file that does not exist: {name}")
    for name, status in sorted(rows.items()):
        if status not in VALID_STATUSES:
            add_error(errors, f"row for {name} has an unrecognised status: {status!r}")

    for directory in paths["fdroid"]:
        for name, path in images_in_tree(directory).items():
            add_error(errors, f"artwork must not ship in the fdroid flavour: {path}")

    return rows, on_disk


def check_release(paths, rows, errors):
    """Release gate: nothing unverified may sit in a distributable flavour."""
    shipped = images_in(paths["shipped"])
    unverified = sorted(n for n in shipped if rows.get(n) != STATUS_VERIFIED)
    if unverified:
        add_error(errors,
            "release blocked: %d image(s) in the standard flavour are not CLEARED: %s"
            % (len(unverified), ", ".join(unverified)))

    fdroid = {}
    for directory in paths["fdroid"]:
        fdroid.update(images_in_tree(directory))
    if fdroid:
        add_error(errors,
            "release blocked: the fdroid flavour must ship no artwork, found: %s"
            % ", ".join(sorted(fdroid)))

    return len(shipped), len(unverified)


def check_apk(apk_path, rows, errors):
    """Inspect the artifact that would be published, not just the tree it came from.

    Two independent rules, because either one alone can be fooled:

      - anything under assets/anime/ is artwork, whatever it is called;
      - anything anywhere in the archive whose filename matches an inventory row is that artwork,
        even if it was moved to another directory, renamed into a resource folder, or embedded in
        a nested archive-free path.

    A file caught by the second rule but not the first is exactly the case the audit asked about:
    artwork that reached the APK by a route other than the assets/anime/ folder.
    """
    if not apk_path.is_file():
        add_error(errors, f"APK not found: {apk_path}")
        return 0, 0

    try:
        with zipfile.ZipFile(apk_path) as z:
            names = z.namelist()
    except (zipfile.BadZipFile, OSError):
        add_error(errors, f"not a readable APK/zip: {apk_path}")
        return 0, 0

    images = [n for n in names if Path(n).suffix.lower() in IMAGE_SUFFIXES]
    under_anime = [n for n in images if n.startswith(APK_ASSET_PREFIX)]
    known_by_name = [n for n in images if Path(n).name in rows]
    bundled = sorted(set(under_anime) | set(known_by_name))

    if not bundled:
        return 0, 0

    unverified, unknown, misplaced = [], [], []
    for entry in bundled:
        name = Path(entry).name
        if name not in rows:
            unknown.append(entry)
        else:
            if rows[name] != STATUS_VERIFIED:
                unverified.append(name)
            if not entry.startswith(APK_ASSET_PREFIX):
                misplaced.append(entry)

    if unknown:
        add_error(errors,
            "release blocked: the APK contains artwork with no inventory row: %s"
            % ", ".join(sorted(set(unknown))))
    if unverified:
        add_error(errors,
            "release blocked: the APK contains %d unverified image(s): %s"
            % (len(unverified), ", ".join(sorted(set(unverified)))))
    if misplaced:
        add_error(errors,
            "release blocked: the APK contains inventory artwork outside %s: %s"
            % (APK_ASSET_PREFIX, ", ".join(sorted(set(misplaced)))))

    return len(bundled), len(unverified)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Artwork provenance gate")
    parser.add_argument("--release", action="store_true",
                        help="release licensing gate: fail when unverified artwork would ship")
    parser.add_argument("--apk", metavar="PATH",
                        help="inspect a built APK instead of (or as well as) the source tree")
    parser.add_argument("--report", action="store_true",
                        help="with --apk, report findings as warnings instead of failing")
    parser.add_argument("--root", metavar="PATH", default=None,
                        help="repository root (default: the parent of this script)")
    args = parser.parse_args(argv)

    root = Path(args.root).resolve() if args.root else Path(__file__).resolve().parent.parent
    paths = repo_paths(root)

    errors = []
    rows, on_disk = check_inventory(paths, errors)

    unverified_total = sorted(n for n, s in rows.items() if s == STATUS_UNVERIFIED)
    cleared_total = sorted(n for n, s in rows.items() if s == STATUS_VERIFIED)
    shipped = images_in(paths["shipped"])
    shipped_unverified = [n for n in unverified_total if n in shipped]

    mode = "release" if args.release else "consistency"
    print(f"artwork gate [{mode}]: {len(rows)} rows, {len(on_disk)} files on disk, "
          f"{len(cleared_total)} cleared, {len(unverified_total)} unverified")

    if args.release:
        n_shipped, n_unverified = check_release(paths, rows, errors)
        print(f"  standard flavour: {n_shipped} image(s), {n_unverified} not cleared")

    if args.apk:
        apk_errors = []
        n_bundled, n_bad = check_apk(Path(args.apk), rows, apk_errors)
        print(f"  apk {args.apk}: {n_bundled} bundled image(s), {n_bad} not cleared")
        if args.report:
            # Describing a build, not gating it: say what is in there and let the build continue.
            for e in apk_errors:
                print(f"::warning::{e}")
        else:
            for e in apk_errors:
                add_error(errors, e)

    # A warning only in consistency mode: it is a fact about the project, not a build defect, and
    # blocking daily work on it would just teach people to skip the check.
    if not args.release and shipped_unverified:
        print("::warning::%d unverified image(s) still ship in the standard flavour: %s"
              % (len(shipped_unverified), ", ".join(sorted(shipped_unverified))))

    if errors:
        for e in errors:
            print(f"::error::{e}", file=sys.stderr)
        return 1

    print("artwork provenance is consistent with the inventory"
          + (" and the release gate passes" if args.release else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
