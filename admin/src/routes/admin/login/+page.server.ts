import { landingPathFor } from '$lib/server/auth';
import { redirect } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';

// Login consolidated at the neutral /login route. Keep this path working for old
// bookmarks by redirecting.
export const load: PageServerLoad = async ({ locals }) => {
	if (locals.user) throw redirect(302, landingPathFor(locals.user));
	throw redirect(302, '/login');
};
