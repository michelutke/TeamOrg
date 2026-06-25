import { error, fail, redirect } from '@sveltejs/kit';
import { requireUser, canManageTeam, ApiError } from '$lib/server/guards';
import { loadUserTeams } from '$lib/server/teams';
import { apiGet, apiPatch } from '$lib/server/api';
import type { EventWithTeams } from '$lib/server/events';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, params }) => {
	const user = requireUser(locals);
	const token = locals.token!;

	let data: EventWithTeams;
	try {
		data = await apiGet<EventWithTeams>(`/events/${params.id}`, token);
	} catch (e) {
		if (e instanceof ApiError) throw error(e.status === 403 ? 403 : 404, 'Kein Zugriff');
		throw e;
	}

	const canManage =
		user.isSuperAdmin || data.event.teamIds.some((tid) => canManageTeam(user, tid));
	if (!canManage) throw error(403, 'Kein Zugriff');

	const teams = (await loadUserTeams(user, token)).filter((t) => t.canManage);

	return {
		teams: teams.map((t) => ({ id: t.id, name: t.name })),
		event: data.event
	};
};

export const actions: Actions = {
	default: async ({ request, locals, params }) => {
		requireUser(locals);
		const form = await request.formData();
		const teamIds = form.getAll('teamIds').map(String).filter(Boolean);
		const minRaw = (form.get('minAttendees') as string)?.trim();

		if (teamIds.length === 0 || !form.get('title') || !form.get('startAt') || !form.get('endAt')) {
			return fail(400, { error: 'Pflichtfelder fehlen' });
		}

		const body = {
			scope: 'this_only',
			title: (form.get('title') as string).trim(),
			type: form.get('type') as string,
			startAt: form.get('startAt') as string,
			endAt: form.get('endAt') as string,
			meetupAt: (form.get('meetupAt') as string) || null,
			location: (form.get('location') as string)?.trim() || null,
			description: (form.get('description') as string)?.trim() || null,
			minAttendees: minRaw ? Number(minRaw) : null,
			teamIds
		};

		try {
			await apiPatch(`/events/${params.id}`, locals.token!, body);
		} catch (e) {
			if (e instanceof ApiError) return fail(e.status, { error: 'Fehler beim Speichern' });
			throw e;
		}
		throw redirect(303, `/app/events/${params.id}`);
	}
};
