import { apiGet } from '$lib/server/api';
import { ApiError, assertClubAccess } from '$lib/server/guards';
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

// Proxy: team roster for the wizard's Mitglieder-Zuordnung step (manage entry — the team-page
// entry already has the roster from its own page load, so it passes it in directly instead).
export const GET: RequestHandler = async ({ url, params, locals }) => {
	assertClubAccess(locals, params.clubId);
	const teamId = url.searchParams.get('teamId');
	if (!teamId) return json({ error: 'teamId required' }, { status: 400 });
	try {
		const members = await apiGet(`/teams/${teamId}/members`, locals.token!);
		return json(members);
	} catch (err) {
		if (err instanceof ApiError) return json({ error: 'failed' }, { status: err.status });
		return json({ error: 'failed' }, { status: 502 });
	}
};
