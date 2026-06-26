import { error } from '@sveltejs/kit';
import { requireUser, isCoach, isClubManager, ApiError } from '$lib/server/guards';
import { apiGet } from '$lib/server/api';
import type { Team } from '$lib/server/teams';
import type { PageServerLoad } from './$types';

interface Member {
	userId: string;
	displayName: string;
	avatarUrl: string | null;
	role: string;
	jerseyNumber: number | null;
	position: string | null;
}

export const load: PageServerLoad = async ({ locals, params }) => {
	const user = requireUser(locals);
	const token = locals.token!;
	const { teamId } = params;

	try {
		const [team, members] = await Promise.all([
			apiGet<Team>(`/teams/${teamId}`, token),
			apiGet<Member[]>(`/teams/${teamId}/members`, token)
		]);

		const canManage = isCoach(user, teamId) || isClubManager(user, team.clubId);

		return { team, members, canManage };
	} catch (e) {
		if (e instanceof ApiError) throw error(e.status === 403 ? 403 : e.status, 'Kein Zugriff');
		throw e;
	}
};
