import { redirect } from '@sveltejs/kit';
import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals, url }) => {
	if (!locals.user && !url.pathname.startsWith('/admin/login')) {
		throw redirect(302, '/admin/login');
	}

	// Club managers who are not impersonating belong in the manager UI
	if (
		locals.user &&
		!locals.user.isSuperAdmin &&
		!locals.impersonation?.active &&
		!url.pathname.startsWith('/admin/login') &&
		!url.pathname.startsWith('/admin/logout')
	) {
		throw redirect(302, '/manage');
	}

	return {
		user: locals.user,
		impersonation: locals.impersonation || null
	};
};
