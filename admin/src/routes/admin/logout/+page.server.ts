import { logout } from '$lib/server/auth';
import { redirect } from '@sveltejs/kit';
import type { Actions } from './$types';

export const actions: Actions = {
	default: async ({ cookies }) => {
		logout(cookies);
		throw redirect(302, '/admin/login');
	}
};
