import { apiPut } from '$lib/server/api';
import { ApiError } from '$lib/server/guards';
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

// Instant RSVP from the event list. Proxies the caller's response to the backend so the
// session token never reaches the browser. Body: { eventId, status, reason? }.
export const POST: RequestHandler = async ({ request, locals }) => {
	if (!locals.token) return json({ error: 'unauthorized' }, { status: 401 });
	const { eventId, status, reason } = await request.json();
	if (!eventId || !status) return json({ error: 'bad_request' }, { status: 400 });
	try {
		await apiPut(`/events/${eventId}/attendance/me`, locals.token, { status, reason: reason ?? null });
		return json({ ok: true });
	} catch (err) {
		if (err instanceof ApiError && err.status === 409) return json({ error: 'deadline' }, { status: 409 });
		if (err instanceof ApiError && err.status === 400) return json({ error: 'reason' }, { status: 400 });
		return json({ error: 'failed' }, { status: 502 });
	}
};
