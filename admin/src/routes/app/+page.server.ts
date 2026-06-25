import { requireUser } from '$lib/server/guards';
import { loadUserTeams } from '$lib/server/teams';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals }) => {
	const user = requireUser(locals);
	const teams = await loadUserTeams(user, locals.token!);
	return { teams };
};
