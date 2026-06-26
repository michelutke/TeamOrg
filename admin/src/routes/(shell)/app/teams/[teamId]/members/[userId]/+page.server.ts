import { error, fail } from '@sveltejs/kit';
import { requireUser, isCoach, isClubManager, ApiError } from '$lib/server/guards';
import { apiGet, apiPut, apiPatch } from '$lib/server/api';
import type { Team } from '$lib/server/teams';
import type { Actions, PageServerLoad } from './$types';

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
	const { teamId, userId } = params;

	try {
		const [team, members] = await Promise.all([
			apiGet<Team>(`/teams/${teamId}`, token),
			apiGet<Member[]>(`/teams/${teamId}/members`, token)
		]);

		const member = members.find((m) => m.userId === userId);
		if (!member) throw error(404, 'Mitglied nicht gefunden');

		const isOwn = userId === user.id;
		const canManage = isCoach(user, teamId) || isClubManager(user, team.clubId);

		return { team, member, isOwn, editable: isOwn || canManage };
	} catch (e) {
		if (e instanceof ApiError) throw error(e.status === 403 ? 403 : 404, 'Kein Zugriff');
		throw e;
	}
};

export const actions: Actions = {
	save: async ({ request, locals, params }) => {
		const user = requireUser(locals);
		const { teamId, userId } = params;
		const form = await request.formData();
		const jerseyRaw = (form.get('jerseyNumber') as string)?.trim();
		const body = {
			jerseyNumber: jerseyRaw ? Number(jerseyRaw) : null,
			position: (form.get('position') as string)?.trim() || null
		};

		try {
			if (userId === user.id) {
				// Self-service route — players edit their own profile here.
				await apiPut(`/users/me/teams/${teamId}/profile`, locals.token!, body);
			} else {
				// Coach/manager editing another member.
				await apiPatch(`/teams/${teamId}/members/${userId}/profile`, locals.token!, body);
			}
			return { saved: true };
		} catch (e) {
			if (e instanceof ApiError) return fail(e.status, { error: 'Fehler' });
			throw e;
		}
	}
};
