import { requireUser } from '$lib/server/guards';
import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals }) => {
	const user = requireUser(locals);

	return {
		user: {
			id: user.id,
			displayName: user.displayName,
			email: user.email,
			isSuperAdmin: user.isSuperAdmin,
			managedClubIds: user.managedClubIds,
			teamRoles: user.teamRoles
		}
	};
};
