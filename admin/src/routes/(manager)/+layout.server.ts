import { redirect } from '@sveltejs/kit';
import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals, url }) => {
	if (!locals.user) {
		throw redirect(302, '/admin/login');
	}

	// Super-admins who accidentally land here go back to /admin
	if (locals.user.isSuperAdmin && !url.pathname.startsWith('/manage')) {
		throw redirect(302, '/admin/dashboard');
	}

	return {
		user: locals.user
	};
};
