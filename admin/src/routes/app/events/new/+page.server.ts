import { error, fail, redirect } from '@sveltejs/kit';
import { requireUser, ApiError } from '$lib/server/guards';
import { loadUserTeams } from '$lib/server/teams';
import { apiPost } from '$lib/server/api';
import type { AppEvent } from '$lib/server/events';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals }) => {
	const user = requireUser(locals);
	const teams = (await loadUserTeams(user, locals.token!)).filter((t) => t.canManage);
	if (teams.length === 0) throw error(403, 'Kein Zugriff');
	return { teams: teams.map((t) => ({ id: t.id, name: t.name })) };
};

function parseEventForm(form: FormData) {
	const teamIds = form.getAll('teamIds').map(String).filter(Boolean);
	const minRaw = (form.get('minAttendees') as string)?.trim();
	return {
		teamIds,
		title: (form.get('title') as string)?.trim(),
		type: form.get('type') as string,
		startAt: form.get('startAt') as string,
		endAt: form.get('endAt') as string,
		meetupAt: ((form.get('meetupAt') as string) || null) as string | null,
		location: ((form.get('location') as string)?.trim() || null) as string | null,
		description: ((form.get('description') as string)?.trim() || null) as string | null,
		minAttendees: minRaw ? Number(minRaw) : null
	};
}

export const actions: Actions = {
	default: async ({ request, locals }) => {
		requireUser(locals);
		const body = parseEventForm(await request.formData());

		if (body.teamIds.length === 0 || !body.title || !body.startAt || !body.endAt) {
			return fail(400, { error: 'Pflichtfelder fehlen' });
		}

		let created: AppEvent;
		try {
			created = await apiPost<AppEvent>('/events', locals.token!, body);
		} catch (e) {
			if (e instanceof ApiError) return fail(e.status, { error: 'Fehler beim Erstellen' });
			throw e;
		}
		throw redirect(303, `/app/events/${created.id}`);
	}
};
