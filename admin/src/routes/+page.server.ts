import { redirect } from '@sveltejs/kit';
import { landingPathFor } from '$lib/server/auth';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals }) => {
	if (locals.user) throw redirect(302, landingPathFor(locals.user));
	// Anonymous visitors get the onboarding chooser, not a login dead-end.
	throw redirect(302, '/start');
};
