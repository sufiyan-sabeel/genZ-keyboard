#!/usr/bin/env python3
"""Tests for scripts/check-artwork-provenance.py.

The point of these is the release gate, so most cases are about a release being refused rather
than a warning being printed. Each test builds a throwaway repository layout and runs the real
command line against it, so the behaviour under test is the behaviour CI gets.

Run:  python3 -m unittest discover -s scripts/tests -v
"""

import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent.parent / "check-artwork-provenance.py"

CLEARED = "CLEARED"
UNVERIFIED = "UNVERIFIED \u2014 DO NOT DISTRIBUTE"


def inventory_md(rows):
    """Render an inventory table with the same column layout as the real document."""
    head = ("| # | File | Theme | MD5 (12) | Source | Creator / rights holder | "
            "Licence or permission | Required attribution | Rights status |\n"
            "| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n")
    body = ""
    for i, (name, status) in enumerate(rows.items(), 1):
        body += f"| {i} | `{name}` | T | `000000000000` | S | C | L | A | `{status}` |\n"
    return head + body


def make_repo(tmp, shipped=None, pending=None, fdroid=None, rows=None):
    """Create a minimal repository layout and return its root."""
    root = Path(tmp)
    (root / "docs").mkdir(parents=True, exist_ok=True)

    shipped = shipped or {}
    pending = pending or {}
    fdroid = fdroid or {}

    def write(directory, files):
        if not files:
            return
        d = root / directory
        d.mkdir(parents=True, exist_ok=True)
        for name in files:
            (d / name).write_bytes(b"RIFF0000WEBP")

    write("app/src/standard/assets/anime", shipped)
    write("docs/pending-themes/artwork", pending)
    write("app/src/fdroid/assets/anime", fdroid)

    if rows is None:
        rows = {}
        for name in shipped:
            rows[name] = UNVERIFIED
        for name in pending:
            rows[name] = UNVERIFIED
        for name in fdroid:
            rows[name] = CLEARED

    (root / "docs" / "artwork-provenance.md").write_text(inventory_md(rows), encoding="utf-8")
    return root


def make_apk(tmp, entries):
    """Build a zip that looks like an APK with the given asset entries."""
    path = Path(tmp) / "app.apk"
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("AndroidManifest.xml", b"\x03\x00")
        z.writestr("classes.dex", b"dex\n035\x00")
        for name in entries:
            z.writestr(f"assets/anime/{name}", b"RIFF0000WEBP")
    return path


def run(*args):
    return subprocess.run([sys.executable, str(SCRIPT), *map(str, args)],
                          capture_output=True, text=True)


class ConsistencyMode(unittest.TestCase):
    """Day-to-day behaviour: report, do not block."""

    def test_unverified_shipped_artwork_only_warns(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None})
            r = run("--root", root)
            self.assertEqual(r.returncode, 0, r.stderr)
            self.assertIn("::warning::", r.stdout)
            self.assertIn("ink.webp", r.stdout)

    def test_a_file_with_no_row_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None}, rows={"other.webp": UNVERIFIED})
            r = run("--root", root)
            self.assertEqual(r.returncode, 1)
            self.assertIn("no row in the inventory", r.stderr)

    def test_a_row_with_no_file_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None},
                             rows={"ink.webp": UNVERIFIED, "ghost.webp": CLEARED})
            r = run("--root", root)
            self.assertEqual(r.returncode, 1)
            self.assertIn("does not exist", r.stderr)

    def test_a_missing_inventory_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "docs").mkdir()
            r = run("--root", root)
            self.assertEqual(r.returncode, 1)
            self.assertIn("is missing", r.stderr)


class ReleaseGate(unittest.TestCase):
    """The gate the pull request adds: a distributable release cannot carry unverified art."""

    def test_unverified_standard_artwork_blocks_the_release(self):
        # the headline case: exactly today's state of the repository
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None, "rose.webp": None})
            r = run("--root", root, "--release")
            self.assertEqual(r.returncode, 1, "a release with unverified artwork must fail")
            self.assertIn("release blocked", r.stderr)
            self.assertIn("ink.webp", r.stderr)
            self.assertIn("rose.webp", r.stderr)

    def test_one_unverified_asset_is_enough_to_block(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None, "rose.webp": None},
                             rows={"ink.webp": CLEARED, "rose.webp": UNVERIFIED})
            r = run("--root", root, "--release")
            self.assertEqual(r.returncode, 1)
            self.assertIn("rose.webp", r.stderr)
            self.assertNotIn("ink.webp", r.stderr)

    def test_a_fully_cleared_standard_set_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None, "rose.webp": None},
                             rows={"ink.webp": CLEARED, "rose.webp": CLEARED})
            r = run("--root", root, "--release")
            self.assertEqual(r.returncode, 0, r.stderr)
            self.assertIn("release gate passes", r.stdout)

    def test_withheld_pending_artwork_does_not_block(self):
        # docs/pending-themes is not distributed, so unverified files there are expected
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None},
                             pending={"art_sakura.webp": None},
                             rows={"ink.webp": CLEARED, "art_sakura.webp": UNVERIFIED})
            r = run("--root", root, "--release")
            self.assertEqual(r.returncode, 0, r.stderr)

    def test_artwork_in_the_fdroid_flavour_blocks_the_release(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, fdroid={"leak.webp": None},
                             rows={"leak.webp": CLEARED})
            r = run("--root", root, "--release")
            self.assertEqual(r.returncode, 1)
            self.assertIn("fdroid flavour must ship no artwork", r.stderr)

    def test_the_gate_has_no_override(self):
        # a flag that silences the gate would be the bypass the review asked to prevent
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None})
            r = run("--root", root, "--release", "--allow-unverified")
            self.assertNotEqual(r.returncode, 0)
            self.assertIn("unrecognized arguments", r.stderr)


class ApkInspection(unittest.TestCase):
    """The artifact check: what would actually be uploaded."""

    def test_an_apk_bundling_unverified_artwork_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None})
            apk = make_apk(tmp, ["ink.webp"])
            r = run("--root", root, "--apk", apk)
            self.assertEqual(r.returncode, 1)
            self.assertIn("APK contains", r.stderr)
            self.assertIn("ink.webp", r.stderr)

    def test_an_apk_with_no_artwork_passes(self):
        # the fdroid flavour: same keyboard, no illustrations
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None})
            apk = make_apk(tmp, [])
            r = run("--root", root, "--apk", apk)
            self.assertEqual(r.returncode, 0, r.stderr)
            self.assertIn("0 bundled image", r.stdout)

    def test_an_apk_with_an_unknown_image_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None})
            apk = make_apk(tmp, ["smuggled.webp"])
            r = run("--root", root, "--apk", apk)
            self.assertEqual(r.returncode, 1)
            self.assertIn("no inventory row", r.stderr)

    def test_an_apk_of_cleared_artwork_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None}, rows={"ink.webp": CLEARED})
            apk = make_apk(tmp, ["ink.webp"])
            r = run("--root", root, "--apk", apk)
            self.assertEqual(r.returncode, 0, r.stderr)

    def test_release_and_apk_can_be_combined(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None})
            apk = make_apk(tmp, ["ink.webp"])
            r = run("--root", root, "--release", "--apk", apk)
            self.assertEqual(r.returncode, 1)
            self.assertIn("standard flavour", r.stdout)
            self.assertIn("apk", r.stdout)

    def test_a_missing_apk_is_an_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None})
            r = run("--root", root, "--apk", Path(tmp) / "absent.apk")
            self.assertEqual(r.returncode, 1)
            self.assertIn("APK not found", r.stderr)


class InventoryValidation(unittest.TestCase):
    """Cases the first audit found untested, though both paths already behaved correctly."""

    def test_an_invalid_status_value_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None}, rows={"ink.webp": "PROBABLY FINE"})
            r = run("--root", root)
            self.assertEqual(r.returncode, 1)
            self.assertIn("unrecognised status", r.stderr)

    def test_an_invalid_status_blocks_the_release_too(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None}, rows={"ink.webp": "MOSTLY CLEARED"})
            r = run("--root", root, "--release")
            self.assertEqual(r.returncode, 1)
            self.assertIn("unrecognised status", r.stderr)

    def test_a_status_with_a_typo_is_not_treated_as_cleared(self):
        # "cleared" lowercase must not pass: only the exact value counts
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None}, rows={"ink.webp": "cleared"})
            r = run("--root", root, "--release")
            self.assertEqual(r.returncode, 1)


class NestedDirectories(unittest.TestCase):
    """Artwork three levels down is still artwork."""

    def test_deeply_nested_fdroid_artwork_is_caught(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, rows={})
            deep = root / "app" / "src" / "fdroid" / "assets" / "anime" / "deep" / "deeper"
            deep.mkdir(parents=True)
            (deep / "leak.webp").write_bytes(b"RIFF0000WEBP")
            # record it so the only complaint is that it is in the fdroid flavour
            (root / "docs" / "artwork-provenance.md").write_text(
                inventory_md({"leak.webp": CLEARED}), encoding="utf-8")
            r = run("--root", root)
            self.assertEqual(r.returncode, 1)
            self.assertIn("must not ship in the fdroid flavour", r.stderr)
            self.assertIn("deep/deeper/leak.webp", r.stderr)

    def test_a_nested_file_is_reported_once_not_four_times(self):
        # the scan roots overlap, so without de-duplication the same file is named repeatedly
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, fdroid={"leak.webp": None}, rows={"leak.webp": CLEARED})
            r = run("--root", root, "--release")
            self.assertEqual(r.returncode, 1)
            self.assertEqual(r.stderr.count("must not ship in the fdroid flavour"), 1,
                             "the same artwork should produce one error line, got:\n" + r.stderr)


class ApkLocations(unittest.TestCase):
    """The APK scan covers more than assets/anime/."""

    def test_artwork_outside_assets_anime_is_caught(self):
        # an inventory image moved into a resource folder: not under assets/anime/, still artwork
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None})
            apk = Path(tmp) / "moved.apk"
            with zipfile.ZipFile(apk, "w") as z:
                z.writestr("AndroidManifest.xml", b"\x03\x00")
                z.writestr("res/drawable/ink.webp", b"RIFF0000WEBP")
            r = run("--root", root, "--apk", apk)
            self.assertEqual(r.returncode, 1)
            self.assertIn("outside assets/anime/", r.stderr)

    def test_unrelated_images_outside_assets_anime_are_ignored(self):
        # emoji and launcher assets are not artwork of unresolved rights and must not trip the gate
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None})
            apk = Path(tmp) / "emoji.apk"
            with zipfile.ZipFile(apk, "w") as z:
                z.writestr("AndroidManifest.xml", b"\x03\x00")
                z.writestr("assets/emoji/twemoji/1f600.webp", b"RIFF0000WEBP")
                z.writestr("res/drawable-hdpi/ic_launcher.png", b"\x89PNG")
            r = run("--root", root, "--apk", apk)
            self.assertEqual(r.returncode, 0, r.stderr)
            self.assertIn("0 bundled image", r.stdout)

    def test_an_unknown_image_under_assets_anime_is_caught(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_repo(tmp, shipped={"ink.webp": None})
            apk = Path(tmp) / "extra.apk"
            with zipfile.ZipFile(apk, "w") as z:
                z.writestr("AndroidManifest.xml", b"\x03\x00")
                z.writestr("assets/anime/bonus.webp", b"RIFF0000WEBP")
            r = run("--root", root, "--apk", apk)
            self.assertEqual(r.returncode, 1)
            self.assertIn("no inventory row", r.stderr)


if __name__ == "__main__":
    unittest.main()
