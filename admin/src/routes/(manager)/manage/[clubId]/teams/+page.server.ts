import { apiGet, apiPost } from '$lib/server/api';
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
		const data = await request.formData();
		const name = data.get('name') as string;
		const description = (data.get('description') as string) || undefined;
		if (!name) return fail(400, { error: 'Team name required' });

		await apiPost(`/clubs/${params.clubId}/teams`, locals.token!, { name, description });
		return { success: true };
	}
};
