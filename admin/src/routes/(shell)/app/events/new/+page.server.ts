import { error, fail, redirect } from '@sveltejs/kit';
import { requireUser, ApiError } from '$lib/server/guards';
import { loadUserTeams } from '$lib/server/teams';
import { apiPost } from '$lib/server/api';
import type { AppEvent } from '$lib/server/events';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, url }) => {
	const user = requireUser(locals);
	const teams = (await loadUserTeams(user, locals.token!)).filter((t) => t.canManage);
	if (teams.length === 0) throw error(403, 'Kein Zugriff');

	// Optional prefill from a carry-over series (design §15): /app/events/new?title=…&…
	const q = url.searchParams;
	const minAttendeesRaw = q.get('minAttendees');
	const weekdaysRaw = q.get('weekdays');
	const intervalRaw = q.get('intervalDays');
	const patternType = q.get('patternType');
	const initial = q.has('title')
		? {
				title: q.get('title') ?? '',
				type: q.get('type') ?? 'training',
				startAt: q.get('startAt'),
				endAt: q.get('endAt'),
				meetupAt: q.get('meetupAt'),
				location: q.get('location'),
				minAttendees: minAttendeesRaw ? Number(minAttendeesRaw) : null,
				teamIds: q.get('teamId') ? [q.get('teamId') as string] : [],
				recurring: patternType
					? {
							patternType,
							weekdays: weekdaysRaw
								? weekdaysRaw.split(',').map(Number).filter((n) => !Number.isNaN(n))
								: null,
							intervalDays: intervalRaw ? Number(intervalRaw) : null,
							seriesEndDate: q.get('seriesEndDate')
						}
					: null
			}
		: null;

	return { teams: teams.map((t) => ({ id: t.id, name: t.name })), initial };
};

function parseRecurring(form: FormData) {
	if (form.get('recurringEnabled') !== 'true') return null;
	const patternType = (form.get('patternType') as string) || 'weekly';
	const weekdays = form.getAll('weekdays').map(Number).filter((n) => !Number.isNaN(n));
	const intervalRaw = (form.get('intervalDays') as string)?.trim();
	const seriesEndDate = (form.get('seriesEndDate') as string)?.trim() || null;
	return {
		patternType,
		weekdays: patternType === 'weekly' && weekdays.length > 0 ? weekdays : null,
		intervalDays: patternType === 'custom' && intervalRaw ? Number(intervalRaw) : null,
		seriesEndDate
	};
}

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
		minAttendees: minRaw ? Number(minRaw) : null,
		recurring: parseRecurring(form)
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
