#!/usr/bin/env bash
# Seeds the backend and runs the Maestro suite on one platform.
# Usage: e2e/maestro/run.sh <android|ios> [flow-name-substring ...]
# Needs: maestro, jq, curl, a booted emulator/simulator with the app installed, and the server running with
# E2E_SEED_ENABLED=true (see e2e/README.md). Artifacts (seed.json, junit xml, screenshots, logs) go to build/maestro-artifacts.
# Env: E2E_BASE_URL (host-side API url, default http://localhost:8080), E2E_IOS_DEVICE (simulator UDID, default: the booted one),
#      E2E_ANDROID_DEVICE (adb serial, default: the only device).
set -uo pipefail

PLATFORM="${1:-}"
[[ "$PLATFORM" == "android" || "$PLATFORM" == "ios" ]] || { echo "usage: $0 <android|ios> [flow-filter ...]" >&2; exit 2; }
shift

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ARTIFACTS="$ROOT/build/maestro-artifacts/$PLATFORM"
APP_ID="ch.teamorg"
API_URL="${E2E_BASE_URL:-http://localhost:8080}"
rm -rf "$ARTIFACTS"
mkdir -p "$ARTIFACTS"

if [[ "$PLATFORM" == "ios" ]]; then
  DEVICE="${E2E_IOS_DEVICE:-$(xcrun simctl list devices booted -j | jq -r '[.devices[][] | select(.state=="Booted")][0].udid // empty')}"
  [[ -n "$DEVICE" ]] || { echo "no booted iOS simulator" >&2; exit 1; }
  EXCLUDE_TAGS="wip,skip-ios"
else
  DEVICE="${E2E_ANDROID_DEVICE:-$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')}"
  [[ -n "$DEVICE" ]] || { echo "no Android device" >&2; exit 1; }
  EXCLUDE_TAGS="wip,skip-android"
fi

echo "[maestro] seeding $API_URL" >&2
"$ROOT/e2e/seed/seed.sh" "$API_URL" > "$ARTIFACTS/seed.json" || { echo "seeding failed" >&2; exit 1; }

ENV_ARGS=()
while IFS= read -r line; do ENV_ARGS+=(-e "$line"); done < <(jq -r --arg api "$API_URL" '
  "API_URL=\($api)", "RUN_ID=\(.runId)", "PASSWORD=\(.password)",
  "CLUB_ID=\(.club.id)", "CLUB_NAME=\(.club.name)", "TEAM_ID=\(.team.id)", "TEAM_NAME=\(.team.name)",
  "MANAGER_EMAIL=\(.manager.email)", "MANAGER_TOKEN=\(.manager.token)",
  "COACH_ID=\(.coach.id)", "COACH_EMAIL=\(.coach.email)", "COACH_NAME=\(.coach.displayName)", "COACH_TOKEN=\(.coach.token)",
  (.players | to_entries[] | "PLAYER\(.key + 1)_ID=\(.value.id)", "PLAYER\(.key + 1)_EMAIL=\(.value.email)",
     "PLAYER\(.key + 1)_NAME=\(.value.displayName)", "PLAYER\(.key + 1)_TOKEN=\(.value.token)"),
  "SINGLE_EVENT_ID=\(.events.single.id)", "SINGLE_EVENT_TITLE=\(.events.single.title)",
  "PAST_EVENT_ID=\(.events.past.id)", "PAST_EVENT_TITLE=\(.events.past.title)",
  "SERIES_EVENT_ID=\(.events.series.eventId)", "SERIES_ID=\(.events.series.seriesId)", "SERIES_TITLE=\(.events.series.title)",
  "INVITE_TOKEN=\(.invite.token)", "INVITE_URL=\(.invite.url)", "INVITE_SCHEME_URL=\(.invite.scheme)"' "$ARTIFACTS/seed.json")
ENV_ARGS+=(-e "PLATFORM=$PLATFORM")

reset_device_state() {
  if [[ "$PLATFORM" == "ios" ]]; then
    xcrun simctl terminate "$DEVICE" "$APP_ID" >/dev/null 2>&1 || true
    xcrun simctl keychain "$DEVICE" reset >/dev/null 2>&1 || true
  else
    adb -s "$DEVICE" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
    adb -s "$DEVICE" shell svc wifi enable >/dev/null 2>&1 || true
    adb -s "$DEVICE" shell svc data enable >/dev/null 2>&1 || true
  fi
}

FAILED=()
PASSED=()
for flow in "$ROOT"/e2e/maestro/flows/*.yaml; do
  name="$(basename "$flow" .yaml)"
  if (( $# > 0 )); then
    match=0
    for filter in "$@"; do [[ "$name" == *"$filter"* ]] && match=1; done
    (( match )) || continue
  fi
  skip=0
  flow_tags="$(sed -n 's/^tags: *\[\(.*\)\].*/\1/p' "$flow" | head -1)"
  for tag in ${EXCLUDE_TAGS//,/ }; do
    [[ ",${flow_tags// /}," == *",$tag,"* ]] && skip=1
  done
  if (( skip )); then echo "[maestro] === $name (skipped, excluded tag)" >&2; continue; fi
  echo "[maestro] === $name" >&2
  reset_device_state
  out="$ARTIFACTS/$name"
  mkdir -p "$out"
  if maestro --device "$DEVICE" test "${ENV_ARGS[@]}" \
      --exclude-tags "$EXCLUDE_TAGS" \
      --format junit --output "$out/junit.xml" \
      --test-output-dir "$out" --debug-output "$out/debug" \
      "$flow" > "$out/maestro.log" 2>&1; then
    PASSED+=("$name")
  else
    FAILED+=("$name")
    tail -15 "$out/maestro.log" >&2
  fi
done

echo "[maestro] passed: ${#PASSED[@]}, failed: ${#FAILED[@]}" >&2
for f in "${FAILED[@]+"${FAILED[@]}"}"; do echo "[maestro] FAILED $f" >&2; done
(( ${#FAILED[@]} == 0 ))
