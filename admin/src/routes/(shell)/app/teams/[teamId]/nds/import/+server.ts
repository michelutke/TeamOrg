import { apiGet, apiPost } from '$lib/server/api';
import { ApiError, requireTeamManage } from '$lib/server/guards';
import type { Team } from '$lib/server/teams';
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

// Proxy: forwards the wizard's assembled NdsImportRequest to the backend, scoped to this team.
export const POST: RequestHandler = async ({ request, params, locals }) => {
	const team = await apiGet<Team>(`/teams/${params.teamId}`, locals.token!);
	requireTeamManage(locals, params.teamId, team.clubId);
	const body = await request.json();
	body.teamId = params.teamId; // fixed entry point — never let the request override the team
	try {
		const result = await apiPost(`/clubs/${team.clubId}/nds/import`, locals.token!, body);
		return json(result);
	} catch (err) {
		if (err instanceof ApiError && err.status === 409)
			return json({ error: 'angebotLinked' }, { status: 409 });
		if (err instanceof ApiError) return json({ error: 'failed' }, { status: err.status });
		return json({ error: 'failed' }, { status: 502 });
	}
};
