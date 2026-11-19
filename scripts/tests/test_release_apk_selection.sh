#!/usr/bin/env bash
# Tests for scripts/select-release-apk.sh.
#
# The release job verifies one APK and uploads one APK. Before this script they were selected by
# two different rules, so "the file that was checked" and "the file that was published" could
# differ. These cases pin the selector's contract, including the ambiguity that made them differ.
#
# Run: bash scripts/tests/test_release_apk_selection.sh

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SELECT="$ROOT/scripts/select-release-apk.sh"

PASS=0
FAIL=0
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() { printf 'FAIL %s\n' "$1" >&2; FAIL=$((FAIL + 1)); }
ok()   { printf 'ok   %s\n' "$1"; PASS=$((PASS + 1)); }

# run <dir> -> sets OUT, ERR, CODE
run() {
  OUT="$(bash "$SELECT" "$1" 2>"$TMP/err")"
  CODE=$?
  ERR="$(cat "$TMP/err")"
}

newdir() {
  local d="$TMP/$1"
  mkdir -p "$d"
  printf '%s\n' "$d"
}

# ---------------------------------------------------------------- one signed APK: the normal case

d="$(newdir single)"
printf 'PK\x03\x04 signed' > "$d/app-standard-release.apk"
run "$d"
if [ "$CODE" -eq 0 ] && [ "$OUT" = "$d/app-standard-release.apk" ]; then
  ok "one signed APK is returned as an absolute path"
else
  fail "one signed APK: code=$CODE out=$OUT err=$ERR"
fi

# ---------------------------------------------------------------- the audit's ambiguity

d="$(newdir ambiguous)"
printf 'PK\x03\x04 signed' > "$d/app-standard-release.apk"
printf 'PK\x03\x04 unsigned' > "$d/app-standard-release-unsigned.apk"
run "$d"
if [ "$CODE" -ne 0 ]; then
  ok "two APKs are refused rather than guessed between"
else
  fail "two APKs should have been refused, got: $OUT"
fi
if printf '%s' "$ERR" | grep -q "found 2"; then
  ok "the refusal names the candidate count"
else
  fail "refusal did not state the count: $ERR"
fi
if printf '%s' "$ERR" | grep -q -- "-unsigned.apk"; then
  ok "the refusal lists the candidates"
else
  fail "refusal did not list candidates: $ERR"
fi

# The specific failure the audit described: alphabetical order would have put the unsigned file
# first, so `ls | head -1` would have uploaded it while the checks verified the signed one.
d="$(newdir sortorder)"
printf 'x' > "$d/app-standard-release.apk"
printf 'x' > "$d/app-standard-release-unsigned.apk"
first="$(cd "$d" && ls *.apk | head -1)"
if [ "$first" = "app-standard-release-unsigned.apk" ]; then
  ok "the unsigned APK does sort first, which is why selection had to be centralised"
else
  fail "expected the unsigned APK to sort first, got $first"
fi

# ---------------------------------------------------------------- degenerate cases

d="$(newdir empty)"
run "$d"
if [ "$CODE" -ne 0 ] && printf '%s' "$ERR" | grep -q "no APK was produced"; then
  ok "an empty output directory is refused"
else
  fail "empty dir: code=$CODE err=$ERR"
fi

d="$(newdir unsigned-only)"
printf 'x' > "$d/app-standard-release-unsigned.apk"
run "$d"
if [ "$CODE" -ne 0 ] && printf '%s' "$ERR" | grep -q "unsigned"; then
  ok "a lone unsigned APK is refused"
else
  fail "unsigned only: code=$CODE out=$OUT err=$ERR"
fi

d="$(newdir zero-length)"
: > "$d/app-standard-release.apk"
run "$d"
if [ "$CODE" -ne 0 ] && printf '%s' "$ERR" | grep -q "empty"; then
  ok "a zero-length APK is refused"
else
  fail "zero length: code=$CODE out=$OUT err=$ERR"
fi

run "$TMP/does-not-exist"
if [ "$CODE" -ne 0 ] && printf '%s' "$ERR" | grep -q "does not exist"; then
  ok "a missing directory is refused"
else
  fail "missing dir: code=$CODE err=$ERR"
fi

OUT="$(bash "$SELECT" 2>"$TMP/err")"; CODE=$?
if [ "$CODE" -eq 2 ]; then
  ok "no argument is a usage error (exit 2)"
else
  fail "no argument: code=$CODE"
fi

# ---------------------------------------------------------------- non-APK files are ignored

d="$(newdir mixed)"
printf 'x' > "$d/app-standard-release.apk"
printf '{}' > "$d/output-metadata.json"
printf 'x' > "$d/notes.txt"
run "$d"
if [ "$CODE" -eq 0 ] && [ "$OUT" = "$d/app-standard-release.apk" ]; then
  ok "output-metadata.json and other files are not treated as candidates"
else
  fail "mixed dir: code=$CODE out=$OUT err=$ERR"
fi

# ---------------------------------------------------------------- identity, which is the point

# A caller must be able to compare "the path that was checked" with "the path that was uploaded".
d="$(newdir identity)"
printf 'PK\x03\x04 payload' > "$d/app-standard-release.apk"
run "$d"
SELECTED="$OUT"
cp "$SELECTED" "$TMP/uploaded.apk"
if [ "$(sha256sum "$SELECTED" | cut -d' ' -f1)" = "$(sha256sum "$TMP/uploaded.apk" | cut -d' ' -f1)" ]; then
  ok "the selected path and the copied upload are byte-identical"
else
  fail "selected and uploaded copies differ"
fi

printf '\n%d passed, %d failed\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
