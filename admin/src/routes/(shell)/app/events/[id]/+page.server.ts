import { error, fail, redirect } from '@sveltejs/kit';
import { requireUser, ApiError } from '$lib/server/guards';
import { loadUserTeams } from '$lib/server/teams';
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

		// Manageability from the user's manageable teams (covers club managers who
		// hold no per-team role). Same derivation as the edit page — one source of truth.
		const manageableTeamIds = new Set(
			(await loadUserTeams(user, token)).filter((t) => t.canManage).map((t) => t.id)
		);
		const canManage =
			user.isSuperAdmin || data.event.teamIds.some((tid) => manageableTeamIds.has(tid));
		// Same guard as the backend (requireEventAccess coach|club_manager).
		const canReconcile = canManage;

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
		if (meetupLocal) {
			const meetup = new Date(meetupLocal);
			if (isNaN(meetup.getTime())) return fail(400, { reconcileError: m.failed });
			body.meetupAt = meetup.toISOString();
		}
		body.notes = notes;
		body.minAttendees = minAttendeesRaw ? Number(minAttendeesRaw) : null;

		try {
			await apiPost(`/events/${params.id}/reconcile`, locals.token!, body);
			return { reconciled: true };
		} catch (e) {
			if (e instanceof ApiError) return fail(e.status, { reconcileError: m.failed });
			throw e;
		}
	},

	cancel: async ({ locals, params }) => {
		requireUser(locals);
		try {
			await apiPost(`/events/${params.id}/cancel`, locals.token!, { scope: 'this_only' });
			return { cancelled: true };
		} catch (e) {
			if (e instanceof ApiError) return fail(e.status, { manageError: manageErr(e) });
			throw e;
		}
	},

	uncancel: async ({ locals, params }) => {
		requireUser(locals);
		try {
			await apiPost(`/events/${params.id}/uncancel`, locals.token!, { scope: 'this_only' });
			return { uncancelled: true };
		} catch (e) {
			if (e instanceof ApiError) return fail(e.status, { manageError: manageErr(e) });
			throw e;
		}
	},

	duplicate: async ({ locals, params }) => {
		requireUser(locals);
		let newId: string;
		try {
			const dup = await apiPost<{ id: string }>(
				`/events/${params.id}/duplicate`,
				locals.token!,
				{}
			);
			newId = dup.id;
		} catch (e) {
			if (e instanceof ApiError) return fail(e.status, { manageError: manageErr(e) });
			throw e;
		}
		// Land on the copy's edit form so the manager can adjust the date/time.
		throw redirect(303, `/app/events/${newId}/edit`);
	}
};

/** A clear message for manage-action failures — 403 means the user isn't a coach/manager. */
function manageErr(e: ApiError): string {
	return e.status === 403 ? 'Keine Berechtigung für diese Aktion' : 'Aktion fehlgeschlagen';
}
