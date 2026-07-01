import { requireUser } from '$lib/server/guards';
import { apiGet } from '$lib/server/api';
import type { EventWithTeams, MatchedTeam } from '$lib/server/events';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, url }) => {
	const user = requireUser(locals);
	const token = locals.token!;
	const teamFilter = url.searchParams.get('team');
	const awaitingFilter = url.searchParams.get('filter') === 'awaiting_checkin';

	const isCoachOrManager =
		user.isSuperAdmin ||
		user.managedClubIds.length > 0 ||
		user.teamRoles.some((r) => r.role === 'coach');

	const canCreate = isCoachOrManager;

	const all = await apiGet<EventWithTeams[]>('/users/me/events', token);

	// Distinct teams across the user's events → filter chips.
	const teamMap = new Map<string, MatchedTeam>();
	for (const e of all) for (const t of e.matchedTeams) teamMap.set(t.id, t);
	const teams = [...teamMap.values()].sort((a, b) => a.name.localeCompare(b.name));

	// awaiting_checkin filter takes precedence over team filter; uses local list (scoped to
	// events the user already sees — a dedicated coach-scoped endpoint would be stricter).
	const events = (awaitingFilter
		? all.filter((e) => e.event.checkInStatus === 'awaiting_checkin')
		: teamFilter
			? all.filter((e) => e.matchedTeams.some((t) => t.id === teamFilter))
			: all
	).sort((a, b) => a.event.startAt.localeCompare(b.event.startAt));

	// Per-event RSVP counts + the caller's own response, so the list can show instant
	// accept/decline buttons (parity with mobile). Fetched in parallel; failures degrade to zero.
	type Att = { confirmed: number; maybe: number; declined: number; mine: string | null };
	const attendance: Record<string, Att> = {};
	await Promise.all(
		events.map(async ({ event }) => {
			try {
				const rows = await apiGet<{ userId: string; status: string }[]>(
					`/events/${event.id}/attendance`,
					token
				);
				let confirmed = 0,
					maybe = 0,
					declined = 0;
				let mine: string | null = null;
				for (const r of rows) {
					if (r.status === 'confirmed') confirmed++;
					else if (r.status === 'unsure') maybe++;
					else if (r.status === 'declined' || r.status === 'declined-auto') declined++;
					if (r.userId === user.id) mine = r.status;
				}
				attendance[event.id] = { confirmed, maybe, declined, mine };
			} catch {
				attendance[event.id] = { confirmed: 0, maybe: 0, declined: 0, mine: null };
			}
		})
	);

	return { events, teams, teamFilter, awaitingFilter, canCreate, isCoachOrManager, attendance };
};
