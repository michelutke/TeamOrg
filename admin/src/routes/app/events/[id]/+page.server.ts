import { error, fail } from '@sveltejs/kit';
import { requireUser, canManageTeam, isCoach, ApiError } from '$lib/server/guards';
import { apiGet, apiPut, apiPost } from '$lib/server/api';
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
		const canReconcile =
			user.isSuperAdmin || data.event.teamIds.some((tid) => isCoach(user, tid));

		const named = responses.map((r) => ({
			...r,
			displayName: memberMap.get(r.userId)?.displayName ?? '—',
			avatarUrl: memberMap.get(r.userId)?.avatarUrl ?? null
		}));

		return {
			event: data.event,
			teams: data.matchedTeams,
			responses: named,
			myResponse,
			canManage,
			canReconcile
		};
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
	},

	reconcile: async ({ request, locals, params, cookies }) => {
		requireUser(locals);
		const m = getMessages(resolveLocale(cookies.get('lang'))).reconcile;
		const form = await request.formData();

		const meetupLocal = (form.get('meetupAt') as string)?.trim();
		const notes = (form.get('notes') as string)?.trim() || null;
		const minAttendeesRaw = (form.get('minAttendees') as string)?.trim();
		const resetAvailability = form.get('resetAvailability') === 'on';

		const body: {
			meetupAt?: string;
			notes?: string | null;
			minAttendees?: number | null;
			resetAvailability: boolean;
		} = { resetAvailability };
		if (meetupLocal) body.meetupAt = new Date(meetupLocal).toISOString();
		body.notes = notes;
		body.minAttendees = minAttendeesRaw ? Number(minAttendeesRaw) : null;

		try {
			await apiPost(`/events/${params.id}/reconcile`, locals.token!, body);
			return { reconciled: true };
		} catch (e) {
			if (e instanceof ApiError) return fail(e.status, { reconcileError: m.failed });
			throw e;
		}
	}
};
