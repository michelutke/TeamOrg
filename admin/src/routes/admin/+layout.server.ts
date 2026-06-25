import { redirect } from '@sveltejs/kit';
import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals, url }) => {
	if (!locals.user) {
		throw redirect(302, '/login');
	}

	// Non-super-admins who are not impersonating belong in the member surface
	if (
		!locals.user.isSuperAdmin &&
		!locals.impersonation?.active &&
		!url.pathname.startsWith('/admin/logout')
	) {
		throw redirect(302, '/app');
	}

	return {
		user: locals.user,
		impersonation: locals.impersonation || null
	};
};
