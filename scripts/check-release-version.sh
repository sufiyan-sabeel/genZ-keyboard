#!/usr/bin/env bash
# Release version consistency gate.
#
# The app version lives in exactly one place: version.properties. The GitHub release body carries
# the same versionCode, because the in-app update check reads it. Three things can therefore drift
# apart — the tag being released, version.properties, and the F-Droid changelog file — and this
# script is what stops them.
#
# Usage:
#   scripts/check-release-version.sh            # validate the working tree only
#   scripts/check-release-version.sh v1.0.7     # also require the tag to match versionName
#
# Exit codes: 0 ok, 1 a real inconsistency, 2 bad usage or missing file.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS="$ROOT/version.properties"
CHANGELOG_DIR="$ROOT/fastlane/metadata/android/en-US/changelogs"
TAG="${1:-}"

fail() { echo "::error::$*" >&2; exit 1; }

[ -f "$PROPS" ] || fail "version.properties is missing at the repository root"

# Read a key without sourcing the file: it is data, not shell.
read_prop() {
  local key="$1"
  sed -n "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*//p" "$PROPS" | tail -1 | tr -d '\r' | tr -d '[:space:]'
}

NAME="$(read_prop versionName)"
CODE="$(read_prop versionCode)"

[ -n "$NAME" ] || fail "version.properties has no versionName"
[ -n "$CODE" ] || fail "version.properties has no versionCode"

case "$NAME" in
  [0-9]*.[0-9]*.[0-9]*) ;;
  *) fail "versionName '$NAME' is not MAJOR.MINOR.PATCH" ;;
esac

case "$CODE" in
  ''|*[!0-9]*) fail "versionCode '$CODE' is not a positive integer" ;;
esac
[ "$CODE" -gt 0 ] || fail "versionCode must be greater than zero"

if [ -n "$TAG" ]; then
  WANT="${TAG#v}"
  WANT="${WANT#V}"
  if [ "$WANT" != "$NAME" ]; then
    fail "tag '$TAG' does not match versionName '$NAME' in version.properties"
  fi
  echo "tag $TAG matches versionName $NAME"
fi

# F-Droid looks for a changelog named after the version code. A missing one is a standard reason
# for a merge request to be sent back, so it is a build-time gate here rather than a surprise later.
CHANGELOG="$CHANGELOG_DIR/$CODE.txt"
if [ ! -f "$CHANGELOG" ]; then
  fail "no changelog for versionCode $CODE — expected fastlane/metadata/android/en-US/changelogs/$CODE.txt"
fi
if [ ! -s "$CHANGELOG" ]; then
  fail "changelog $CHANGELOG is empty"
fi

echo "version $NAME (versionCode $CODE), changelog $(wc -c <"$CHANGELOG" | tr -d ' ') bytes — consistent"
