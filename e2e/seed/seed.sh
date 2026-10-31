#!/usr/bin/env bash
# Seeds a fresh club, team, users, events and notifications for Maestro runs.
# Usage: e2e/seed/seed.sh [--env] [BASE_URL]
#   BASE_URL defaults to $E2E_BASE_URL or http://localhost:8080.
#   Prints the created entities as JSON on stdout, or KEY=value lines with --env.
# The server must run with E2E_SEED_ENABLED=true (only the club is created via /e2e/seed/club).
set -euo pipefail

OUTPUT=json
if [[ "${1:-}" == "--env" ]]; then
  OUTPUT=env
  shift
fi
BASE_URL="${1:-${E2E_BASE_URL:-http://localhost:8080}}"
BASE_URL="${BASE_URL%/}"
PASSWORD="${E2E_PASSWORD:-Teamorg-E2E-1}"
RUN_ID="$(date +%s)"
EMAIL_DOMAIN="e2e.teamorg.test"

for tool in curl jq; do
  command -v "$tool" >/dev/null || { echo "missing required tool: $tool" >&2; exit 1; }
done

log() { echo "[seed] $*" >&2; }

# api METHOD PATH TOKEN [JSON_BODY] -> response body on stdout, exits on non-2xx
api() {
  local method="$1" path="$2" token="$3" body="${4:-}"
  local args=(-sS -X "$method" "$BASE_URL$path" -H "Content-Type: application/json" -w $'\n%{http_code}')
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$body" ]] && args+=(-d "$body")
  local response status payload
  response="$(curl "${args[@]}")"
  status="${response##*$'\n'}"
  payload="${response%$'\n'*}"
  if [[ "$status" != 2* ]]; then
    echo "[seed] $method $path failed with HTTP $status: $payload" >&2
    exit 1
  fi
  printf '%s' "$payload"
}

# register EMAIL NAME -> {token,userId,displayName,...}
register() {
  api POST /auth/register "" "$(jq -nc --arg e "$1" --arg p "$PASSWORD" --arg n "$2" \
    '{email:$e,password:$p,displayName:$n}')"
}

# Dates are derived from now: UTC midnight today + N days + H hours.
iso_at() { jq -nr --argjson d "$1" --argjson h "$2" '(now | floor | . - (. % 86400)) + $d * 86400 + $h * 3600 | todate'; }
weekday_index() { jq -nr --arg t "$1" '$t | fromdate | strftime("%u") | tonumber - 1'; }
date_at() { jq -nr --argjson d "$1" '(now | floor | . - (. % 86400)) + $d * 86400 | strftime("%Y-%m-%d")'; }

log "base url $BASE_URL, run $RUN_ID"
curl -fsS "$BASE_URL/health" >/dev/null || { echo "[seed] server not reachable at $BASE_URL" >&2; exit 1; }

MANAGER_EMAIL="e2e-manager-$RUN_ID@$EMAIL_DOMAIN"
COACH_EMAIL="e2e-coach-$RUN_ID@$EMAIL_DOMAIN"
PLAYER_EMAILS=("e2e-player1-$RUN_ID@$EMAIL_DOMAIN" "e2e-player2-$RUN_ID@$EMAIL_DOMAIN" "e2e-player3-$RUN_ID@$EMAIL_DOMAIN")
PLAYER_NAMES=("Pia Player" "Paul Player" "Pat Player")

log "registering users"
MANAGER="$(register "$MANAGER_EMAIL" "Mia Manager")"
COACH="$(register "$COACH_EMAIL" "Carl Coach")"
PLAYERS=()
for i in 0 1 2; do
  PLAYERS+=("$(register "${PLAYER_EMAILS[$i]}" "${PLAYER_NAMES[$i]}")")
done
MANAGER_TOKEN="$(jq -r .token <<<"$MANAGER")"
COACH_TOKEN="$(jq -r .token <<<"$COACH")"
MANAGER_ID="$(jq -r .userId <<<"$MANAGER")"
COACH_ID="$(jq -r .userId <<<"$COACH")"

log "creating club and team"
CLUB_NAME="E2E Club $RUN_ID"
CLUB="$(api POST /e2e/seed/club "" "$(jq -nc --arg n "$CLUB_NAME" --arg m "$MANAGER_EMAIL" '{name:$n,managerEmail:$m}')")"
CLUB_ID="$(jq -r .id <<<"$CLUB")"
TEAM_NAME="E2E Team $RUN_ID"
TEAM="$(api POST "/clubs/$CLUB_ID/teams" "$MANAGER_TOKEN" "$(jq -nc --arg n "$TEAM_NAME" '{name:$n}')")"
TEAM_ID="$(jq -r .id <<<"$TEAM")"

log "adding coach and players to team"
api POST "/teams/$TEAM_ID/members" "$MANAGER_TOKEN" "$(jq -nc --arg u "$COACH_ID" '{userId:$u,role:"coach"}')" >/dev/null
for i in 0 1 2; do
  api POST "/teams/$TEAM_ID/members" "$MANAGER_TOKEN" \
    "$(jq -nc --arg u "$(jq -r .userId <<<"${PLAYERS[$i]}")" '{userId:$u,role:"player"}')" >/dev/null
done

log "creating single event and weekly series"
SINGLE_START="$(iso_at 1 18)"
SINGLE_END="$(iso_at 1 19.5)"
SINGLE_TITLE="E2E Single Training $RUN_ID"
SINGLE="$(api POST /events "$COACH_TOKEN" "$(jq -nc --arg t "$SINGLE_TITLE" --arg s "$SINGLE_START" --arg e "$SINGLE_END" --arg team "$TEAM_ID" \
  '{title:$t,type:"training",startAt:$s,endAt:$e,location:"E2E Hall",teamIds:[$team]}')")"
SINGLE_ID="$(jq -r .id <<<"$SINGLE")"

SERIES_START="$(iso_at 2 19)"
SERIES_END="$(iso_at 2 20.5)"
SERIES_UNTIL="$(date_at 58)"
SERIES_WEEKDAY="$(weekday_index "$SERIES_START")"
SERIES_TITLE="E2E Weekly Training $RUN_ID"
SERIES="$(api POST /events "$COACH_TOKEN" "$(jq -nc --arg t "$SERIES_TITLE" --arg s "$SERIES_START" --arg e "$SERIES_END" --arg team "$TEAM_ID" \
  --argjson wd "$SERIES_WEEKDAY" --arg until "$SERIES_UNTIL" \
  '{title:$t,type:"training",startAt:$s,endAt:$e,location:"E2E Hall",teamIds:[$team],
    recurring:{patternType:"weekly",weekdays:[$wd],seriesEndDate:$until}}')")"
SERIES_EVENT_ID="$(jq -r .id <<<"$SERIES")"
SERIES_ID="$(jq -r .seriesId <<<"$SERIES")"

log "player responses (create coach notifications)"
rsvp() { api PUT "/events/$SINGLE_ID/attendance/me" "$(jq -r .token <<<"$1")" "$2" >/dev/null; }
rsvp "${PLAYERS[0]}" '{"status":"confirmed"}'
rsvp "${PLAYERS[1]}" '{"status":"declined","reason":"E2E away"}'
rsvp "${PLAYERS[2]}" '{"status":"unsure","reason":"E2E maybe"}'

unread_count() { api GET /notifications/unread-count "$1" | jq -r '.count // .unreadCount // .'; }
log "waiting for notifications"
COACH_UNREAD=0
for _ in $(seq 1 20); do
  COACH_UNREAD="$(unread_count "$COACH_TOKEN")"
  [[ "$COACH_UNREAD" =~ ^[0-9]+$ ]] && (( COACH_UNREAD >= 3 )) && break
  sleep 0.5
done
if ! [[ "$COACH_UNREAD" =~ ^[0-9]+$ ]] || (( COACH_UNREAD < 3 )); then
  echo "[seed] expected >= 3 coach notifications, got $COACH_UNREAD" >&2
  exit 1
fi
PLAYER1_UNREAD="$(unread_count "$(jq -r .token <<<"${PLAYERS[0]}")")"

player_json() {
  jq -nc --argjson auth "$1" --arg email "$2" \
    '{id:$auth.userId,email:$email,displayName:$auth.displayName,token:$auth.token}'
}
PLAYERS_JSON="$(for i in 0 1 2; do player_json "${PLAYERS[$i]}" "${PLAYER_EMAILS[$i]}"; done | jq -sc .)"

RESULT="$(jq -n \
  --arg baseUrl "$BASE_URL" --arg password "$PASSWORD" --arg runId "$RUN_ID" \
  --arg clubId "$CLUB_ID" --arg clubName "$CLUB_NAME" --arg teamId "$TEAM_ID" --arg teamName "$TEAM_NAME" \
  --argjson manager "$(player_json "$MANAGER" "$MANAGER_EMAIL")" \
  --argjson coach "$(player_json "$COACH" "$COACH_EMAIL")" \
  --argjson players "$PLAYERS_JSON" \
  --arg singleId "$SINGLE_ID" --arg singleTitle "$SINGLE_TITLE" --arg singleStart "$SINGLE_START" \
  --arg seriesEventId "$SERIES_EVENT_ID" --arg seriesId "$SERIES_ID" --arg seriesTitle "$SERIES_TITLE" --arg seriesStart "$SERIES_START" \
  --argjson coachUnread "$COACH_UNREAD" --argjson player1Unread "$PLAYER1_UNREAD" \
  '{baseUrl:$baseUrl,password:$password,runId:$runId,
    club:{id:$clubId,name:$clubName},team:{id:$teamId,name:$teamName},
    manager:$manager,coach:$coach,players:$players,
    events:{single:{id:$singleId,title:$singleTitle,startAt:$singleStart},
            series:{eventId:$seriesEventId,seriesId:$seriesId,title:$seriesTitle,firstStartAt:$seriesStart}},
    notifications:{coachUnread:$coachUnread,player1Unread:$player1Unread}}')"

if [[ "$OUTPUT" == "env" ]]; then
  jq -r '
    "E2E_BASE_URL=\(.baseUrl)", "E2E_PASSWORD=\(.password)", "E2E_RUN_ID=\(.runId)",
    "E2E_CLUB_ID=\(.club.id)", "E2E_CLUB_NAME=\(.club.name)",
    "E2E_TEAM_ID=\(.team.id)", "E2E_TEAM_NAME=\(.team.name)",
    "E2E_MANAGER_EMAIL=\(.manager.email)", "E2E_COACH_EMAIL=\(.coach.email)",
    "E2E_COACH_NAME=\(.coach.displayName)",
    (.players | to_entries[] | "E2E_PLAYER\(.key + 1)_EMAIL=\(.value.email)", "E2E_PLAYER\(.key + 1)_NAME=\(.value.displayName)", "E2E_PLAYER\(.key + 1)_ID=\(.value.id)"),
    "E2E_SINGLE_EVENT_ID=\(.events.single.id)", "E2E_SINGLE_EVENT_TITLE=\(.events.single.title)",
    "E2E_SERIES_EVENT_ID=\(.events.series.eventId)", "E2E_SERIES_ID=\(.events.series.seriesId)",
    "E2E_SERIES_TITLE=\(.events.series.title)"' <<<"$RESULT"
else
  printf '%s\n' "$RESULT"
fi
