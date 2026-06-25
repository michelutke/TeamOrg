import { apiGet, apiPost } from '$lib/server/api';
import { ApiError, assertClubAccess } from '$lib/server/guards';
import { getMessages, resolveLocale } from '$lib/i18n';
import { fail } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';

interface Team {
	id: string;
	clubId: string;
	name: string;
	memberCount: number;
	description: string | null;
	archivedAt: string | null;
	createdAt: string;
}

interface SwissVolleyStatus {
	provider: string;
	keyValid: boolean | null;
	lastValidatedAt: string | null;
	syncPausedReason: string | null;
}

interface ImportedTeam {
	teamId: string;
	svTeamId: number;
	name: string;
}

interface ImportResult {
	created: ImportedTeam[];
	skipped: number[];
}

export const load: PageServerLoad = async ({ params, locals, cookies }) => {
	const teams = await apiGet<Team[]>(`/clubs/${params.clubId}/teams`, locals.token!);

	let swissVolleyConnected = false;
	try {
		const status = await apiGet<SwissVolleyStatus>(
			`/clubs/${params.clubId}/integrations/swissvolley`,
			locals.token!
		);
		swissVolleyConnected = status.keyValid === true;
	} catch {
		// 404 (no integration) or any error → no import action shown.
		swissVolleyConnected = false;
	}

	const lang = resolveLocale(cookies.get('lang'));
	return { teams, swissVolleyConnected, lang, m: getMessages(lang) };
};

export const actions: Actions = {
	create: async ({ request, params, locals }) => {
		assertClubAccess(locals, params.clubId);
		const data = await request.formData();
		const name = data.get('name') as string;
		const description = (data.get('description') as string) || undefined;
		if (!name) return fail(400, { error: 'Team name required' });

		try {
			await apiPost(`/clubs/${params.clubId}/teams`, locals.token!, { name, description });
			return { success: true };
		} catch (err) {
			if (err instanceof ApiError && err.status === 403)
				return fail(403, { error: 'Not authorized to create teams for this club' });
			return fail(500, { error: 'Failed to create team' });
		}
	},

	importSv: async ({ request, params, locals }) => {
		assertClubAccess(locals, params.clubId);
		const data = await request.formData();
		const svTeamIds = data
			.getAll('svTeamIds')
			.map((v) => Number(v))
			.filter((n) => Number.isInteger(n));
		if (svTeamIds.length === 0) return fail(400, { importError: 'selectAtLeastOne' });

		try {
			const result = await apiPost<ImportResult>(`/clubs/${params.clubId}/teams/import`, locals.token!, {
				svTeamIds
			});
			return { imported: result };
		} catch (err) {
			if (err instanceof ApiError && err.status === 409)
				return fail(409, { importError: 'noKeyBody' });
			return fail(500, { importError: 'importFailed' });
		}
	}
};
