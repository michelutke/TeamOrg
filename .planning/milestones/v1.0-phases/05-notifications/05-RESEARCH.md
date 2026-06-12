# Phase 05: Notifications - Research

**Researched:** 2026-03-26
**Domain:** Push notifications (OneSignal), reminder scheduling, in-app inbox, per-team settings
**Confidence:** HIGH

## Summary

This phase adds push notifications (iOS + Android via OneSignal SDK 5.x) and an in-app inbox to an existing KMP (Kotlin Multiplatform) project using Ktor server, Compose Multiplatform, SQLDelight, and Koin DI. The primary complexity is in server-side: OneSignal REST API integration, a reminder scheduler (poll-from-DB pattern), dedup guard, and per-team notification settings storage. The client side is mostly standard KMP patterns already established in phases 3–4.

OneSignal's SDK 5.x uses `OneSignal.login(externalUserId)` to tie device registrations to your user IDs — no push token management needed. The server calls OneSignal's REST API to send pushes by `external_id`. The project already has background coroutine job patterns (EventMaterialisationJob, AbwesenheitBackfillJob) that the reminder scheduler can follow exactly.

**Primary recommendation:** Follow established project patterns exactly. Server-side PushService wraps Ktor HttpClient calls to OneSignal REST API. ReminderSchedulerJob polls `notifications` table for due reminders, fires, marks sent — same coroutine loop pattern as EventMaterialisationJob.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Notification triggers & batching**
- Event edit notifications: Claude's discretion on debounce vs immediate
- Coach response mode: pick ONE — per-response OR pre-event summary (not both simultaneously)
- Absence notifications to coach: one summary notification listing all affected events (not per-event)
- No quiet hours — rely on device-level DND
- No-duplicate guard: server-side dedup before sending
- Removed-member guard: check membership before sending

**Reminder configuration**
- Global default + per-event override
- Free-form time picker for lead time selection (not predefined options)
- Default for new users: 2 hours before
- Per-event override: "Reminder" row on EventDetailScreen — tap to set custom lead time for that event
- Per-event override: stored per (user, event)

**In-app inbox**
- Flat reverse-chronological list (like GitHub notifications)
- Tap notification → deep-link to related screen
- Read state: mark read on tap + "Mark all as read" button
- Unread badge count on Inbox tab in bottom nav
- 90-day retention, auto-cleanup
- Existing PlaceholderScreen("Inbox") ready to replace

**Settings screen**
- Entry point: gear icon in Inbox screen toolbar
- Toggles grouped by category: Events (new/edit/cancel), Responses (per-response/summary), Reminders, Absences
- Per-team settings (not global)
- Coach response mode toggle per-team in same settings screen

### Claude's Discretion
- Event edit debounce timing (if any)
- Exact notification copy/wording
- Time picker component choice
- Loading states and error handling
- Notification channel setup (Android)
- Settings screen visual layout/spacing

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| NO-01 | Player notified when new event created for their team | PushService.sendToUsers() called from eventRoutes on creation; membership check before send |
| NO-02 | Player receives configurable reminder before event (free-form lead time) | ReminderSchedulerJob polls notification_reminders table; lead time stored per (user, event) with server fallback to user default |
| NO-03 | Player notified when event is edited (time/location change) | PushService called from eventRoutes PUT handler; debounce decision at Claude's discretion |
| NO-04 | Player notified when event is cancelled | PushService called from eventRoutes cancel handler |
| NO-05 | Coach can choose per-response notifications (each RSVP) | notification_settings.coach_response_mode = 'per_response'; trigger in attendanceRoutes |
| NO-06 | Coach can choose pre-event summary of no-response players | notification_settings.coach_response_mode = 'summary'; ReminderSchedulerJob fires summary N hours before event |
| NO-07 | Coach notified when player adds/modifies Abwesenheit affecting upcoming events | PushService called from abwesenheitRoutes; one summary notification listing affected events |
| NO-08 | Each user can enable/disable notification types independently | notification_settings table per (user, team); NotificationSettingsScreen reads/writes via API |
| NO-09 | User can configure reminder lead time | reminder_lead_minutes stored in notification_settings (global default) and event_reminder_overrides (per-event); free-form picker |
| NO-10 | Push on iOS + Android via OneSignal | Android SDK 5.7.6 in TeamorgApplication.onCreate(); iOS SDK 5.5.0 in AppDelegate; PushService wraps OneSignal REST API |
| NO-11 | In-app notification inbox accessible without push enabled | notifications table on server; NotificationCacheManager (SQLDelight) on client; InboxScreen replaces PlaceholderScreen |
| NO-12 | No duplicate notifications; no notifications to removed members | Server-side: dedup column (idempotency_key) in notifications table; membership check via TeamRolesTable before every send |
</phase_requirements>

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| OneSignal Android SDK | 5.7.6 | Push on Android | Locked decision; Kotlin-native, coroutine-based |
| OneSignal iOS XCFramework | 5.5.0 | Push on iOS | Locked decision; SPM distribution |
| Ktor HttpClient | 3.3.3 (existing) | Server calls OneSignal REST API | Already in project; no extra dependency |
| SQLDelight | 2.0.2 (existing) | In-app notification inbox local cache | Established pattern (EventCacheManager, AttendanceCacheManager) |
| multiplatform-settings | 1.3.0 (existing) | Extended for notification prefs | Already used for token/userId storage |
| Exposed + Flyway | 0.54.0 / 10.18.0 (existing) | Notification + settings DB tables | All server DB uses these |
| Koin | 4.1.1 (existing) | DI for PushService, NotificationRepository | All services registered via Koin |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx.datetime | 0.6.1 (existing) | Instant arithmetic for lead time calc | Already required for KMP iOS |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| OneSignal REST API via Ktor HttpClient | onesignal-java-client | Extra dependency; plain HTTP is sufficient and keeps stack minimal |
| Poll-from-DB reminder scheduler | kjob / ktor-scheduler | External deps with lock/cron overhead; poll loop at 1-minute cadence is proven in this codebase |

**Installation (Android):**
```kotlin
// composeApp/build.gradle.kts (androidMain dependencies)
implementation("com.onesignal:OneSignal:5.7.6")
```

**Installation (iOS — SPM in Xcode):**
```
https://github.com/OneSignal/OneSignal-XCFramework
```
Targets: OneSignalFramework → composeApp target; OneSignalExtension → Notification Service Extension target.

---

## Architecture Patterns

### Recommended Project Structure
```
server/src/main/kotlin/ch/teamorg/
├── infra/
│   └── ReminderSchedulerJob.kt       # coroutine loop, polls DB for due reminders
├── routes/
│   └── NotificationRoutes.kt         # GET /notifications, POST /notifications/read-all, GET/PUT /notifications/settings
├── domain/repositories/
│   └── NotificationRepository.kt     # interface + impl: create, list, markRead, getSettings, saveSettings

shared/src/commonMain/kotlin/ch/teamorg/
├── domain/
│   ├── Notification.kt               # domain model
│   └── NotificationSettings.kt       # domain model
├── data/
│   └── NotificationCacheManager.kt   # SQLDelight inbox cache
├── repository/
│   └── NotificationRepository.kt     # interface (KMP)
├── data/repository/
│   └── NotificationRepositoryImpl.kt # Ktor HTTP implementation

composeApp/src/commonMain/kotlin/ch/teamorg/
├── ui/inbox/
│   ├── InboxScreen.kt                # replaces PlaceholderScreen("Inbox")
│   ├── InboxViewModel.kt
│   ├── NotificationSettingsScreen.kt
│   └── NotificationSettingsViewModel.kt
└── di/
    └── UiModule.kt                   # add InboxViewModel, NotificationSettingsViewModel

server/src/main/resources/db/migrations/
└── V9__create_notifications.sql      # notifications, notification_settings, event_reminder_overrides
```

### Pattern 1: PushService (Server-side Push Dispatch)
**What:** A Koin-injected service that wraps OneSignal REST API calls via the existing Ktor HttpClient.
**When to use:** Called from any route handler (eventRoutes, attendanceRoutes, abwesenheitRoutes) that triggers a notification.
**Example:**
```kotlin
// Source: OneSignal REST API docs (https://documentation.onesignal.com/reference/push-notification)
// Registered: single<PushService> { PushServiceImpl(get()) } in Koin appModule
class PushServiceImpl(private val client: HttpClient) : PushService {
    override suspend fun sendToUsers(
        userIds: List<String>,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        if (userIds.isEmpty()) return
        client.post("https://api.onesignal.com/notifications?c=push") {
            header("Authorization", "Key ${System.getenv("ONESIGNAL_API_KEY")}")
            contentType(ContentType.Application.Json)
            setBody(mapOf(
                "app_id" to System.getenv("ONESIGNAL_APP_ID"),
                "include_aliases" to mapOf("external_id" to userIds),
                "target_channel" to "push",
                "contents" to mapOf("en" to body),
                "headings" to mapOf("en" to title),
                "data" to data
            ))
        }
    }
}
```

### Pattern 2: ReminderSchedulerJob (Poll-from-DB)
**What:** Background coroutine that polls `notification_reminders` table every minute for reminders due to fire.
**When to use:** Reminder scheduling for NO-02; pre-event coach summary for NO-06.
**Example:**
```kotlin
// Source: EventMaterialisationJob.kt pattern in this codebase
fun Application.startReminderSchedulerJob() {
    launch(Dispatchers.IO) {
        while (isActive) {
            delay(1.minutes)
            try {
                reminderRepository.fireDueReminders()  // marks as sent + calls PushService
            } catch (e: Exception) {
                logger.error("Reminder scheduler error", e)
            }
        }
    }
}
```

### Pattern 3: Server-side Dedup (NO-12)
**What:** `notifications` table has a unique `idempotency_key` column. Before inserting + sending, check for existing record with same key.
**When to use:** Every notification creation path.
**Key:** Compose as `"{type}:{targetUserId}:{entityId}:{epochDay}"` — prevents duplicate on edit/resave.

### Pattern 4: OneSignal External User ID (Login/Logout)
**What:** On login call `OneSignal.login(userId)`. On logout call `OneSignal.logout()`. Server targets users by their `userId` UUID string.
**When to use:** After successful auth on Android and iOS.

### Pattern 5: In-App Inbox (SQLDelight cache)
**What:** Server returns paginated notification list. Client caches in SQLDelight `cached_notification` table. InboxViewModel loads from cache first, refreshes from server.
**When to use:** InboxScreen mount + pull-to-refresh.
**Example table:**
```sql
-- Notification.sq (SQLDelight)
CREATE TABLE cached_notification (
    id TEXT NOT NULL PRIMARY KEY,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    entity_id TEXT,
    entity_type TEXT,
    is_read INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);
```

### Anti-Patterns to Avoid
- **Storing OneSignal push tokens manually:** OneSignal SDK 5.x manages subscriptions internally; use `login(userId)` only.
- **Sending push per-absence-event:** Locked decision: one summary notification for all affected events.
- **Both response modes simultaneously:** Locked decision: binary per-team setting, not dual mode.
- **Global notification settings:** Settings are per-team, not global.
- **Blocking route handlers on push send:** Push dispatch should be `launch(Dispatchers.IO)` inside route handler — fire-and-forget so HTTP response is not delayed.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Push delivery to iOS/Android | Custom FCM/APNs integration | OneSignal SDK 5.x + REST API | Certificate management, delivery guarantees, cross-platform handled |
| Token lifecycle (register/unregister on reinstall) | Manual token DB | OneSignal.login(userId) | OneSignal handles subscription lifecycle internally |
| Reminder timing precision | `delay(exactMs)` calculated once at startup | Poll-from-DB at 1-minute cadence | Process restart safety; matches established codebase pattern |

**Key insight:** OneSignal SDK 5.x removes all manual push token management. The server never sees or stores device tokens — it only sends to `external_id` (= your userId UUID).

---

## Common Pitfalls

### Pitfall 1: OneSignal.login() Not Called After Auth
**What goes wrong:** Push tokens are registered anonymously; server cannot target the user by `external_id`.
**Why it happens:** SDK initializes in Application.onCreate() before user logs in; `login()` must be called post-authentication.
**How to avoid:** Call `OneSignal.login(userId)` in Android `AuthRepositoryImpl` after successful login response; same in iOS.
**Warning signs:** OneSignal dashboard shows anonymous subscriptions, `include_aliases` API calls return errors.

### Pitfall 2: iOS Notification Service Extension Not Added
**What goes wrong:** iOS notifications arrive but cannot show images or are not counted; notification permission may not work correctly.
**Why it happens:** iOS requires a separate NSE target for notification handling.
**How to avoid:** Add Notification Service Extension target in Xcode; assign `OneSignalExtension` package to it.
**Warning signs:** Builds fine but push permission prompt never appears.

### Pitfall 3: Android Notification Channel Not Created
**What goes wrong:** Notifications silently dropped on Android 8+.
**Why it happens:** Android requires channel to be created before notifications arrive.
**How to avoid:** Claude's discretion on channel setup — create at least one channel in `TeamorgApplication.onCreate()` before OneSignal init, or configure via OneSignal Dashboard.
**Warning signs:** Notifications not displayed on Android 8+ physical device.

### Pitfall 4: Reminder Firing on Cancelled/Past Events
**What goes wrong:** Reminders fire for cancelled or already-past events.
**Why it happens:** Reminder rows created at event creation time without checking subsequent cancellations.
**How to avoid:** `fireDueReminders()` joins with `events` table, skips rows where `events.status = 'cancelled'` or `events.start_at < NOW()`.

### Pitfall 5: Removed Member Receiving Push
**What goes wrong:** Notification sent to user after they were removed from team.
**Why it happens:** Recipient list built at trigger time without fresh membership check.
**How to avoid:** Always query `TeamRolesTable` for current active members immediately before calling `PushService.sendToUsers()`.

### Pitfall 6: Duplicate Notifications on Event Edit
**What goes wrong:** Coach saves event twice in quick succession; two "event edited" pushes arrive.
**Why it happens:** Edit triggers notification immediately each time.
**How to avoid:** Use `idempotency_key` unique constraint in `notifications` table; compose key from `"event_edit:{eventId}:{epochHour}"` — dedup to once per hour per event.

### Pitfall 7: iOS KMP Module Export for OneSignal
**What goes wrong:** OneSignal is an iOS-native framework; it cannot be called from shared KMP code.
**Why it happens:** Developers try to put push registration in shared code.
**How to avoid:** PushService registration (OneSignal.login/logout) lives in `iosMain` and `androidMain` actual implementations. Server does all targeting. Shared KMP only holds the NotificationRepository interface and domain models.

---

## Code Examples

### Android Init (TeamorgApplication.kt)
```kotlin
// Source: https://documentation.onesignal.com/docs/en/android-sdk-setup
class TeamorgApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Existing Koin init...
        startKoin { androidContext(this@TeamorgApplication); modules(sharedModule, uiModule) }

        // OneSignal init — AFTER Koin so UserPreferences is available
        OneSignal.Debug.logLevel = LogLevel.NONE  // set VERBOSE during dev
        OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)
    }
}
```

### iOS Init (AppDelegate in iOSApp.swift)
```swift
// Source: https://documentation.onesignal.com/docs/en/ios-sdk-setup
class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        MainViewControllerKt.doInitKoin()
        OneSignal.Debug.setLogLevel(.LL_NONE)  // set LL_VERBOSE during dev
        OneSignal.initialize(Env.oneSignalAppId, withLaunchOptions: launchOptions)
        return true
    }
}
```

### Server: Send Push with Dedup
```kotlin
// Called from route handlers — fire-and-forget
suspend fun notifyAndStore(
    recipientUserIds: List<UUID>,
    type: String,
    title: String,
    body: String,
    entityId: String?,
    idempotencyKey: String,
    notificationRepo: NotificationRepository,
    pushService: PushService
) {
    val eligibleIds = recipientUserIds.filter { userId ->
        notificationRepo.isUserEligible(userId, type) &&    // settings check
        !notificationRepo.isDuplicate(idempotencyKey, userId) // dedup check
    }
    if (eligibleIds.isEmpty()) return

    notificationRepo.createBatch(eligibleIds, type, title, body, entityId, idempotencyKey)
    pushService.sendToUsers(eligibleIds.map { it.toString() }, title, body)
}
```

### V9 Migration Outline
```sql
-- V9__create_notifications.sql
CREATE TABLE notifications (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type             TEXT NOT NULL,        -- 'event_new','event_edit','event_cancel','reminder','response','absence'
    title            TEXT NOT NULL,
    body             TEXT NOT NULL,
    entity_id        UUID NULL,
    entity_type      TEXT NULL,            -- 'event','abwesenheit'
    is_read          BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key  TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX notifications_dedup ON notifications(user_id, idempotency_key);
CREATE INDEX notifications_user_unread ON notifications(user_id, is_read, created_at DESC);

CREATE TABLE notification_settings (
    user_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    team_id              UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    events_new           BOOLEAN NOT NULL DEFAULT TRUE,
    events_edit          BOOLEAN NOT NULL DEFAULT TRUE,
    events_cancel        BOOLEAN NOT NULL DEFAULT TRUE,
    reminders_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    reminder_lead_minutes INTEGER NOT NULL DEFAULT 120,   -- 2h default
    coach_response_mode  TEXT NOT NULL DEFAULT 'per_response'
                          CHECK (coach_response_mode IN ('per_response','summary')),
    absences_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, team_id)
);

CREATE TABLE event_reminder_overrides (
    user_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id             UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    reminder_lead_minutes INTEGER NULL,   -- NULL = use settings default; -1 = disabled for this event
    PRIMARY KEY (user_id, event_id)
);

-- Scheduled reminder rows (one per eligible user per event, generated at event creation/edit)
CREATE TABLE notification_reminders (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id   UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    fire_at    TIMESTAMPTZ NOT NULL,
    sent       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX notification_reminders_uniq ON notification_reminders(user_id, event_id);
CREATE INDEX notification_reminders_due ON notification_reminders(fire_at, sent) WHERE NOT sent;
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual push token registration (register endpoint + store tokens) | OneSignal.login(externalUserId) — SDK manages subscriptions | SDK 5.x (2023) | No push_tokens table needed; server targets by userId string |
| OneSignal PlayerId / PlayerID targeting | `include_aliases.external_id` | SDK 5.x | Cleaner user identity model; external_id = your userId |

**Deprecated/outdated:**
- `setExternalUserId()`: Replaced by `OneSignal.login()` in SDK 5.x.
- `include_player_ids` in REST API: Replaced by `include_aliases.external_id`.
- `include_subscription_ids`: Use `include_aliases` instead for user-level targeting.

---

## Open Questions

1. **ONESIGNAL_APP_ID and ONESIGNAL_API_KEY env vars**
   - What we know: Server calls OneSignal REST API; needs app ID + API key.
   - What's unclear: How/where to inject these into the Ktor server config (application.conf or environment).
   - Recommendation: Add to `application.conf` as `onesignal.appId` and `onesignal.apiKey`; read via `environment.config.property()` in Application.module(). Provide in `.env` for local dev.

2. **ONESIGNAL_APP_ID in Android build**
   - What we know: `TeamorgApplication` needs the App ID at init time.
   - What's unclear: Whether `BuildConfig.ONESIGNAL_APP_ID` is already set up or needs to be added to `build.gradle.kts`.
   - Recommendation: Add `buildConfigField("String", "ONESIGNAL_APP_ID", "\"${localProperties["onesignal.appId"]}\"")` in composeApp's android block.

3. **Event edit debounce**
   - What we know: CONTEXT.md says Claude's discretion.
   - What's unclear: Whether idempotency_key keyed to epoch-hour is sufficient or a server-side debounce timer is needed.
   - Recommendation: Use idempotency_key `"event_edit:{eventId}:{epochHour}"` — effectively debounces to once per hour with zero added complexity.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Kotlin Test + Ktor TestApplication + Testcontainers (PostgreSQL) |
| Config file | None — IntegrationTestBase.kt provides withTeamorgTestApplication() |
| Quick run command | `./gradlew :server:test --tests "ch.teamorg.routes.NotificationRoutesTest" -x :composeApp:generateComposeResClass` |
| Full suite command | `./gradlew :server:test -x :composeApp:generateComposeResClass` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| NO-01 | Event creation triggers notification row in DB | integration | `./gradlew :server:test --tests "*.NotificationRoutesTest.createEvent_*"` | Wave 0 |
| NO-02 | Reminder row created with correct fire_at; ReminderSchedulerJob fires due rows | unit + integration | `./gradlew :server:test --tests "*.NotificationRoutesTest.reminder_*"` | Wave 0 |
| NO-03 | Event edit creates notification (dedup within epoch-hour) | integration | `./gradlew :server:test --tests "*.NotificationRoutesTest.editEvent_*"` | Wave 0 |
| NO-04 | Event cancel creates notification for all team members | integration | `./gradlew :server:test --tests "*.NotificationRoutesTest.cancelEvent_*"` | Wave 0 |
| NO-05 | Per-response mode: coach gets notification on each RSVP | integration | `./gradlew :server:test --tests "*.NotificationRoutesTest.coachResponse_perResponse"` | Wave 0 |
| NO-06 | Summary mode: coach gets no-response summary before event | integration | `./gradlew :server:test --tests "*.NotificationRoutesTest.coachResponse_summary"` | Wave 0 |
| NO-07 | Abwesenheit change generates single summary to coach | integration | `./gradlew :server:test --tests "*.NotificationRoutesTest.absence_*"` | Wave 0 |
| NO-08 | Disabled setting prevents notification creation | integration | `./gradlew :server:test --tests "*.NotificationRoutesTest.settings_*"` | Wave 0 |
| NO-09 | Reminder lead time override respected per event | unit | `./gradlew :server:test --tests "*.NotificationRoutesTest.reminderOverride_*"` | Wave 0 |
| NO-10 | PushService calls OneSignal REST API (mock) | unit | `./gradlew :server:test --tests "*.PushServiceTest.*"` | Wave 0 |
| NO-11 | GET /notifications returns inbox; mark-read endpoints work | integration | `./gradlew :server:test --tests "*.NotificationRoutesTest.inbox_*"` | Wave 0 |
| NO-12 | Dedup: same idempotency_key not inserted twice; removed member excluded | integration | `./gradlew :server:test --tests "*.NotificationRoutesTest.dedup_*" --tests "*.NotificationRoutesTest.removedMember_*"` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :server:test --tests "ch.teamorg.routes.NotificationRoutesTest" -x :composeApp:generateComposeResClass`
- **Per wave merge:** `./gradlew :server:test -x :composeApp:generateComposeResClass`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `server/src/test/kotlin/ch/teamorg/routes/NotificationRoutesTest.kt` — covers NO-01 through NO-12
- [ ] `server/src/test/kotlin/ch/teamorg/PushServiceTest.kt` — unit tests with mocked HttpClient for NO-10

---

## Sources

### Primary (HIGH confidence)
- OneSignal Android SDK Docs (https://documentation.onesignal.com/docs/en/android-sdk-setup) — init code, login(), notification channels
- OneSignal iOS SDK Docs (https://documentation.onesignal.com/docs/en/ios-sdk-setup) — SPM setup, AppDelegate init, login()
- OneSignal REST API Reference (https://documentation.onesignal.com/reference/push-notification) — include_aliases.external_id, request structure
- Codebase: EventMaterialisationJob.kt, AbwesenheitBackfillJob.kt — coroutine loop pattern
- Codebase: Attendance.sq, Event.sq — SQLDelight table/query pattern
- Codebase: SharedModule.android.kt / ios.kt — Koin registration pattern

### Secondary (MEDIUM confidence)
- GitHub releases: OneSignal-Android-SDK (https://github.com/OneSignal/OneSignal-Android-SDK/releases) — confirmed 5.7.6 latest
- GitHub releases: OneSignal-XCFramework (https://github.com/OneSignal/OneSignal-XCFramework/releases) — confirmed 5.5.0 latest

### Tertiary (LOW confidence)
- WebSearch results on Ktor scheduler patterns — corroborate poll-from-DB recommendation but not verified against official Ktor docs

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — versions confirmed from GitHub releases; all other deps are existing project deps
- Architecture: HIGH — follows established codebase patterns directly (job loop, Koin DI, SQLDelight cache, repository pattern)
- Pitfalls: MEDIUM-HIGH — OneSignal SDK 5.x login() requirement verified from official docs; Android channel and iOS NSE requirements verified; dedup/timing pitfalls derived from codebase analysis

**Research date:** 2026-03-26
**Valid until:** 2026-04-26 (OneSignal SDK versions; REST API structure is stable)
