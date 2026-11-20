#!/usr/bin/env bash
# Select the one release APK that will be published.
#
# The audit found the release job selecting its APK three times with three different rules:
# `ls | head -1` for the copy that got uploaded, and `ls | grep -v unsigned | head -1` for the
# artwork and certificate checks. With two APKs in the output directory those disagree, and the
# file that was verified is not the file that was published.
#
# This is now the only place an APK is chosen. It prints one absolute path and nothing else, so a
# caller can do:
#
#   APK=$(bash scripts/select-release-apk.sh app/build/outputs/apk/standard/release)
#
# and every later step works on that path. It refuses to guess:
#
#   - no APK at all            -> exit 1
#   - more than one APK        -> exit 1, all candidates listed
#   - the only APK is unsigned -> exit 1, because an unsigned APK cannot be installed by anyone
#
# Exit codes: 0 one signed APK, 1 anything else, 2 bad usage.

set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: select-release-apk.sh <apk-output-dir>" >&2
  exit 2
fi

DIR="$1"

if [ ! -d "$DIR" ]; then
  echo "::error::APK output directory does not exist: $DIR" >&2
  exit 1
fi

# nullglob so an empty directory yields an empty array instead of the literal pattern
shopt -s nullglob
CANDIDATES=("$DIR"/*.apk)
shopt -u nullglob

COUNT="${#CANDIDATES[@]}"

if [ "$COUNT" -eq 0 ]; then
  echo "::error::no APK was produced in $DIR" >&2
  exit 1
fi

if [ "$COUNT" -gt 1 ]; then
  # More than one APK means the build did something unexpected, and picking one would be a guess
  # about which artifact the checks and the upload should agree on. Refuse instead.
  echo "::error::expected exactly one APK in $DIR, found $COUNT:" >&2
  for f in "${CANDIDATES[@]}"; do echo "::error::  $f" >&2; done
  exit 1
fi

APK="${CANDIDATES[0]}"

case "$APK" in
  *-unsigned.apk)
    echo "::error::the only APK in $DIR is unsigned ($APK); it cannot be installed, so it must not be published" >&2
    exit 1
    ;;
esac

if [ ! -s "$APK" ]; then
  echo "::error::the APK is empty: $APK" >&2
  exit 1
fi

# Absolute path, so a caller that changes directory still refers to the same file.
( cd "$(dirname "$APK")" && printf '%s/%s\n' "$(pwd)" "$(basename "$APK")" )
