import { redirect } from '@sveltejs/kit';
import { landingPathFor } from '$lib/server/auth';
import { getMessages, resolveLocale } from '$lib/i18n';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, cookies }) => {
	if (locals.user) throw redirect(303, landingPathFor(locals.user));
	const lang = resolveLocale(cookies.get('lang'));
	return { lang, m: getMessages(lang).onboarding };
};
