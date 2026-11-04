# E2E groundwork (Maestro)

Pieces needed to drive the mobile UI with Maestro on the Android emulator and the iOS Simulator:
seeded backend data, an iOS build that talks to a local server, and stable test tags in the UI.

## 1. Local backend

Start Postgres and the server with the seed routes enabled:

```bash
docker run -d --name teamorg-e2e-pg -e POSTGRES_USER=teamorg -e POSTGRES_PASSWORD=teamorg \
  -e POSTGRES_DB=teamorg -p 55432:5432 postgres:15-alpine

E2E_SEED_ENABLED=true \
ALLOW_WEAK_JWT_SECRET=true \
SWISSVOLLEY_SYNC_ENABLED=false \
DATABASE_URL='jdbc:postgresql://localhost:55432/teamorg?user=teamorg&password=teamorg' \
./gradlew :server:run
```

`E2E_SEED_ENABLED` defaults to `false` (`e2e.seed-enabled` in `server/src/main/resources/application.conf`) and is
not passed through `docker-compose.yml`, so deployed environments never register `/e2e/seed/*`
(the path is a plain 404). Never set it in production.

## 2. Seed data

```bash
e2e/seed/seed.sh [--env] [BASE_URL]
```

Needs `bash`, `curl`, `jq`. `BASE_URL` defaults to `$E2E_BASE_URL` or `http://localhost:8080`.
Every run creates a new club, so runs do not interfere with each other:

- 5 users with unique emails (`e2e-<role>-<unixtime>@e2e.teamorg.test`) and the fixed password `Teamorg-E2E-1`
  (override with `E2E_PASSWORD`): manager (club manager), coach, 3 players
- one club, one team, coach and players added to the team
- one single future event (tomorrow 18:00 UTC) and one weekly series (first occurrence in 2 days,
  repeats weekly for about 8 weeks). All dates are derived from the current date.
- pending notifications from real actions: players create `event_new` notifications for themselves
  by the coach creating events, and the three player RSVPs (confirmed, declined, unsure) create three
  `response` notifications for the coach

Everything goes through the public API except club creation (needs a super-admin or a Stripe card),
which uses `POST /e2e/seed/club` (`{"name","managerEmail"}`), only available with `E2E_SEED_ENABLED=true`.

Output: JSON on stdout (logs on stderr), or `KEY=value` lines with `--env`, e.g. to feed Maestro:

```bash
e2e/seed/seed.sh > seed.json
maestro test -e EMAIL="$(jq -r .coach.email seed.json)" -e PASSWORD="$(jq -r .password seed.json)" flows/
```

JSON shape: `baseUrl`, `password`, `runId`, `club{id,name}`, `team{id,name}`, `manager|coach{id,email,displayName,token}`,
`players[3]{id,email,displayName,token}`, `events.single{id,title,startAt}`,
`events.series{eventId,seriesId,title,firstStartAt}`, `notifications{coachUnread,player1Unread}`.

The auth rate limit is 20 requests per minute per IP; one seed run uses 5.

## 3. iOS simulator build against localhost

```bash
e2e/ios/build-sim.sh [API_BASE_URL]   # prints the .app path
xcrun simctl install booted "$(e2e/ios/build-sim.sh)"
```

The script builds Debug for the simulator into `build/ios-e2e` with `API_BASE_URL` set (default
`http://localhost:8080`, only `https://`, `http://localhost` and `http://127.0.0.1` are accepted).
The checked-in `Config.xcconfig` and `Info.plist` are not edited: the script derives
`build/ios-e2e-config/Info-E2E.plist` (a copy of `Info.plist` plus `NSAppTransportSecurity > NSAllowsLocalNetworking`)
and passes it as `INFOPLIST_FILE`. Release and TestFlight builds keep `iosApp/Info.plist`, which has no ATS entry.
The shared HTTP client (`requireSecureBaseUrl`) allows `http://` only for `localhost`, `127.0.0.1` and `10.0.2.2`,
and the iOS app reads the same `API_BASE_URL` key, so the simulator build is accepted.
For Android use the emulator alias `http://10.0.2.2:8080` (already how the debug build and instrumented tests work).

## 4. Test tags

`Modifier.testTagsAsResourceId()` is applied once at the app root (`TeamorgApp.kt`) and also inside every
sheet, dialog and popup menu, because those render in their own window on Android and do not inherit the
root flag. When adding a sheet or dialog, put `Modifier.testTagsAsResourceId()` on its content.

- Android: a `testTag` becomes the UI Automator `resource-id` (Maestro `id:`).
- iOS: Compose Multiplatform 1.10 maps every `testTag` to the element's `accessibilityIdentifier`
  (Maestro `id:`) by default. No `ComposeUIViewController` option has to be enabled, the accessibility tree is
  always synced. Containers that carry a `testTag` are exposed even without text.

Tag naming follows `type_name` (`btn_`, `tf_`, `txt_`, `chip_`, `switch_`, `sheet_`, `menu_`, `fab_`, `row_`,
`list_`) plus an id suffix for list items (`inbox_row_{id}`, `member_response_row_{userId}`,
`absence_card_{id}`, `team_card_{id}`, `club_member_{userId}`). The bottom bar uses `bottom_nav_events|teams|inbox|profile`.
Compact RSVP pills (event list) use `btn_rsvp_*_compact`; the event detail ones are `btn_rsvp_going|declined|unsure`.

## 5. Known gotchas

- iOS and Android ask for notification permission once, right after the first login or registration. Flows must
  dismiss the system alert after signing in (`- tapOn: "Erlauben"` / `"Allow"`, optional) before using `id:` selectors.
- The simulator build is ad-hoc signed on purpose; an unsigned app crashes at launch with Keychain error -34018.
- Logout: `profile_btn_logout` on the own profile (also shown on the no-team Profile tab); `btn_logout` on the invite screen.
