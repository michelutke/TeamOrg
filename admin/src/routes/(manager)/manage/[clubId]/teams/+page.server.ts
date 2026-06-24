import { apiGet, apiPost } from '$lib/server/api';
import { ApiError, assertClubAccess } from '$lib/server/guards';
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

export const load: PageServerLoad = async ({ params, locals }) => {
	const teams = await apiGet<Team[]>(`/clubs/${params.clubId}/teams`, locals.token!);
	return { teams };
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
	}
};
