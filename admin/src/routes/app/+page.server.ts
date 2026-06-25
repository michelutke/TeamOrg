import { requireUser } from '$lib/server/guards';
import { apiGet } from '$lib/server/api';
import type { PageServerLoad } from './$types';

interface Team {
	id: string;
	name: string;
	clubId?: string;
}

interface TeamCard {
	id: string;
	name: string;
	role: 'coach' | 'player' | 'club_manager';
	canManage: boolean;
}

export const load: PageServerLoad = async ({ locals }) => {
	const user = requireUser(locals);
	const token = locals.token!;

	const cards = new Map<string, TeamCard>();

	// Teams the user is a coach/player of.
	await Promise.all(
		user.teamRoles.map(async (tr) => {
			try {
				const team = await apiGet<Team>(`/teams/${tr.teamId}`, token);
				const canManage = tr.role === 'coach' || user.managedClubIds.includes(tr.clubId);
				cards.set(team.id, {
					id: team.id,
					name: team.name,
					role: tr.role === 'coach' ? 'coach' : 'player',
					canManage
				});
			} catch {
				// skip teams we can't read
			}
		})
	);

	// Teams of clubs the user manages (may overlap with the above).
	await Promise.all(
		user.managedClubIds.map(async (clubId) => {
			try {
				const teams = await apiGet<Team[]>(`/clubs/${clubId}/teams`, token);
				for (const team of teams) {
					if (!cards.has(team.id)) {
						cards.set(team.id, {
							id: team.id,
							name: team.name,
							role: 'club_manager',
							canManage: true
						});
					}
				}
			} catch {
				// skip clubs we can't read
			}
		})
	);

	return { teams: [...cards.values()] };
};
