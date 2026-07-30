import { apiPost } from '$lib/server/api';
import { ApiError, assertClubAccess } from '$lib/server/guards';
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

// Proxy: forwards the wizard's assembled NdsImportRequest to the backend and returns the result.
export const POST: RequestHandler = async ({ request, params, locals }) => {
	assertClubAccess(locals, params.clubId);
	const body = await request.json();
	try {
		const result = await apiPost(`/clubs/${params.clubId}/nds/import`, locals.token!, body);
		return json(result);
	} catch (err) {
		if (err instanceof ApiError && err.status === 409)
			return json({ error: 'angebotLinked' }, { status: 409 });
		if (err instanceof ApiError) return json({ error: 'failed' }, { status: err.status });
		return json({ error: 'failed' }, { status: 502 });
	}
};
