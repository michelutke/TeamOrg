# Phase 5: Notifications - Context

**Gathered:** 2026-03-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Players and coaches receive timely, relevant push notifications (OneSignal, iOS + Android) and in-app notifications. Users control what they get via per-team notification settings. Covers: event notifications (new/edit/cancel), configurable reminders, coach response notifications (per-response or summary mode), absence change notifications, in-app inbox, per-user settings, dedup + removed-member guards.

</domain>

<decisions>
## Implementation Decisions

### Notification triggers & batching
- Event edit notifications: Claude's discretion on debounce vs immediate
- Coach response mode: pick ONE — per-response OR pre-event summary (not both simultaneously)
- Absence notifications to coach: one summary notification listing all affected events (not per-event)
- No quiet hours — rely on device-level DND
- No-duplicate guard: server-side dedup before sending
- Removed-member guard: check membership before sending

### Reminder configuration
- Global default + per-event override
- Free-form time picker for lead time selection (not predefined options)
- Default for new users: 2 hours before
- Per-event override: "Reminder" row on EventDetailScreen — tap to set custom lead time for that event

### In-app inbox
- Flat chronological list (reverse-chronological, like GitHub notifications)
- Tap notification → deep-link to related screen (event detail, absence, etc.)
- Read state: mark read on tap + "Mark all as read" button
- Unread badge count on Inbox tab in bottom nav
- 90-day retention, auto-cleanup older notifications (matches event cache window)
- Existing Inbox placeholder screen in bottom nav ready for implementation

### Settings screen
- Entry point: gear icon in Inbox screen toolbar
- Toggles grouped by category: Events (new/edit/cancel), Responses (per-response/summary), Reminders, Absences
- Per-team settings (not global) — user configures notification preferences for each team separately
- Coach response mode toggle (per-response vs summary) per-team, in same settings screen

### Claude's Discretion
- Event edit debounce timing (if any)
- Exact notification copy/wording
- Time picker component choice
- Loading states and error handling
- Notification channel setup (Android)
- Settings screen visual layout/spacing

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

No external specs — requirements fully captured in REQUIREMENTS.md (NO-01 through NO-12) and decisions above.

### Requirements
- `.planning/REQUIREMENTS.md` §NO — Notification requirements NO-01 through NO-12
- `.planning/ROADMAP.md` §Phase 5 — Deliverables and success criteria

### Prior phase context
- `.planning/phases/04-attendance-tracking/04-CONTEXT.md` — Attendance patterns, response buttons, absence creation flow (notification triggers connect here)
- `.planning/phases/03-event-scheduling/03-CONTEXT.md` — Event model, cache pattern, EventDetailScreen structure (reminder row integrates here)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PlaceholderScreen("Inbox")` in navigation — ready to replace with real inbox
- `UserPreferences` (multiplatform-settings) — extend for notification prefs storage
- SQLDelight cache pattern (EventCacheManager, AttendanceCacheManager) — reuse for notification inbox local storage
- BottomSheet pattern — reuse for settings or inline configuration
- LazyColumn + Card pattern — reuse for inbox list items
- MutationQueueManager — pattern for offline notification state sync

### Established Patterns
- Koin DI for all services/repositories — PushService will follow same pattern
- Entity-scoped routes (eventRoutes(), attendanceRoutes()) — add notificationRoutes()
- Flyway migrations for DB schema — add notification tables
- expect/actual KmpViewModel — notification-related ViewModels follow same pattern
- Background jobs (auto-decline, auto-present) — reminder scheduler follows same pattern

### Integration Points
- Bottom nav Screen enum — Inbox already defined, needs real screen
- EventDetailScreen — add Reminder row
- Server routes — add notification endpoints
- Koin SharedModule — register PushService, NotificationRepository
- `gradle/libs.versions.toml` — add OneSignal dependency
- AndroidApp TeamorgApplication — OneSignal SDK init
- iOS AppDelegate — OneSignal SDK init

</code_context>

<specifics>
## Specific Ideas

- Reminder time picker should be free-form (user picks exact duration), not a dropdown with fixed choices
- Coach response mode is a binary choice per team, not a global setting
- Inbox should feel like GitHub notifications — simple, flat, actionable

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 05-notifications*
*Context gathered: 2026-03-26*
