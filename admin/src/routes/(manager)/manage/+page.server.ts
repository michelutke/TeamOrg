import { redirect, error } from '@sveltejs/kit';
import { apiGet } from '$lib/server/api';
import type { PageServerLoad } from './$types';

interface Club {
	id: string;
	name: string;
	sportType: string;
	location: string | null;
}

export const load: PageServerLoad = async ({ locals }) => {
	if (!locals.user) throw redirect(302, '/admin/login');

	const managedClubIds = locals.user.managedClubIds;

	// Super-admins landing here without managed clubs have no business here
	if (managedClubIds.length === 0) {
		if (locals.user.isSuperAdmin) throw redirect(302, '/admin/dashboard');
		throw error(403, 'No managed clubs found');
	}

	// Single club: skip the picker
	if (managedClubIds.length === 1) {
		throw redirect(302, `/manage/${managedClubIds[0]}`);
	}

	// Multiple clubs: fetch names; skip any that fail rather than 500ing the picker
	const results = await Promise.allSettled(
		managedClubIds.map((id) => apiGet<Club>(`/clubs/${id}`, locals.token!))
	);

	const clubs = results
		.filter((r): r is PromiseFulfilledResult<Club> => r.status === 'fulfilled')
		.map((r) => r.value);

	if (clubs.length === 0) throw error(503, 'Could not load any club data');

	return { clubs };
};
