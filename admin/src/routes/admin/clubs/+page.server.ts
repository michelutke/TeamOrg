import { apiGet, apiPost } from '$lib/server/api';
import { fail, redirect } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';

interface ClubListItem {
	id: string;
	name: string;
	sportType: string;
	location: string | null;
	status: string;
	teamCount: number;
	memberCount: number;
	createdAt: string;
}

interface ClubsResponse {
	clubs: ClubListItem[];
	page: number;
	pageSize: number;
	totalCount: number;
}

export const load: PageServerLoad = async ({ locals, url }) => {
	const page = parseInt(url.searchParams.get('page') || '1');
	const clubs = await apiGet<ClubsResponse>(`/admin/clubs?page=${page}&pageSize=50`, locals.adminToken!);
	return { clubs, page };
};

export const actions: Actions = {
	create: async ({ request, locals }) => {
		const data = await request.formData();
		const name = data.get('name') as string;
		const sportType = (data.get('sportType') as string) || 'volleyball';
		const location = (data.get('location') as string) || null;
		const managerEmail = (data.get('managerEmail') as string) || null;

		if (!name) return fail(400, { error: 'Club name required' });

		const club = await apiPost<{ id: string }>('/admin/clubs', locals.adminToken!, {
			name,
			sportType,
			location,
			managerEmail
		});
		throw redirect(302, `/admin/clubs/${club.id}`);
	}
};
