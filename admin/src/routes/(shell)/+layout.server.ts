import { redirect } from '@sveltejs/kit';
import { requireUser } from '$lib/server/guards';
import { apiGet } from '$lib/server/api';
import { getMessages, isLocale, resolveLocale } from '$lib/i18n';
import type { LayoutServerLoad } from './$types';

const ONE_YEAR = 60 * 60 * 24 * 365;

interface Club {
	id: string;
	name: string;
}

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

	// Names for the sidebar's Club Management section / club switcher.
	const managedClubs =
		user.managedClubIds.length > 0
			? (
					await Promise.allSettled(
						user.managedClubIds.map((id) => apiGet<Club>(`/clubs/${id}`, locals.token!))
					)
				)
					.filter((r): r is PromiseFulfilledResult<Club> => r.status === 'fulfilled')
					.map((r) => ({ id: r.value.id, name: r.value.name }))
			: [];

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
		},
		managedClubs
	};
};
