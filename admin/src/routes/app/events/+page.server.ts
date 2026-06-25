import { requireUser } from '$lib/server/guards';
import { apiGet } from '$lib/server/api';
import type { EventWithTeams, MatchedTeam } from '$lib/server/events';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, url }) => {
	const user = requireUser(locals);
	const token = locals.token!;
	const teamFilter = url.searchParams.get('team');

	const canCreate =
		user.isSuperAdmin ||
		user.managedClubIds.length > 0 ||
		user.teamRoles.some((r) => r.role === 'coach');

	const all = await apiGet<EventWithTeams[]>('/users/me/events', token);

	// Distinct teams across the user's events → filter chips.
	const teamMap = new Map<string, MatchedTeam>();
	for (const e of all) for (const t of e.matchedTeams) teamMap.set(t.id, t);
	const teams = [...teamMap.values()].sort((a, b) => a.name.localeCompare(b.name));

	const events = (teamFilter
		? all.filter((e) => e.matchedTeams.some((t) => t.id === teamFilter))
		: all
	).sort((a, b) => a.event.startAt.localeCompare(b.event.startAt));

	return { events, teams, teamFilter, canCreate };
};
