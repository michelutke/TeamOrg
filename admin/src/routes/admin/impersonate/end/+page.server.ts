import { endImpersonation } from '$lib/server/impersonation';
import { redirect } from '@sveltejs/kit';
import type { Actions } from './$types';

export const actions: Actions = {
	default: async ({ cookies }) => {
		await endImpersonation(cookies);
		throw redirect(302, '/admin/dashboard');
	}
};
