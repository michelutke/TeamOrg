import { redirect, error } from '@sveltejs/kit';
import { apiGet } from '$lib/server/api';
import type { LayoutServerLoad } from './$types';

interface Club {
	id: string;
	name: string;
	sportType: string;
	location: string | null;
	status: string;
}

export const load: LayoutServerLoad = async ({ params, locals }) => {
	if (!locals.user) throw redirect(302, '/login');

	const { clubId } = params;
	const { managedClubIds, isSuperAdmin } = locals.user;

	// Server-side RBAC: only the club's managers (or super-admins) may proceed
	if (!isSuperAdmin && !managedClubIds.includes(clubId)) {
		throw error(403, 'You do not have access to this club');
	}

	const club = await apiGet<Club>(`/clubs/${clubId}`, locals.token!);

	return { club, clubId };
};
