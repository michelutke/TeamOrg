import { error, fail } from '@sveltejs/kit';
import { requireTeamManage, ApiError } from '$lib/server/guards';
import { apiGet, apiPut } from '$lib/server/api';
import type { AppEvent } from '$lib/server/events';
import type { Actions, PageServerLoad } from './$types';

interface CheckInEntry {
	userId: string;
	userName: string;
	userAvatar: string | null;
	response: { status: string } | null;
	record: { status: string; note: string | null } | null;
}

export const load: PageServerLoad = async ({ locals, params }) => {
	requireTeamManage(locals, params.teamId);
	const token = locals.token!;

	try {
		const [event, entries] = await Promise.all([
			apiGet<{ event: AppEvent }>(`/events/${params.eventId}`, token).then((d) => d.event),
			apiGet<CheckInEntry[]>(`/events/${params.eventId}/check-in`, token)
		]);
		return { event, entries, teamId: params.teamId };
	} catch (e) {
		if (e instanceof ApiError) throw error(e.status === 403 ? 403 : 404, 'Kein Zugriff');
		throw e;
	}
};

export const actions: Actions = {
	setStatus: async ({ request, locals, params }) => {
		requireTeamManage(locals, params.teamId);
		const form = await request.formData();
		const userId = form.get('userId') as string;
		const status = form.get('status') as string;

		try {
			await apiPut(`/events/${params.eventId}/check-in/${userId}`, locals.token!, { status });
			return { ok: true };
		} catch (e) {
			if (e instanceof ApiError) return fail(e.status, { error: 'Fehler' });
			throw e;
		}
	}
};
