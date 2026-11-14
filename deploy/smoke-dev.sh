#!/usr/bin/env bash
# End-to-end smoke test of Pride Universal dev, run ON the server against 127.0.0.1:8111.
# Uses test numbers 99999000xx only; messaging dev makes every code 123456 and sends nothing.
set -u
B=http://127.0.0.1:8111
J='Content-Type: application/json'
PASS=0; FAIL=0
check() { # name expected actual
  if [[ "$2" == "$3" ]]; then PASS=$((PASS+1)); printf '  ok   %-48s %s\n' "$1" "$3";
  else FAIL=$((FAIL+1)); printf '  FAIL %-48s expected %s got %s\n' "$1" "$2" "$3"; fi
}
req() { # method url token body -> sets CODE and BODY
  local m="$1" u="$2" t="$3" d="${4:-}" out
  if [[ -n "$d" ]]; then out=$(curl -s -m 20 -w $'\n%{http_code}' -X "$m" "$B$u" -H "$J" -H "Authorization: Bearer $t" -d "$d")
  else out=$(curl -s -m 20 -w $'\n%{http_code}' -X "$m" "$B$u" -H "Authorization: Bearer $t"); fi
  CODE="${out##*$'\n'}"; BODY="${out%$'\n'*}"
}
login() { # mobile -> echoes access token
  curl -s -m 20 -X POST "$B/api/auth/otp/send" -H "$J" -d "{\"mobile\":\"$1\"}" >/dev/null
  curl -s -m 20 -X POST "$B/api/auth/otp/verify" -H "$J" -d "{\"mobile\":\"$1\",\"otp\":\"123456\"}" | jq -r '.access_token // empty'
}
psql_dev() { docker exec pride-postgres psql -U pride_admin -d pride_dev -qAt -c "$1"; }

ADMIN=9999900001; OWNER=9999900002; GUARD=9999900003
psql_dev "insert into app_user (mobile, display_name, role) values ('$ADMIN','Smoke Admin','admin') on conflict (mobile) do nothing" >/dev/null
psql_dev "delete from otp_request where mobile like '99999000%'" >/dev/null
# Re-runnable: an account keeps the flat it was first linked to, and each run adds a new flat for
# $OWNER. Retire the earlier smoke flats and the owner's account, so this run provisions the owner
# against this run's flat, the way a first sign-in does. (Deleting an account is safe by design:
# every reference to app_user cascades or is set null, which account deletion relies on.)
psql_dev "update flat set is_active = false where owner_contact = '$OWNER'" >/dev/null
psql_dev "delete from app_user where mobile = '$OWNER'" >/dev/null

echo "== auth"
req POST /api/auth/otp/send "" '{"mobile":"9999900099"}'; check "unknown number refused" 404 "$CODE"
A=$(login $ADMIN); check "admin logs in with 123456 via messaging" yes "$([[ -n "$A" ]] && echo yes || echo no)"
req GET /api/auth/me "$A"; check "me" 200 "$CODE"

echo "== settings, flats, bills"
req GET /api/settings/A "$A"; V=$(echo "$BODY" | jq .version)
req PUT /api/settings/A "$A" "{\"monthly_maintenance\":2500,\"maintenance_due_day\":10,\"society_name\":\"Pride Universal (dev)\",\"upi_id\":\"pride@upi\",\"version\":$V}"
check "admin sets wing A amounts" 200 "$CODE"
FLATNO="S$(date +%H%M%S)"
PLATE="MH14SM${FLATNO: -4}"   # earlier smoke flats are retired above, so this plate is the smoke flat's alone
req POST /api/flats "$A" "{\"wing\":\"A\",\"flat_number\":\"$FLATNO\",\"floor\":1,\"owner_name\":\"Smoke Owner\",\"owner_contact\":\"$OWNER\",\"four_wheeler\":true,\"four_wheeler_count\":1,\"four_wheeler_numbers\":\"MH 14 SM ${FLATNO: -4}\"}"
check "admin adds a flat" 201 "$CODE"; FLAT_ID=$(echo "$BODY" | jq -r .id)
Y=$(date +%Y); M=$(date +%-m)
req POST /api/bills/generate "$A" "{\"year\":$Y,\"month\":$M}"; check "generate bills" 200 "$CODE"
req GET "/api/bills?status=pending&wing=A" "$A"; check "bill filter by lowercase status" 200 "$CODE"

echo "== resident"
R=$(login $OWNER); check "owner logs in (auto-provisioned)" yes "$([[ -n "$R" ]] && echo yes || echo no)"
req GET /api/dashboard "$R"; check "resident dashboard" 200 "$CODE"
check "dashboard shows the flat's dues" true "$(echo "$BODY" | jq '.my_flat.bills_due >= 1')"
req GET /api/bills/mine "$R"; check "my bills" 200 "$CODE"
req POST /api/complaints "$R" '{"category":"plumbing","title":"Smoke: leaking tap","description":"test","priority":"high"}'
check "resident raises a complaint" 201 "$CODE"; CMP=$(echo "$BODY" | jq -r .id); CV=$(echo "$BODY" | jq .version)
req GET /api/transactions "$R"; check "resident kept out of the books" 403 "$CODE"

echo "== committee"
req POST /api/notices "$A" '{"title":"Smoke notice","content":"Water cut 10-12","notice_type":"general","scope":"both","is_published":true,"pinned":true}'
check "admin posts a notice" 201 "$CODE"
req GET /api/notices "$R"; check "resident sees it" true "$(echo "$BODY" | jq 'map(.title) | index("Smoke notice") != null')"
req PUT "/api/complaints/$CMP" "$A" "{\"status\":\"resolved\",\"assigned_to\":\"Plumber\",\"version\":$CV}"
check "admin resolves the complaint" 200 "$CODE"
req POST /api/meetings "$A" '{"meeting_type":"agm","title":"Smoke AGM","scope":"both","scheduled_date":"2026-12-20","venue":"Clubhouse"}'
check "admin schedules a meeting" 201 "$CODE"; MTG=$(echo "$BODY" | jq -r .id)
req POST "/api/meetings/$MTG/resolutions" "$A" '{"title":"Approve accounts","status":"passed","votes_for":10}'
check "resolution R-1" '"R-1"' "$(echo "$BODY" | jq .resolution_number)"
req POST /api/transactions "$A" "{\"wing\":\"common\",\"category\":\"donation\",\"amount\":500,\"txn_date\":\"$(date +%F)\"}"
check "record a ledger entry" 201 "$CODE"

echo "== gate"
req POST /api/users "$A" "{\"mobile\":\"$GUARD\",\"display_name\":\"Smoke Guard\",\"role\":\"security\"}"
[[ "$CODE" == 201 || "$CODE" == 409 ]] && CODE=ok; check "admin creates a guard account" ok "$CODE"
G=$(login $GUARD)
req POST /api/visitors "$G" "{\"visitor_name\":\"Courier\",\"purpose\":\"delivery\",\"wing\":\"A\",\"flat_number\":\"$FLATNO\",\"await_approval\":true}"
check "guard logs a visitor (pending)" 201 "$CODE"; VIS=$(echo "$BODY" | jq -r .id)
req POST "/api/visitors/$VIS/approve" "$R"; check "resident approves" 200 "$CODE"
req POST "/api/visitors/$VIS/check-out" "$G"; check "guard checks out" 200 "$CODE"

echo "== community"
req POST /api/vendors "$R" '{"name":"Smoke Electricals","service_type":"electrician","contact":"9123456780","approved":true}'
check "resident recommends a vendor, unverified" false "$(echo "$BODY" | jq .approved)"
req POST /api/marketplace "$R" '{"title":"Smoke sofa","category":"furniture","listing_type":"give_away","price":900}'
check "giveaway is free" 0 "$(echo "$BODY" | jq .price)"

echo "== festival funds, gate vehicles, businesses, notifications"
# The poster's numbers: 7,680 + 1,76,642 - 1,07,208 = 77,114, then Kojagiri spends 42,401 of it.
req POST /api/events "$A" '{"name":"Smoke Festival","start_date":"2090-09-14","opening_balance":7680,"is_published":true}'
check "committee opens an event fund" 201 "$CODE"; EV1=$(echo "$BODY" | jq -r .id)
req POST "/api/events/$EV1/entries" "$A" '{"type":"income","head":"vargani","amount":176642}'
req POST "/api/events/$EV1/entries" "$A" '{"type":"expense","head":"mahaprasad","amount":107208}'
req POST /api/events "$A" "{\"name\":\"Smoke Kojagiri\",\"start_date\":\"2090-10-25\",\"carried_from_id\":\"$EV1\",\"is_published\":true}"
EV2=$(echo "$BODY" | jq -r .id)
req POST "/api/events/$EV2/entries" "$A" '{"type":"expense","head":"Pav bhaji","amount":42401}'
req GET "/api/events/$EV2" "$R"; check "member reads it" 200 "$CODE"
check "carried balance closes at 34,713" 34713 "$(echo "$BODY" | jq '.closing_balance | floor')"
req GET /api/events "$G"; check "guards are not shown festival money" 403 "$CODE"
req GET "/api/vehicles/lookup?plate=$(echo "$PLATE" | tr 'A-Z' 'a-z')" "$G"; check "guard looks up a plate" 200 "$CODE"
check "plate found at the smoke flat" "\"$FLATNO\"" "$(echo "$BODY" | jq '.matches[0].flat_number')"
req GET "/api/vehicles/lookup?plate=MH12AB1234" "$R"; check "residents cannot look up plates" 403 "$CODE"
req POST /api/businesses "$R" '{"name":"Smoke Tiffins","category":"home_food","contact":"9123456780","delivers":true}'
check "resident lists a business, pending" '"pending"' "$(echo "$BODY" | jq .status)"; BIZ=$(echo "$BODY" | jq -r .id)
req POST "/api/businesses/$BIZ/review" "$A" '{"status":"rejected","note":"smoke test"}'; check "committee reviews it" 200 "$CODE"
req POST /api/notifications "$A" "{\"title\":\"Smoke\",\"body\":\"Smoke message\",\"audience\":\"flat\",\"audience_value\":\"A-$FLATNO\"}"
check "admin writes to one flat" 1 "$(echo "$BODY" | jq .recipients)"
req GET /api/notifications "$R"; check "it is in the resident's inbox" true "$(echo "$BODY" | jq 'map(.title) | index("Smoke") != null')"

echo "== Nexus console"
TOKEN=$(grep -E '^ADMIN_SERVICE_TOKEN=' ~/pride/dev.env | cut -d= -f2-)
CODE=$(curl -s -o /dev/null -w '%{http_code}' -m 10 "$B/api/admin/users" -H "Authorization: Bearer $A"); check "admin JWT refused on /api/admin" 401 "$CODE"
BODY=$(curl -s -m 10 "$B/api/admin/users" -H "Authorization: Bearer $TOKEN")
check "console lists users, camelCase" true "$(echo "$BODY" | jq '.data | length > 0 and (.[0] | has("registrationDate"))')"
unset TOKEN

echo "== tidy"
psql_dev "update notice set is_published = false where title = 'Smoke notice'" >/dev/null
psql_dev "delete from member_business where name = 'Smoke Tiffins'" >/dev/null
psql_dev "delete from society_event where name = 'Smoke Kojagiri'" >/dev/null
psql_dev "delete from society_event where name = 'Smoke Festival'" >/dev/null
echo "passed $PASS, failed $FAIL"
