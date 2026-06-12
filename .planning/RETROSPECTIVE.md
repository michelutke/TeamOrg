# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v1.0 — MVP

**Shipped:** 2026-04-07
**Phases:** 9 | **Plans:** 53 | **Timeline:** 34 days (2026-03-02 → 2026-04-04)

### What Was Built
- KMP monorepo: Ktor backend + Compose Multiplatform mobile (Android + iOS) + SvelteKit admin
- Full team management lifecycle: clubs, teams, invites, roles, sub-groups, player profiles
- Event scheduling with recurring series, server-side materialisation, calendar views
- Attendance tracking: RSVP, Abwesenheit (weekly + period), coach overrides, offline queue
- Push notifications (OneSignal) + in-app inbox with per-user settings and reminder scheduler
- Super-admin panel: club CRUD, impersonation (1h limit), user search, immutable audit log

### What Worked
- Phase-by-phase always-working approach prevented non-functional dead ends (lesson from failed first attempt)
- Milestone audit after Phase 5 caught 7 orphaned requirements — gap-closure phases (4.1, 5.1, 5.2) fixed them systematically
- Testcontainers integration tests caught real bugs early (SubGroup serialization, role detection)
- Decisions logged per-phase in STATE.md made cross-phase consistency easier (e.g. kotlinx-datetime everywhere, not java.time)

### What Was Inefficient
- Phase 01 had no VERIFICATION.md — orphaned AUTH-01–06 checkboxes weren't caught until milestone audit
- Some SUMMARY.md files had empty one-liners, making auto-extraction unreliable
- kizitonwose calendar version conflict (2.10.0→2.7.0) cost a debugging cycle — should have pinned kotlinx-datetime 0.6.x constraint earlier
- Several decimal phases (4.1, 5.1, 5.2) needed post-hoc — earlier verification per phase would have caught gaps sooner

### Patterns Established
- `getMyRoles()` single-call pattern for role detection across all ViewModels
- expect/actual for platform-specific features (ImagePicker, PushRegistration, DatabaseDriver)
- Cache-aside pattern: SQLDelight offline cache + Ktor repository impl
- Server: route-local DTOs for request/response shapes, domain models in shared/
- Offline mutation queue with server-authoritative conflict resolution (409)
- Audit log immutable at DB level (app role has no UPDATE/DELETE)

### Key Lessons
1. Run `/gsd:audit-milestone` before completing — catches orphaned requirements early
2. Pin transitive dependency versions when using KMP — iOS linker errors are cryptic
3. Decimal phases work well for targeted gap-closure — better than reopening completed phases
4. JSONB columns in PostgreSQL + Exposed text() don't mix — use TEXT for JSON storage in Exposed
5. SvelteKit server-side load functions + API routes keep tokens off the browser — correct pattern for admin panels

### Cost Observations
- Model mix: primarily Opus for planning + execution, Sonnet for verification/gap analysis
- 444 commits across 34 days
- Notable: parallel plan execution per phase (waves) significantly reduced wall-clock time

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Timeline | Phases | Key Change |
|-----------|----------|--------|------------|
| v1.0 | 34 days | 9 | Established phase-by-phase workflow with milestone audit |

### Top Lessons (Verified Across Milestones)

1. Always verify per-phase before moving on — catching gaps later costs decimal phases
2. Log decisions explicitly — cross-phase consistency depends on it
