import { redirect } from '@sveltejs/kit';
import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals, url }) => {
	if (!locals.user && !url.pathname.startsWith('/admin/login')) {
		throw redirect(302, '/admin/login');
	}
	return {
		user: locals.user,
		impersonation: locals.impersonation || null
	};
};
