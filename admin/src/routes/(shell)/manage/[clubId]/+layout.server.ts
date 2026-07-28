import { redirect, error } from '@sveltejs/kit';
import { apiGet } from '$lib/server/api';
import { getMessages, resolveLocale } from '$lib/i18n';
import type { LayoutServerLoad } from './$types';

interface Club {
	id: string;
	name: string;
	sportType: string;
	location: string | null;
	logoUrl: string | null;
	status: string;
	billingStatus: 'active' | 'past_due' | 'frozen';
	billingMode: 'stripe' | 'manual' | 'free';
}

export const load: LayoutServerLoad = async ({ params, locals, cookies }) => {
	if (!locals.user) throw redirect(302, '/login');

	const { clubId } = params;
	const { managedClubIds, isSuperAdmin } = locals.user;

	// Server-side RBAC: only the club's managers (or super-admins) may proceed
	if (!isSuperAdmin && !managedClubIds.includes(clubId)) {
		throw error(403, 'You do not have access to this club');
	}

	const club = await apiGet<Club>(`/clubs/${clubId}`, locals.token!);

	const lang = resolveLocale(cookies.get('lang'));

	return { club, clubId, lang, m: getMessages(lang) };
};
