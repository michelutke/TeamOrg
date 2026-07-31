import { apiGet, apiPostForm } from '$lib/server/api';
import { ApiError, requireTeamManage } from '$lib/server/guards';
import type { Team } from '$lib/server/teams';
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

// Proxy: forwards the wizard's file uploads to the backend parser, scoped to this team (the
// team-page entry point — a coach without club-manage access). Mirrors
// manage/[clubId]/nds/parse/+server.ts, which the club-manage entry point uses instead.
export const POST: RequestHandler = async ({ request, params, locals }) => {
	const team = await apiGet<Team>(`/teams/${params.teamId}`, locals.token!);
	requireTeamManage(locals, params.teamId, team.clubId);
	const form = await request.formData();
	if (!form.has('teamId')) form.append('teamId', params.teamId);
	try {
		const parsed = await apiPostForm(`/clubs/${team.clubId}/nds/parse`, locals.token!, form);
		return json(parsed);
	} catch (err) {
		if (err instanceof ApiError && err.status === 422)
			return json({ error: 'parse' }, { status: 422 });
		if (err instanceof ApiError) return json({ error: 'failed' }, { status: err.status });
		return json({ error: 'failed' }, { status: 502 });
	}
};
