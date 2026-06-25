import { error, fail } from '@sveltejs/kit';
import { requireUser, canManageTeam, ApiError } from '$lib/server/guards';
import { apiGet, apiPut } from '$lib/server/api';
import { getMessages, resolveLocale } from '$lib/i18n';
import type { EventWithTeams, AttendanceResponse } from '$lib/server/events';
import type { Actions, PageServerLoad } from './$types';

interface Member {
	userId: string;
	displayName: string;
	avatarUrl: string | null;
}

export const load: PageServerLoad = async ({ locals, params }) => {
	const user = requireUser(locals);
	const token = locals.token!;
	const { id } = params;

	try {
		const [data, responses] = await Promise.all([
			apiGet<EventWithTeams>(`/events/${id}`, token),
			apiGet<AttendanceResponse[]>(`/events/${id}/attendance`, token)
		]);

		// Resolve member names across the event's teams (for the responses list).
		const memberMap = new Map<string, Member>();
		await Promise.all(
			data.matchedTeams.map(async (t) => {
				try {
					const members = await apiGet<Member[]>(`/teams/${t.id}/members`, token);
					for (const m of members) memberMap.set(m.userId, m);
				} catch {
					// ignore teams we can't read
				}
			})
		);

		const myResponse = responses.find((r) => r.userId === user.id) ?? null;
		const canManage =
			user.isSuperAdmin || data.event.teamIds.some((tid) => canManageTeam(user, tid));

		const named = responses.map((r) => ({
			...r,
			displayName: memberMap.get(r.userId)?.displayName ?? '—',
			avatarUrl: memberMap.get(r.userId)?.avatarUrl ?? null
		}));

		return { event: data.event, teams: data.matchedTeams, responses: named, myResponse, canManage };
	} catch (e) {
		if (e instanceof ApiError) throw error(e.status === 403 ? 403 : 404, 'Kein Zugriff');
		throw e;
	}
};

export const actions: Actions = {
	rsvp: async ({ request, locals, params, cookies }) => {
		const user = requireUser(locals);
		void user;
		const m = getMessages(resolveLocale(cookies.get('lang'))).rsvp;
		const form = await request.formData();
		const status = form.get('status') as string;
		const reason = (form.get('reason') as string)?.trim() || null;

		if (status === 'unsure' && !reason) {
			return fail(400, { error: m.reasonRequired });
		}

		try {
			await apiPut(`/events/${params.id}/attendance/me`, locals.token!, { status, reason });
			return { saved: true };
		} catch (e) {
			if (e instanceof ApiError && e.status === 409) return fail(409, { error: m.deadlinePassed });
			if (e instanceof ApiError) return fail(e.status, { error: 'Fehler' });
			throw e;
		}
	}
};
