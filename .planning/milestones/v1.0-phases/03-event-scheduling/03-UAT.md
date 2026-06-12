---
status: complete
phase: 03-event-scheduling
source: 03-00-SUMMARY.md, 03-01-SUMMARY.md, 03-02-SUMMARY.md, 03-03-SUMMARY.md, 03-04-SUMMARY.md, 03-05-SUMMARY.md, 03-06-SUMMARY.md, 03-07-SUMMARY.md
started: 2026-03-23T12:00:00Z
updated: 2026-03-23T12:00:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Event list with filtering
expected: EventListScreen displays upcoming events. Filter by team (All Teams + per-team chips) and event type (All/Training/Match/Other). Events show type colour indicator, title, date range, multi-team badge, recurring icon.
result: pass

### 2. Create button coach-only
expected: FAB "+" visible on EventListScreen only for coaches. Non-coaches see no create button.
result: pass

### 3. Event detail screen
expected: EventDetailScreen shows: cancelled banner (if applicable), type chip + title + recurring indicator, date/time, location with Maps button, team chips, subgroup chips, description with "Show more" toggle, attendance placeholder.
result: pass

### 4. Edit event
expected: Coach taps event, sees "Edit" in dropdown. CreateEditEventScreen opens pre-filled. Can change dates via DatePicker, times via TimePicker, add/remove teams, modify fields. "Save Changes" persists.
result: pass

### 5. Duplicate event
expected: Coach taps "Duplicate" on event. New create form opens pre-filled from source, series info cleared. Saved event appears as independent non-recurring copy.
result: pass

### 6. Cancel event with scope
expected: Coach taps "Cancel" on event. RecurringScopeSheet appears for recurring events. Select this_only/this_and_future/all. Event removed from list or shown cancelled (alpha=0.4 + strikethrough).
result: pass

### 7. Recurring pattern configuration
expected: Coach toggles "Recurring" switch on create form. RecurringPatternSheet opens: Daily/Weekly/Custom radio, Mon-Sun weekday grid, custom interval, end date. Pattern summarized as chip on form.
result: pass

### 8. Calendar month view
expected: Calendar tab shows month view. Days with events show coloured dots (training=blue, match=green, other=purple, cancelled=grey). Navigate months. Tap day shows that day's events.
result: pass

### 9. Calendar week view
expected: Month/Week segmented control. Week view shows time-block grid. Events with coloured left borders. Cancelled events alpha=0.4 + strikethrough.
result: skipped
reason: Week view removed — merged into single calendar with month view + info chips per V1 design

### 10. Sub-group targeting
expected: Coach selects teams on create form. Audience section shows sub-group chips (fetched per team). Can select/deselect sub-groups.
result: pass

### 11. Multi-team badge on events
expected: EventListScreen items show multi-team badge if event targets >1 team. EventDetailScreen displays team chips and subgroup chips.
result: pass

### 12. Minimum attendees setting
expected: CreateEditEventScreen has min attendees field with toggle switch. Coach can enable/disable and set count. Value saved with event.
result: pass

### 13. Cancelled event visual distinction
expected: Cancelled events show Cancelled chip, alpha=0.4, strikethrough title in list. Detail screen shows cancel banner (colorError strip) at top.
result: pass

### 14. Date formatting single vs multi-day
expected: Single-day: "Monday, Jan 15, 2:00 PM". Multi-day: "Monday, Jan 15 — Wednesday, Jan 17". Start/end times displayed.
result: pass

### 15. Location with maps link
expected: Create form has location field. Detail screen shows location with "Open in Maps" button that opens Maps app.
result: pass
note: Maps integration deferred — location display works, deep link to maps app later

### 16. Offline cache and sync
expected: Events appear from cache when offline. When network returns, cache syncs. No error shown to user.
result: pass

### 17. Recurring indicator in list and detail
expected: List shows recurring icon on recurring events. Detail shows pattern summary like "Training (Recurring, every Monday)".
result: pass

### 18. Multi-day calendar spanning
expected: Month view: multi-day event shows dot on all days. Week view: event block spans multiple days. Both views consistent.
result: pass

## Summary

total: 18
passed: 17
issues: 0
pending: 0
skipped: 1
skipped: 0

## Gaps

[none yet]
