import { redirect } from '@sveltejs/kit';
import { landingPathFor } from '$lib/server/auth';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals }) => {
	if (locals.user) throw redirect(302, landingPathFor(locals.user));
	throw redirect(302, '/login');
};
