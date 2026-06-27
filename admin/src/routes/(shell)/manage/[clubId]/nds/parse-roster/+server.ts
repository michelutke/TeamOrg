import { apiPostForm } from '$lib/server/api';
import { ApiError, assertClubAccess } from '$lib/server/guards';
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

// Proxy: forwards a dedicated NDS person export (Teilnehmende .csv / Leiter .xlsx) to the backend
// parser and returns the parsed person list (with PERSONENNUMMER).
export const POST: RequestHandler = async ({ request, params, locals }) => {
	assertClubAccess(locals, params.clubId);
	const form = await request.formData();
	try {
		const persons = await apiPostForm(`/clubs/${params.clubId}/nds/parse-roster`, locals.token!, form);
		return json(persons);
	} catch (err) {
		if (err instanceof ApiError && err.status === 422)
			return json({ error: 'parse' }, { status: 422 });
		return json({ error: 'failed' }, { status: 502 });
	}
};
