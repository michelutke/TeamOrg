import { redirect } from '@sveltejs/kit';
import { requireUser } from '$lib/server/guards';
import { getMessages, isLocale, resolveLocale } from '$lib/i18n';
import type { LayoutServerLoad } from './$types';

const ONE_YEAR = 60 * 60 * 24 * 365;

export const load: LayoutServerLoad = async ({ locals, url, cookies }) => {
	const user = requireUser(locals);

	// Language switch via ?lang=de|en — persist in a cookie, redirect to a clean URL.
	const requested = url.searchParams.get('lang');
	if (requested) {
		if (isLocale(requested)) {
			cookies.set('lang', requested, { path: '/', maxAge: ONE_YEAR, sameSite: 'lax' });
		}
		throw redirect(303, url.pathname);
	}

	const lang = resolveLocale(cookies.get('lang'));

	return {
		lang,
		m: getMessages(lang),
		user: {
			id: user.id,
			displayName: user.displayName,
			email: user.email,
			isSuperAdmin: user.isSuperAdmin,
			managedClubIds: user.managedClubIds,
			teamRoles: user.teamRoles
		}
	};
};
