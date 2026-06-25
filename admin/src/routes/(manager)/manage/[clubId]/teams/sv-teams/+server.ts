import { apiGet } from '$lib/server/api';
import { ApiError, assertClubAccess } from '$lib/server/guards';
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

interface SvLeague {
	leagueId: number | null;
	caption: string | null;
}

interface SvTeam {
	teamId: number | null;
	seasonalTeamId: number | null;
	caption: string | null;
	gender: string | null;
	league: SvLeague | null;
}

export const GET: RequestHandler = async ({ params, locals }) => {
	assertClubAccess(locals, params.clubId);
	try {
		const teams = await apiGet<SvTeam[]>(
			`/clubs/${params.clubId}/integrations/swissvolley/teams`,
			locals.token!
		);
		return json({ teams });
	} catch (err) {
		// 409 → no valid SwissVolley key stored.
		if (err instanceof ApiError && err.status === 409) {
			return json({ noKey: true }, { status: 409 });
		}
		return json({ error: true }, { status: 502 });
	}
};
