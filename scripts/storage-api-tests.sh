#!/usr/bin/env bash
# Storage API integration tests for GenZ Keyboard community storage.
#
# STATUS: NOT RUN in this repository's CI. The development sandbox cannot reach
# *.supabase.co (egress not allowlisted), so these tests have never executed against a live
# project. Do not read their presence as evidence that storage authorization works.
#
# Run from a machine with network access:
#     SUPABASE_URL=... SUPABASE_ANON_KEY=... ./scripts/storage-api-tests.sh
#
# Never pass a service-role key: these tests are specifically about what the anon key and ordinary
# user tokens are NOT allowed to do.
set -uo pipefail

: "${SUPABASE_URL:?set SUPABASE_URL}"
: "${SUPABASE_ANON_KEY:?set SUPABASE_ANON_KEY}"

URL="${SUPABASE_URL%/}"
KEY="$SUPABASE_ANON_KEY"
BUCKET="theme-wallpapers"
PASS=0; FAIL=0

say()  { printf '\n== %s\n' "$*"; }
ok()   { PASS=$((PASS+1)); printf '  PASS  %s (got %s)\n' "$1" "$2"; }
bad()  { FAIL=$((FAIL+1)); printf '  FAIL  %s (got %s, wanted %s)\n' "$1" "$2" "$3"; }
check(){ [ "$2" = "$3" ] && ok "$1" "$2" || bad "$1" "$2" "$3"; }

signup() { # $1=email -> prints access token
  curl -s -X POST "$URL/auth/v1/signup" -H "apikey: $KEY" -H "Content-Type: application/json" \
       -d "{\"email\":\"$1\",\"password\":\"Test-passw0rd!\"}" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

code() { # $1=method $2=url $3=token(optional) $4=file(optional) $5=content-type(optional)
  local m="$1" u="$2" t="${3:-}" f="${4:-}" ct="${5:-}"
  local args=(-s -o /dev/null -w '%{http_code}' -X "$m" "$u" -H "apikey: $KEY")
  [ -n "$t" ]  && args+=(-H "Authorization: Bearer $t")
  [ -n "$ct" ] && args+=(-H "Content-Type: $ct")
  [ -n "$f" ]  && args+=(--data-binary "@$f")
  curl "${args[@]}"
}

say "setup: two disposable accounts"
A=$(signup "a-$(date +%s)@example.test"); B=$(signup "b-$(date +%s)@example.test")
[ -z "$A" ] || [ -z "$B" ] && { echo "could not create accounts"; exit 1; }
AID=$(printf '%s' "$A" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | sed -n 's/.*"sub":"\([^"]*\)".*/\1/p')
[ -z "$AID" ] && { echo "could not read account A id"; exit 1; }

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
printf '\x89PNG\r\n\x1a\n' > "$TMP/ok.png"; head -c 200 /dev/urandom >> "$TMP/ok.png"
printf '%%PDF-1.4\n' > "$TMP/bad.pdf"
head -c 9000000 /dev/urandom > "$TMP/big.bin"   # ~9 MB, over the 8 MB bucket limit

P="$AID/wall.png"

say "1. own-folder upload (expect 200)"
check "A uploads to own folder" "$(code POST "$URL/storage/v1/object/$BUCKET/$P" "$A" "$TMP/ok.png" image/png)" 200

say "2. cross-user upload into another folder (expect 4xx)"
check "B uploads into A folder" "$(code POST "$URL/storage/v1/object/$BUCKET/$AID/evil.png" "$B" "$TMP/ok.png" image/png)" 403

say "3. cross-user overwrite (expect 4xx)"
check "B overwrites A object" "$(code POST "$URL/storage/v1/object/$BUCKET/$P" "$B" "$TMP/ok.png" image/png)" 403

say "4. own overwrite — no UPDATE policy exists, so expect 4xx"
check "A overwrites own object" "$(code POST "$URL/storage/v1/object/$BUCKET/$P" "$A" "$TMP/ok.png" image/png)" 403

say "5. cross-user delete (expect 4xx)"
check "B deletes A object" "$(code DELETE "$URL/storage/v1/object/$BUCKET/$P" "$B")" 403

say "6. anonymous upload (expect 4xx)"
check "anon upload" "$(code POST "$URL/storage/v1/object/$BUCKET/$AID/anon.png" "" "$TMP/ok.png" image/png)" 401

say "7. public download (expect 200)"
check "public download" "$(code GET "$URL/storage/v1/object/public/$BUCKET/$P")" 200

say "8. oversized upload (expect 4xx)"
check "9 MB rejected" "$(code POST "$URL/storage/v1/object/$BUCKET/$AID/big.bin" "$A" "$TMP/big.bin" image/png)" 413

say "9. disallowed MIME (expect 4xx)"
check "application/pdf rejected" "$(code POST "$URL/storage/v1/object/$BUCKET/$AID/x.pdf" "$A" "$TMP/bad.pdf" application/pdf)" 400

say "10. malformed path (expect 4xx)"
check "path without owner folder" "$(code POST "$URL/storage/v1/object/$BUCKET/loose.png" "$A" "$TMP/ok.png" image/png)" 403

say "11. own delete (expect 200)"
check "A deletes own object" "$(code DELETE "$URL/storage/v1/object/$BUCKET/$P" "$A")" 200

printf '\n===== %d passed, %d failed =====\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
