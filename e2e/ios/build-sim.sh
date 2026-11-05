#!/usr/bin/env bash
# Builds the iOS app for the simulator against a local backend, for Maestro runs.
# Usage: e2e/ios/build-sim.sh [API_BASE_URL]   (default http://localhost:8080 or $E2E_API_BASE_URL)
# Prints the resulting .app path on stdout.
#
# The checked-in Config.xcconfig and Info.plist are not touched. The script derives an E2E-only
# Info.plist (with the ATS local-networking exception) into build/ios-e2e-config and passes it,
# plus API_BASE_URL, as command-line build settings. Release builds keep using iosApp/Info.plist,
# which has no ATS exception.
# The build is ad-hoc signed (not unsigned): an unsigned simulator app has no entitlements and the
# Keychain-backed token store crashes with error -34018 at launch.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
API_BASE_URL="${1:-${E2E_API_BASE_URL:-http://localhost:8080}}"
DERIVED_DATA="$ROOT/build/ios-e2e"
CONFIG_DIR="$ROOT/build/ios-e2e-config"
E2E_PLIST="$CONFIG_DIR/Info-E2E.plist"

case "$API_BASE_URL" in
  http://localhost|http://localhost:*|http://127.0.0.1|http://127.0.0.1:*|https://*) ;;
  *) echo "API_BASE_URL must be https:// or http://localhost / http://127.0.0.1 (got: $API_BASE_URL)" >&2; exit 1 ;;
esac

mkdir -p "$CONFIG_DIR"
cp "$ROOT/iosApp/iosApp/Info.plist" "$E2E_PLIST"
/usr/libexec/PlistBuddy -c "Add :NSAppTransportSecurity dict" "$E2E_PLIST"
/usr/libexec/PlistBuddy -c "Add :NSAppTransportSecurity:NSAllowsLocalNetworking bool true" "$E2E_PLIST"

echo "[ios-e2e] building against $API_BASE_URL" >&2
cd "$ROOT"
xcodebuild build \
  -workspace iosApp/iosApp.xcworkspace \
  -scheme iosApp-Workspace \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath "$DERIVED_DATA" \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES \
  CODE_SIGN_STYLE=Manual CODE_SIGN_IDENTITY=- DEVELOPMENT_TEAM= CODE_SIGNING_ALLOWED=YES CODE_SIGNING_REQUIRED=NO \
  "API_BASE_URL=$API_BASE_URL" \
  "INFOPLIST_FILE=$E2E_PLIST" >&2

APP_PATH="$(find "$DERIVED_DATA/Build/Products/Debug-iphonesimulator" -maxdepth 1 -name '*.app' -print -quit)"
[[ -n "$APP_PATH" ]] || { echo "[ios-e2e] no .app produced" >&2; exit 1; }

BUILT_URL="$(/usr/libexec/PlistBuddy -c 'Print :API_BASE_URL' "$APP_PATH/Info.plist")"
[[ "$BUILT_URL" == "$API_BASE_URL" ]] || { echo "[ios-e2e] built API_BASE_URL is $BUILT_URL, expected $API_BASE_URL" >&2; exit 1; }
/usr/libexec/PlistBuddy -c 'Print :NSAppTransportSecurity:NSAllowsLocalNetworking' "$APP_PATH/Info.plist" >/dev/null

echo "$APP_PATH"
