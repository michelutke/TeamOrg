# Phase 03: Event Scheduling - Research

**Researched:** 2026-03-19
**Domain:** KMP event scheduling — Ktor backend, Exposed ORM, Compose Multiplatform calendar UI, SQLDelight cache, kotlinx-datetime
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Calendar UI:** Use `kizitonwose/calendar-compose` (`compose-multiplatform` artifact) for month and week views. Style with M3 tokens. Do not build a custom calendar grid.
- **Timezone handling:** Store UTC on server. Display in device local timezone via `kotlinx-datetime`. Create/edit form shows timezone abbreviation label (e.g. "CET") — informational only, no picker. Event list and detail: show local time only, no timezone label.
- **Offline browsing:** Cache fetched events in SQLDelight for read-only access. Cache window: 3 months upcoming. Read-only in Phase 3 (no mutation queue). If offline and cache empty: show empty state, not error.
- **Attendance card placeholder:** Event detail screen renders placeholder card "Attendance — Coming soon" in the correct layout position. Phase 4 fills it in without restructuring.

### Claude's Discretion
- Exact kizitonwose API configuration (HorizontalCalendar vs VerticalCalendar, etc.)
- SQLDelight schema structure for events cache (mapping from API response)
- Background job implementation details (materialisation service)
- Error state designs for network failures

### Deferred Ideas (OUT OF SCOPE)
- None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| ES-01 | Coach creates event: title, type, start/end datetime, location, description | Create form (S4) + POST /events; Exposed table EventsTable |
| ES-02 | Events span multiple days | start_at / end_at on different dates; calendar week view spans columns; list shows date range |
| ES-03 | Coach creates recurring events (daily/weekly/custom, optional end date) | event_series + lazy materialisation; S5 recurring pattern bottom sheet |
| ES-04 | Coach assigns event to one or more teams | event_teams junction table; multi-select in form |
| ES-05 | Coach targets event at specific sub-groups | event_subgroups junction table; sub-group multi-select in form (shown after team selected) |
| ES-06 | Coach sets min required attendees (optional) | min_attendees column; number field in form options section |
| ES-07 | Coach edits event; players notified | PATCH /events/{id} with scope; domain event emission for Phase 5 |
| ES-08 | Coach cancels event; players notified; responses preserved | POST /events/{id}/cancel; status = cancelled; domain event emission |
| ES-09 | Edit recurring: this / this+future / all | Scope sheet S6; server handles via series_override / series split / template update |
| ES-10 | Coach duplicates event | POST /events/{id}/duplicate → pre-fill create form |
| ES-11 | Player sees chronological list of upcoming events | GET /users/me/events; EventListScreen; LazyColumn pattern |
| ES-12 | Player views event details | GET /events/{id}; EventDetailScreen with placeholder attendance card |
| ES-13 | Player filters events by team and event type | Filter chip row; params: type, teamId on GET /users/me/events |
| ES-14 | Player sees calendar view (month/week) | CalendarScreen; kizitonwose HorizontalCalendar + WeekCalendar |
| ES-15 | Recurring event expansion server-side | Materialisation background job; Ktor coroutine scheduled on Application scope |
| ES-16 | Events stored UTC, displayed in local timezone | Exposed TIMESTAMPTZ; kotlinx-datetime Instant → toLocalDateTime(TimeZone.currentSystemDefault()) |
</phase_requirements>

---

## Summary

Phase 3 is a large, multi-layer phase spanning: Ktor backend (new DB tables, 3+ API endpoint groups, a background job), shared KMP domain/repository layer, SQLDelight offline cache, and a rich Compose Multiplatform UI (event list, event detail, create/edit form with bottom sheets, calendar month+week view).

The most technically novel aspects are: (1) recurring event materialisation — lazy expansion into concrete `events` rows via a rolling 12-month background job on the Ktor `Application` coroutine scope, with three edit-scope mutation strategies; (2) the calendar UI via `kizitonwose/calendar-compose` (`compose-multiplatform` artifact), which is confirmed KMP-compatible and handles layout entirely — we only provide `dayContent` composables; (3) UTC ↔ local timezone conversion via `kotlinx-datetime` `Instant.toLocalDateTime(TimeZone.currentSystemDefault())`.

The codebase already provides: `Screen.Events` and `Screen.Calendar` routes (currently `PlaceholderScreen`), the `sub_groups` and `sub_group_members` DB tables (V6 migration), and established ViewModel/Repository/Koin patterns to follow verbatim. Navigation3 is in use with the manual backstack pattern; all new screens hook into `AppNavigation.kt` using the same `when(screen)` dispatch.

**Primary recommendation:** Work backend-first (migrations → tables → repositories → routes → materialisation job), then shared KMP domain layer, then UI layer (list → detail → create/edit form → calendar). Keep recurring logic entirely server-side; client is a dumb consumer.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.kizitonwose.calendar:compose-multiplatform` | **2.10.0** | Month + week calendar composables for KMP | Confirmed KMP-compatible; handles all layout; only supply dayContent lambdas |
| `kotlinx-datetime` | **0.6.1** (pinned) | UTC↔local timezone conversion in shared domain | Already in project; pinned due to K/N IR crash on 0.7.x |
| `app.cash.sqldelight` (runtime + drivers) | **2.0.2** | Offline event cache | Already in project; established pattern |
| `io.ktor:ktor-server-*` | **3.3.3** | Backend routes, auth, JSON | Already in project |
| `org.jetbrains.exposed:*` + `exposed-java-time` | project version | ORM for events/series tables | Already in project; `java.time.Instant` for TIMESTAMPTZ |
| Flyway | project version | DB migrations V7+ | Already in project; migrations in `server/src/main/resources/db/migrations/` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `koin-compose` / `koin-core` | **4.1.1** | DI wiring for new ViewModels and repositories | Always — follows existing `factory { }` / `single { }` pattern |
| `kotlinx-coroutines` | **1.10.2** | Background materialisation job on Application scope | Materialisation job only |
| `kotlin-test` + Testcontainers | project versions | Server-side integration tests | Follow `IntegrationTestBase` pattern |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `compose-multiplatform` artifact | `compose` (Android-only artifact) | Android-only; breaks iOS — use multiplatform artifact |
| Ktor coroutine background job | Quartz / external scheduler | YAGNI — PROJECT.md explicitly states in-process coroutines are sufficient for MVP |
| Server-side timezone conversion | Client-only timezone label | Spec requires UTC storage + client display — don't deviate |

**Installation (new dependency only):**
```kotlin
// shared/build.gradle.kts — commonMain dependencies
implementation("com.kizitonwose.calendar:compose-multiplatform:2.10.0")
```

Add to `libs.versions.toml`:
```toml
[versions]
kizitonwose-calendar = "2.10.0"

[libraries]
kizitonwose-calendar-compose = { module = "com.kizitonwose.calendar:compose-multiplatform", version.ref = "kizitonwose-calendar" }
```

**Version verification:** `compose-multiplatform:2.10.0` confirmed from GitHub releases (Jan 17 2026). Uses Kotlin 2.3.0 + AGP 8.13.2 — matches project stack exactly.

---

## Architecture Patterns

### Recommended Project Structure

```
server/src/main/kotlin/ch/teamorg/
├── db/tables/
│   ├── EventsTable.kt           # events + event_series + event_teams + event_subgroups
│   └── (SubGroupsTable.kt)      # already exists
├── domain/models/
│   └── Event.kt                 # Event, EventSeries, EventWithTeams domain models
├── domain/repositories/
│   ├── EventRepository.kt
│   └── EventRepositoryImpl.kt
├── routes/
│   └── EventRoutes.kt
├── infra/
│   └── EventMaterialisationJob.kt  # coroutine background job
└── plugins/
    └── Routing.kt               # add eventRoutes()

shared/src/commonMain/kotlin/ch/teamorg/
├── domain/
│   ├── Event.kt                 # shared Event data class (mirrors API response)
│   └── EventSeries.kt
├── repository/
│   └── EventRepository.kt       # interface: suspend fun (): Result<T>
├── data/repository/
│   └── EventRepositoryImpl.kt   # Ktor client impl
└── di/
    └── (add event entries to sharedModule)

composeApp/src/commonMain/kotlin/ch/teamorg/ui/
├── events/
│   ├── EventListScreen.kt
│   ├── EventListViewModel.kt
│   ├── EventDetailScreen.kt
│   ├── EventDetailViewModel.kt
│   ├── CreateEditEventScreen.kt
│   ├── CreateEditEventViewModel.kt
│   └── RecurringPatternSheet.kt    # bottom sheet S5
├── calendar/
│   ├── CalendarScreen.kt           # hosts month + week tabs
│   └── CalendarViewModel.kt
└── navigation/
    ├── Screen.kt                   # add EventDetail(id), CreateEvent, EditEvent(id) screens
    └── AppNavigation.kt            # replace PlaceholderScreen for Events + Calendar
```

### Pattern 1: ViewModel State + Flow (established project pattern)
**What:** `data class *State` held in `MutableStateFlow`; one-off navigation events in `MutableSharedFlow`
**When to use:** Every ViewModel in this phase

```kotlin
// Source: existing TeamRosterViewModel.kt pattern
data class EventListState(
    val events: List<Event> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTeamId: String? = null,
    val selectedType: EventType? = null
)

class EventListViewModel(
    private val eventRepository: EventRepository
) : ViewModel() {
    private val _state = MutableStateFlow(EventListState())
    val state = _state.asStateFlow()
}
```

### Pattern 2: Repository with Result<T> (established project pattern)
**What:** Interface in `shared/domain/repository/`; impl in `shared/data/repository/`; `suspend fun (): Result<T>`
**When to use:** All EventRepository, SubgroupRepository methods

```kotlin
// Source: existing TeamRepositoryImpl.kt pattern
interface EventRepository {
    suspend fun getMyEvents(from: String? = null, to: String? = null,
                            type: String? = null, teamId: String? = null): Result<List<Event>>
    suspend fun getEventDetail(id: String): Result<Event>
    suspend fun createEvent(request: CreateEventRequest): Result<Event>
    suspend fun editEvent(id: String, scope: RecurringScope, request: EditEventRequest): Result<Event>
    suspend fun cancelEvent(id: String, scope: RecurringScope): Result<Unit>
    suspend fun duplicateEvent(id: String): Result<Event>
}
```

### Pattern 3: Exposed ORM tables (established project pattern)
**What:** `object *Table : Table("snake_name")` in `db/tables/`; `java.time.Instant` for timestamps
**When to use:** All new DB tables

```kotlin
// Source: existing SubGroupsTable.kt pattern
object EventsTable : Table("events") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val title = text("title")
    val type = enumerationByName<EventType>("type", 16)
    val startAt = timestamp("start_at")   // java.time.Instant via exposed-java-time
    val endAt = timestamp("end_at")
    val meetupAt = timestamp("meetup_at").nullable()
    val location = text("location").nullable()
    val description = text("description").nullable()
    val minAttendees = integer("min_attendees").nullable()
    val status = enumerationByName<EventStatus>("status", 16).default(EventStatus.active)
    val cancelledAt = timestamp("cancelled_at").nullable()
    val seriesId = uuid("series_id").references(EventSeriesTable.id).nullable()
    val seriesSequence = integer("series_sequence").nullable()
    val seriesOverride = bool("series_override").default(false)
    val createdBy = uuid("created_by").references(UsersTable.id)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}
```

### Pattern 4: Ktor server routes (established project pattern)
**What:** Top-level `fun Routing.eventRoutes()` function; `authenticate("jwt") { ... }` wrapper
**When to use:** All event API endpoints

```kotlin
// Source: existing TeamRoutes.kt pattern
fun Routing.eventRoutes() {
    authenticate("jwt") {
        get("/users/me/events") { /* ... */ }
        get("/teams/{teamId}/events") { /* ... */ }
        get("/events/{id}") { /* ... */ }
        post("/events") { /* ... */ }
        patch("/events/{id}") { /* ... */ }
        post("/events/{id}/cancel") { /* ... */ }
        post("/events/{id}/duplicate") { /* ... */ }
    }
}
```

### Pattern 5: Materialisation background job
**What:** Ktor `Application` coroutine scope; `launch` at startup + triggered on series creation
**When to use:** `EventMaterialisationJob` only — never block a request path

```kotlin
// Source: PROJECT.md ADR — background jobs are Ktor coroutines
fun Application.startMaterialisationJob(eventRepository: EventRepository) {
    launch(Dispatchers.IO) {
        while (isActive) {
            eventRepository.materialiseUpcomingOccurrences()
            delay(24.hours)
        }
    }
}
// Also call eventRepository.materialiseUpcomingOccurrences() immediately after POST /events creates a series
```

### Pattern 6: kizitonwose HorizontalCalendar (month view)
**What:** `HorizontalCalendar` composable; `rememberCalendarState` for state; `dayContent` lambda for day cells
**When to use:** Month view in CalendarScreen

```kotlin
// Source: kizitonwose/Calendar docs — Compose.md
val currentMonth = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).month }
val state = rememberCalendarState(
    startMonth = YearMonth(currentYear, Month.JANUARY),
    endMonth = YearMonth(currentYear + 1, Month.DECEMBER),
    firstVisibleMonth = YearMonth(currentYear, currentMonth),
    firstDayOfWeek = DayOfWeek.MONDAY
)
HorizontalCalendar(
    state = state,
    dayContent = { day -> EventDayCell(day, eventsOnDay) },
    monthHeader = { month -> DaysOfWeekHeader() }
)
```

### Pattern 7: kizitonwose WeekCalendar (week view)
**What:** `WeekCalendar` composable; `rememberWeekCalendarState`; `dayContent` for time-block grid
**When to use:** Week view in CalendarScreen

```kotlin
// Source: kizitonwose/Calendar docs — Compose.md
val state = rememberWeekCalendarState(
    startDate = LocalDate(currentYear, Month.JANUARY, 1),
    endDate = LocalDate(currentYear + 1, Month.DECEMBER, 31),
    firstVisibleWeekDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    firstDayOfWeek = DayOfWeek.MONDAY
)
WeekCalendar(
    state = state,
    dayContent = { day -> WeekDayColumn(day, eventsOnDay) }
)
```

### Pattern 8: UTC ↔ local timezone (kotlinx-datetime)
**What:** Server stores `Instant` (UTC); shared domain converts for display
**When to use:** Everywhere an event time is shown; keep raw `Instant` in domain model

```kotlin
// Source: kotlinx.datetime official API
import kotlinx.datetime.*

// Convert for display
val localDateTime: LocalDateTime = event.startAt.toLocalDateTime(TimeZone.currentSystemDefault())
// Format for timezone abbreviation label
val tzId = TimeZone.currentSystemDefault().id  // e.g. "Europe/Zurich"
// Show abbreviated: derive from id or use platform-specific formatting
```

### Pattern 9: Navigation3 new screens
**What:** Add `data class` / `data object` entries to `Screen.kt`; add `is Screen.X ->` branches in `AppNavigation.kt`
**When to use:** EventDetail, CreateEvent, EditEvent screens

```kotlin
// Source: existing Screen.kt pattern
@Serializable
data class EventDetail(val eventId: String) : Screen("event_detail/{eventId}")
@Serializable
data object CreateEvent : Screen("create_event")
@Serializable
data class EditEvent(val eventId: String) : Screen("edit_event/{eventId}")
```

### Anti-Patterns to Avoid
- **Role check in Composable:** Never hide FAB based on JWT-embedded role. Check role from ViewModel state (loaded from server response).
- **Timezone conversion in Composable:** UTC→local conversion belongs in shared domain (ViewModel or repository mapping), not in UI layer.
- **Blocking request path for materialisation:** Never trigger materialisation synchronously in the POST /events handler response. Trigger via `launch` (fire-and-forget within Application scope).
- **Storing LocalDateTime in domain model:** Keep `Instant` in Event data class; convert only at display time.
- **Editing past occurrences:** All edit scopes must guard `series_sequence >= currentSequence` before modifying materialised occurrences.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Calendar grid layout | Custom LazyGrid month/week layout | `kizitonwose/calendar-compose` | Day position edge cases, multi-day spanning, locale first-day-of-week, accessibility |
| UTC ↔ local timezone math | Manual offset calculation | `kotlinx-datetime` `Instant.toLocalDateTime(TimeZone.currentSystemDefault())` | DST, historical timezone rule changes |
| Recurring date expansion | Custom RRULE-style engine | Server materialisation job with `event_series` + concrete `events` rows | Querying materialised rows is trivial; virtual expansion makes range queries complex |
| Jetpack DatePicker / TimePicker | Custom time input widgets | `DatePickerDialog` / `TimePickerDialog` from `material3` | Already in Compose M3 |

**Key insight:** The decision to materialise occurrences as concrete DB rows (rather than virtual expansion) means all queries are simple `WHERE start_at BETWEEN ? AND ?` — no expansion logic on the read path. The only complexity is the write path (edit scopes) and the daily materialisation job.

---

## Common Pitfalls

### Pitfall 1: Table name mismatch for subgroups
**What goes wrong:** Phase 3 spec uses `subgroups` / `subgroup_members` but V6 migration created `sub_groups` / `sub_group_members`.
**Why it happens:** Spec was written before migration naming was finalised.
**How to avoid:** Use `sub_groups` and `sub_group_members` throughout Phase 3 (matches V6 migration). `event_subgroups` new table references `sub_groups(id)`.
**Warning signs:** FK constraint error on migration V7.

### Pitfall 2: kizitonwose version requires kotlinx-datetime 0.7.x
**What goes wrong:** Version 2.8.0+ of the library uses kotlinx-datetime 0.7.x API changes. Project pins 0.6.1 due to K/N IR crash.
**Why it happens:** Library version 2.8.0 (Jul 2024) updated to kotlinx-datetime 0.7.1.
**How to avoid:** Use `2.10.0` BUT verify it does not force-upgrade kotlinx-datetime transitively. If it does, either force-override `kotlinx-datetime = "0.6.1"` in `libs.versions.toml` or test carefully on iOS. The 0.7.x API change is primarily the `DateTimePeriod`/`DateTimeUnit` API — kizitonwose itself only uses `LocalDate` and `YearMonth`, so the crash risk may be lower. **Validate on iOS simulator before completing calendar tasks.**
**Warning signs:** `IR lowering phase failed` on iOS linkage; or runtime crash on iOS when navigating to CalendarScreen.

### Pitfall 3: Edit scope "all" must skip past occurrences
**What goes wrong:** Applying "edit all" template update overwrites past event rows.
**Why it happens:** Naive implementation updates all rows with `series_id = ?`.
**How to avoid:** Always add `AND start_at > NOW()` AND `series_override = false` to the UPDATE query for "all" scope.

### Pitfall 4: Materialisation job runs before DB migration is applied
**What goes wrong:** Job starts at application boot and tries to query `events` table before Flyway runs.
**Why it happens:** Flyway runs in `DatabaseFactory.init()` but job could theoretically race if launched in a separate coroutine before that.
**How to avoid:** Launch the materialisation job inside `Application.module()` *after* `DatabaseFactory.init()` completes. Follow the same order as current `module()` function.

### Pitfall 5: SQLDelight schema undefined (no `.sq` files yet)
**What goes wrong:** Attempting to run shared code that references SQLDelight-generated classes fails at compile time.
**Why it happens:** The shared module has no `.sq` files yet (verified by filesystem scan).
**How to avoid:** Create the SQLDelight schema files as Wave 0 work before implementing the cache. The schema needs: `events.sq` with INSERT/SELECT for the 3-month cache window.

### Pitfall 6: Navigation3 backstack for event detail + create form
**What goes wrong:** Navigating Event Detail → Edit → back leaves a stale detail screen.
**Why it happens:** The current manual backstack (`MutableList<Screen>`) is used for all navigation; replace-then-add is not automatic.
**How to avoid:** After a successful save in EditEvent, pop the edit screen AND refresh detail. Use `MutableSharedFlow` events from ViewModel to signal navigation — same pattern as existing screens.

### Pitfall 7: Multi-day events spanning midnight in calendar week view
**What goes wrong:** A multi-day event (e.g. Fri–Sun) does not appear on Saturday in the week view.
**Why it happens:** If you only place events on their `start_at` date, subsequent days are invisible.
**How to avoid:** In the calendar day cell, check if any event's `startDate <= thisDay <= endDate` (using kotlinx-datetime LocalDate comparison). Build a `Map<LocalDate, List<Event>>` in the ViewModel before passing to composables.

---

## Code Examples

### Timezone conversion (shared domain)
```kotlin
// Source: kotlinx.datetime official API (kotlinlang.org/api/kotlinx-datetime)
import kotlinx.datetime.*

fun Instant.toLocalDisplay(): LocalDateTime =
    toLocalDateTime(TimeZone.currentSystemDefault())

fun Instant.toTimezoneAbbr(): String {
    // TimeZone.id returns e.g. "Europe/Zurich"; abbreviation requires platform formatting
    // For the form label, derive from the ID or use a simple fallback
    return TimeZone.currentSystemDefault().id
}
```

### Recurring scope edit (server, conceptual)
```kotlin
// Source: tech.md spec §Recurring Event Strategy
enum class RecurringScope { THIS_ONLY, THIS_AND_FUTURE, ALL }

// THIS_ONLY: update single events row, set seriesOverride = true
// THIS_AND_FUTURE: split series — set series.seriesEndDate to currentOccurrence.date - 1 day,
//   create new EventSeries with new template, materialise future occurrences
// ALL: update event_series template fields, then bulk-update all future events
//   WHERE series_id = ? AND start_at > NOW() AND series_override = false
```

### SQLDelight event cache schema (to be created)
```sql
-- shared/src/commonMain/sqldelight/ch/teamorg/Event.sq
CREATE TABLE IF NOT EXISTS CachedEvent (
    id TEXT NOT NULL PRIMARY KEY,
    title TEXT NOT NULL,
    type TEXT NOT NULL,
    start_at INTEGER NOT NULL,   -- epoch millis
    end_at INTEGER NOT NULL,
    meetup_at INTEGER,
    location TEXT,
    description TEXT,
    min_attendees INTEGER,
    status TEXT NOT NULL,
    series_id TEXT,
    series_override INTEGER NOT NULL DEFAULT 0,
    cached_at INTEGER NOT NULL
);

getUpcomingEvents:
SELECT * FROM CachedEvent
WHERE start_at >= :fromMillis AND start_at <= :toMillis
ORDER BY start_at ASC;

upsertEvent:
INSERT OR REPLACE INTO CachedEvent VALUES ?;

deleteOlderThan:
DELETE FROM CachedEvent WHERE cached_at < :cutoffMillis;
```

### Koin registration pattern (follow exactly)
```kotlin
// UiModule.kt — add new ViewModels
factory { EventListViewModel(get()) }
factory { EventDetailViewModel(get()) }
factory { CreateEditEventViewModel(get(), get()) }  // EventRepo + SubgroupRepo
factory { CalendarViewModel(get()) }

// SharedModule (androidMain + iosMain) — add repositories
single<EventRepository> { EventRepositoryImpl(get()) }
single<SubgroupRepository> { SubgroupRepositoryImpl(get()) }
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Android-only `calendar-view` XML library | `kizitonwose/calendar-compose` KMP | 2023 — KMP support added | Use `compose-multiplatform` artifact, not `compose` or `view` |
| kotlinx-datetime 0.7.x | Pinned to 0.6.1 in this project | Project decision (K/N IR crash) | Must not upgrade; verify kizitonwose 2.10.0 doesn't force it |
| Navigation3 alpha04 (project has alpha04) | alpha04 is current in project | — | No change needed |

**Deprecated/outdated:**
- `kizitonwose/calendar-compose:compose` (Android-only): replaced by `compose-multiplatform` for KMP projects
- `java.util.Date` / `java.util.Calendar` for timestamps: use `java.time.Instant` in Exposed (via `exposed-java-time`), `kotlinx-datetime Instant` in shared KMP

---

## Open Questions

1. **kizitonwose 2.10.0 transitive dependency on kotlinx-datetime 0.7.x**
   - What we know: v2.8.0 adopted 0.7.x API; project pins 0.6.1; v2.10.0 confirmed for Kotlin 2.3.0
   - What's unclear: whether 2.10.0 forces 0.7.x as a transitive dep at runtime on iOS
   - Recommendation: Wave 0 task — add the dependency, run `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` immediately, fail fast before any other calendar work. If crash, add `force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")` to `configurations.all { resolutionStrategy }` in the shared module.

2. **Timezone abbreviation formatting (CET/CEST display)**
   - What we know: `TimeZone.currentSystemDefault().id` returns "Europe/Zurich"; abbreviation like "CET" requires locale-aware formatting
   - What's unclear: whether CMP 1.10.1 on iOS provides `java.text.SimpleDateFormat("zzz")` equivalent
   - Recommendation: Use `TimeZone.currentSystemDefault().id` as the label fallback (informational only per spec). If platform-specific abbreviation is needed, use `expect/actual` pattern.

3. **Exposed enum columns for `event_type` and `event_status`**
   - What we know: Exposed supports `enumerationByName<T>()` for enums stored as text
   - What's unclear: whether PostgreSQL `CREATE TYPE` ENUM or plain TEXT is preferred for these columns in Flyway
   - Recommendation: Use PostgreSQL `TEXT` column with a `CHECK` constraint (simpler Flyway migration) + `enumerationByName` in Exposed. Avoids needing `ALTER TYPE` if values change later.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Kotlin Test + Testcontainers (server); Compose UI Test (mobile) |
| Config file | `server/src/test/kotlin/ch/teamorg/test/IntegrationTestBase.kt` |
| Quick run command | `./gradlew :server:test --tests "ch.teamorg.routes.EventRoutesTest"` |
| Full suite command | `./gradlew :server:test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ES-01 | POST /events creates one-off event | integration | `./gradlew :server:test --tests "*.EventRoutesTest.create one-off event success"` | ❌ Wave 0 |
| ES-03 | POST /events creates series; materialisation expands occurrences | integration | `./gradlew :server:test --tests "*.EventRoutesTest.create recurring event materialises occurrences"` | ❌ Wave 0 |
| ES-07 | PATCH /events/{id} edits event | integration | `./gradlew :server:test --tests "*.EventRoutesTest.edit event success"` | ❌ Wave 0 |
| ES-08 | POST /events/{id}/cancel cancels event | integration | `./gradlew :server:test --tests "*.EventRoutesTest.cancel event success"` | ❌ Wave 0 |
| ES-09 | Edit recurring: all 3 scopes work correctly | integration | `./gradlew :server:test --tests "*.EventRoutesTest.edit recurring event this-only"` etc. | ❌ Wave 0 |
| ES-11/13 | GET /users/me/events returns filtered list | integration | `./gradlew :server:test --tests "*.EventRoutesTest.get my events filtered by type"` | ❌ Wave 0 |
| ES-15 | Materialisation job produces correct occurrences | unit | `./gradlew :server:test --tests "*.EventMaterialisationTest"` | ❌ Wave 0 |
| ES-16 | Event times stored as UTC TIMESTAMPTZ | integration | included in create/read tests above | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :server:test --tests "ch.teamorg.routes.EventRoutesTest"`
- **Per wave merge:** `./gradlew :server:test`
- **Phase gate:** Full server test suite + `./gradlew :composeApp:testDebugUnitTest` green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `server/src/test/kotlin/ch/teamorg/routes/EventRoutesTest.kt` — covers ES-01, ES-07, ES-08, ES-09, ES-11, ES-13
- [ ] `server/src/test/kotlin/ch/teamorg/infra/EventMaterialisationTest.kt` — covers ES-03, ES-15
- [ ] `shared/src/commonMain/sqldelight/ch/teamorg/Event.sq` — SQLDelight schema (compile dependency)
- [ ] `shared/src/commonMain/sqldelight/ch/teamorg/teamorg.db` SQLDelight database definition file — if not already present

---

## Sources

### Primary (HIGH confidence)
- kizitonwose/Calendar GitHub releases — version 2.10.0 confirmed, KMP support confirmed, API from official `docs/Compose.md`
- `server/src/main/resources/db/migrations/V6__create_subgroups.sql` — confirmed table name `sub_groups` (not `subgroups`)
- `gradle/libs.versions.toml` — confirmed: kotlinx-datetime 0.6.1 pinned, koin 4.1.1, ktor 3.3.3, sqldelight 2.0.2
- `.project/specs/event-scheduling/tech.md` — canonical data model, API surface, recurring strategy
- `.project/specs/event-scheduling/ux.md` — all screens S1–S7, flows F1–F7
- Existing codebase patterns: `TeamRosterViewModel`, `TeamRepositoryImpl`, `SubGroupsTable`, `IntegrationTestBase`, `AppNavigation`
- `kotlinlang.org/api/kotlinx-datetime` — `Instant.toLocalDateTime(TimeZone.currentSystemDefault())` confirmed

### Secondary (MEDIUM confidence)
- kizitonwose GitHub releases page — v2.10.0 uses Kotlin 2.3.0 (matches project); v2.8.0 introduced kotlinx-datetime 0.7.x dependency (potential conflict)
- Ktor GitHub issues #1530 — in-process coroutine background jobs are the standard MVP approach for Ktor (matches PROJECT.md decision)

### Tertiary (LOW confidence)
- Timezone abbreviation ("CET") on iOS via Compose Multiplatform — not directly verified; flagged as open question

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions verified from project files and official releases
- Architecture: HIGH — all patterns directly lifted from existing codebase
- Pitfalls: HIGH (table names, materialisation) / MEDIUM (kizitonwose datetime transitive dep, iOS timezone) — verified from migration files and release notes
- Calendar API: HIGH — verified from official docs

**Research date:** 2026-03-19
**Valid until:** 2026-04-19 (kizitonwose releases frequently; re-check if updating library version)
