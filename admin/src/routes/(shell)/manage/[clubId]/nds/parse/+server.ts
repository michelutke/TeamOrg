import { apiPostForm } from '$lib/server/api';
import { ApiError, assertClubAccess } from '$lib/server/guards';
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

// Proxy: forwards the uploaded Anwesenheitsliste xlsx to the backend parser and returns the
// preview JSON. The browser uploads here so the backend token never reaches the client.
export const POST: RequestHandler = async ({ request, params, locals }) => {
	assertClubAccess(locals, params.clubId);
	const form = await request.formData();
	try {
		const parsed = await apiPostForm(`/clubs/${params.clubId}/nds/parse`, locals.token!, form);
		return json(parsed);
	} catch (err) {
		if (err instanceof ApiError && err.status === 422)
			return json({ error: 'parse' }, { status: 422 });
		if (err instanceof ApiError && err.status === 400)
			return json({ error: 'noFile' }, { status: 400 });
		return json({ error: 'failed' }, { status: 502 });
	}
};
