# Recurring events (series) — as built

Covers the series model, how NDS imports produce series, and the edit scopes
("apply to all") across web + mobile. Last updated 2026-07-04 (PR #45).

## Model

- `event_series` holds the pattern (`patternType` weekly/daily/custom, weekdays,
  `interval_days`, `series_end_date`) and a **template** (title, type, duration,
  location, description, `template_default_response`, …).
- Each generated event carries `series_id` + `series_sequence`; events edited with
  scope "this only" set `series_override = true` and are excluded from later
  series-wide updates.
- `EventMaterialisationJob` rolls the series forward, copying template fields
  (including `template_default_response` → `default_response`).

## Where series come from

1. **Create form** (web + mobile): the recurrence section (weekly/daily/custom +
   end date).
2. **NDS import**: on first import, activities are grouped by
   (weekday, activity symbol, duration); groups with enough occurrences become
   weekly series, the rest single events. Verified on prod: an imported season of
   100 trainings = 2 weekly series (Mo + Mi). Re-imports never regroup — existing
   events are matched by date and only attendance is attached.

## Editing with scope ("apply to all")

`PATCH /events/{id}` takes `scope`:

| Scope | Effect |
|---|---|
| `this_only` | updates only this event and marks it `series_override` |
| `this_and_future` | updates this event (all fields incl. dates), then propagates to future events + the series template |
| `all` | updates the series template + all future, non-overridden events |

**What propagates:** title, type, **location**, description, minAttendees,
defaultResponse. **What never propagates: dates** (`startAt`/`endAt`/`meetupAt`
stay per event — a series can't be collapsed onto one date). Past events and
`series_override` events are never touched.

Surfaces:
- **Mobile**: `RecurringScopeSheet` (since the events feature).
- **Web**: the edit form shows the scope choice („Nur dieser Termin" / „Dieser
  und alle zukünftigen" / „Alle Termine der Serie") whenever the event has a
  `seriesId` — added in PR #45; previously the web hardcoded `this_only`, so
  e.g. a location change could never reach the series.
- Cancel/uncancel on the web event detail currently uses `this_only` only.

## Known limitations / follow-ups

- Series propagation is future-only by design; e.g. setting the Ort on an
  imported season does not backfill past trainings. Relevant for NDS export,
  which requires ORT for Trainings — the export preflight will flag past events
  without a location.
- No web UI to edit the series *pattern* (weekdays/end date) after creation.
