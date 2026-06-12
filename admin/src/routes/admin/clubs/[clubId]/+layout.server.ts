import { apiGet } from '$lib/server/api';
import type { LayoutServerLoad } from './$types';

interface Manager {
	userId: string;
	displayName: string;
	email: string;
}

interface ClubDetail {
	id: string;
	name: string;
	sportType: string;
	location: string | null;
	status: string;
	createdAt: string;
	managers: Manager[];
}

export const load: LayoutServerLoad = async ({ params, locals }) => {
	const club = await apiGet<ClubDetail>(`/admin/clubs/${params.clubId}`, locals.adminToken!);
	return {
		club,
		impersonating: !!locals.impersonation?.active,
		clubId: params.clubId
	};
};
