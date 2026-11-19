#!/usr/bin/env bash
# M2 end-to-end verification — run this from a machine that can reach your Supabase project.
#
# STATUS: NOT RUN. The development sandbox cannot reach *.supabase.co, so this script has never
# executed. Its presence is not evidence that any of it works.
#
# Usage:
#   SUPABASE_URL=https://<ref>.supabase.co \
#   SUPABASE_ANON_KEY=<publishable key> \
#   ./scripts/verify-m2.sh
#
# Never pass a service-role key. This script exists to prove what the publishable key and ordinary
# user tokens are NOT allowed to do.
#
# Secrets: no token, password, signed URL or key is ever printed. Results are pass/fail plus HTTP
# codes only.
set -uo pipefail

: "${SUPABASE_URL:?set SUPABASE_URL}"
: "${SUPABASE_ANON_KEY:?set SUPABASE_ANON_KEY}"
URL="${SUPABASE_URL%/}"; KEY="$SUPABASE_ANON_KEY"

RUN="m2-$(date +%s)-$RANDOM"          # unique per run; every artefact is named with it
PASS=0; FAIL=0; SKIP=0
ok(){ PASS=$((PASS+1)); printf '  PASS  %s\n' "$1"; }
bad(){ FAIL=$((FAIL+1)); printf '  FAIL  %s (got %s, wanted %s)\n' "$1" "$2" "$3"; }
skp(){ SKIP=$((SKIP+1)); printf '  SKIP  %s — %s\n' "$1" "$2"; }
chk(){ [ "$2" = "$3" ] && ok "$1" || bad "$1" "$2" "$3"; }
say(){ printf '\n== %s\n' "$*"; }

jqget(){ sed -n "s/.*\"$1\":\"\\([^\"]*\\)\".*/\\1/p" | head -1; }

signup(){ # $1 email -> access token on stdout
  curl -s -X POST "$URL/auth/v1/signup" -H "apikey: $KEY" -H "Content-Type: application/json" \
    -d "{\"email\":\"$1\",\"password\":\"${PW}\"}" | jqget access_token
}
login(){
  curl -s -X POST "$URL/auth/v1/token?grant_type=password" -H "apikey: $KEY" \
    -H "Content-Type: application/json" -d "{\"email\":\"$1\",\"password\":\"${PW}\"}" | jqget access_token
}
code(){ local m=$1 u=$2 t=${3:-} f=${4:-} ct=${5:-}; local a=(-s -o /dev/null -w '%{http_code}' -X "$m" "$u" -H "apikey: $KEY")
  [ -n "$t" ] && a+=(-H "Authorization: Bearer $t"); [ -n "$ct" ] && a+=(-H "Content-Type: $ct")
  [ -n "$f" ] && a+=(--data-binary "@$f"); curl "${a[@]}"; }

PW="Test-${RUN}!"
A_MAIL="a-${RUN}@example.test"; B_MAIL="b-${RUN}@example.test"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
printf '\x89PNG\r\n\x1a\n' > "$TMP/w.png"; head -c 512 /dev/urandom >> "$TMP/w.png"

say "1. authentication"
TA=$(signup "$A_MAIL"); [ -n "$TA" ] && ok "account A created" || bad "account A created" "none" "token"
TB=$(signup "$B_MAIL"); [ -n "$TB" ] && ok "account B created" || bad "account B created" "none" "token"
AID=$(printf '%s' "$TA" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | sed -n 's/.*"sub":"\([^"]*\)".*/\1/p')
[ -n "$AID" ] && ok "account id read from token" || { echo "cannot continue without account id"; exit 1; }
chk "login returns a token" "$([ -n "$(login "$A_MAIL")" ] && echo yes || echo no)" yes
chk "wrong password is rejected" \
R=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$URL/auth/v1/token?grant_type=password" \
    -H "apikey: $KEY" -H "Content-Type: application/json" \
    -d "{\"email\":\"$A_MAIL\",\"password\":\"wrong\"}"); chk "wrong password rejected" "$R" 400

say "2. session refresh"
RT=$(curl -s -X POST "$URL/auth/v1/token?grant_type=password" -H "apikey: $KEY" \
     -H "Content-Type: application/json" -d "{\"email\":\"$A_MAIL\",\"password\":\"$PW\"}" | jqget refresh_token)
NT=$(curl -s -X POST "$URL/auth/v1/token?grant_type=refresh_token" -H "apikey: $KEY" \
     -H "Content-Type: application/json" -d "{\"refresh_token\":\"$RT\"}" | jqget access_token)
[ -n "$NT" ] && ok "refresh yields a new access token" || bad "refresh yields a new access token" none token
chk "a bogus refresh token is rejected" \
R=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$URL/auth/v1/token?grant_type=refresh_token" \
    -H "apikey: $KEY" -H "Content-Type: application/json" -d '{"refresh_token":"nope"}')
chk "bogus refresh token rejected" "$R" 400

DRAFT="$AID/${RUN}.png"
say "3. private draft upload"
chk "A uploads draft" "$(code POST "$URL/storage/v1/object/theme-drafts/$DRAFT" "$TA" "$TMP/w.png" image/png)" 200

say "4. draft ownership isolation"
chk "B cannot read A draft" "$(code GET "$URL/storage/v1/object/authenticated/theme-drafts/$DRAFT" "$TB")" 400
chk "anon cannot read A draft" "$(code GET "$URL/storage/v1/object/authenticated/theme-drafts/$DRAFT")" 401
chk "B cannot upload into A folder" "$(code POST "$URL/storage/v1/object/theme-drafts/$AID/x.png" "$TB" "$TMP/w.png" image/png)" 403

say "5. signed URL preview"
SIGNED=$(curl -s -X POST "$URL/storage/v1/object/sign/theme-drafts/$DRAFT" \
  -H "apikey: $KEY" -H "Authorization: Bearer $TA" -H "Content-Type: application/json" \
  -d '{"expiresIn":60}')
SU=$(printf '%s' "$SIGNED" | jqget signedURL)
[ -n "$SU" ] && ok "owner can mint a signed URL (value not printed)" || bad "owner can mint a signed URL" none url
[ -n "$SU" ] && chk "signed URL serves the object" "$(code GET "$URL/storage/v1$SU")" 200

say "6. public copy"
# the copy endpoint needs a JSON body; build it inline
printf '{"bucketId":"theme-drafts","sourceKey":"%s","destinationBucket":"theme-wallpapers","destinationKey":"%s"}' \
  "$DRAFT" "$AID/${RUN}.png" > "$TMP/copy.json"
chk "A copies draft to public" "$(code POST "$URL/storage/v1/object/copy" "$TA" "$TMP/copy.json" application/json)" 200
chk "repeat copy returns 409" "$(code POST "$URL/storage/v1/object/copy" "$TA" "$TMP/copy.json" application/json)" 409

say "7. database publishing"
ROW=$(curl -s -X POST "$URL/rest/v1/themes" -H "apikey: $KEY" -H "Authorization: Bearer $TA" \
  -H "Content-Type: application/json" -H "Prefer: return=representation" \
  -d "{\"name\":\"$RUN\",\"category\":\"anime\",\"doc\":{},\"draft_path\":\"$DRAFT\",\"is_public\":false}")
TID=$(printf '%s' "$ROW" | jqget id)
[ -n "$TID" ] && ok "draft row created" || bad "draft row created" none id
chk "publish without public wallpaper is refused" \
R=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH "$URL/rest/v1/themes?id=eq.$TID" \
    -H "apikey: $KEY" -H "Authorization: Bearer $TA" -H "Content-Type: application/json" \
    -d '{"is_public":true}')
chk "publish with no wallpaper rejected" "$R" 400
R=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH "$URL/rest/v1/themes?id=eq.$TID" \
    -H "apikey: $KEY" -H "Authorization: Bearer $TA" -H "Content-Type: application/json" \
    -d "{\"wallpaper_path\":\"$AID/${RUN}.png\",\"is_public\":true}")
chk "publish with wallpaper succeeds" "$R" 204

say "8. retry and recovery"
chk "repeating the publish is harmless" \
R=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH "$URL/rest/v1/themes?id=eq.$TID" \
    -H "apikey: $KEY" -H "Authorization: Bearer $TA" -H "Content-Type: application/json" \
    -d "{\"wallpaper_path\":\"$AID/${RUN}.png\",\"is_public\":true}")
chk "repeat publish still succeeds" "$R" 204
DENIED=$(curl -s -X PATCH "$URL/rest/v1/themes?id=eq.$TID" \
    -H "apikey: $KEY" -H "Authorization: Bearer $TB" -H "Content-Type: application/json" \
    -H "Prefer: return=representation" -d '{"name":"hijacked"}')
chk "B cannot edit A theme (zero rows)" "$(printf '%s' "$DENIED" | grep -c '"id"' || true)" 0

say "9. draft cleanup"
chk "A deletes its draft" "$(code DELETE "$URL/storage/v1/object/theme-drafts/$DRAFT" "$TA")" 200
chk "public artwork still readable" "$(code GET "$URL/storage/v1/object/public/theme-wallpapers/$AID/${RUN}.png")" 200

say "10. offline keyboard"
skp "keyboard works with no network" "requires a device; run manually with airplane mode on"

printf '\n===== %d passed, %d failed, %d skipped =====\n' "$PASS" "$FAIL" "$SKIP"
cat <<EOF

CLEANUP — only artefacts named with this run id ($RUN) are affected:
  - delete theme row:   DELETE /rest/v1/themes?id=eq.$TID
  - delete public file: DELETE /storage/v1/object/theme-wallpapers/$AID/$RUN.png
  - delete accounts:    remove $A_MAIL and $B_MAIL from the dashboard

script has never been executed — the expected codes must be confirmed by the first real run, not
assumed. Fix them as reality dictates rather than trusting the numbers written here.
EOF
[ "$FAIL" -eq 0 ]
