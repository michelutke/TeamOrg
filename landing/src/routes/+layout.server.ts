import { redirect } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';
import { defaultLocale, getMessages, isLocale } from '$lib/i18n';
import type { LayoutServerLoad } from './$types';

const ONE_YEAR = 60 * 60 * 24 * 365;

export const load: LayoutServerLoad = async ({ url, cookies }) => {
	// Language switch via ?lang=de|en - persist in a cookie, then redirect to a
	// clean URL so the choice sticks and the param doesn't linger.
	const requested = url.searchParams.get('lang');
	if (requested) {
		if (isLocale(requested)) {
			cookies.set('lang', requested, { path: '/', maxAge: ONE_YEAR, sameSite: 'lax' });
		}
		throw redirect(303, url.pathname);
	}

	const cookieLang = cookies.get('lang');
	const lang = isLocale(cookieLang) ? cookieLang : defaultLocale;

	return {
		lang,
		m: getMessages(lang),
		turnstileSiteKey: env.TURNSTILE_SITEKEY ?? '',
		// Member/manager web app — the "Anmelden" link target. Overridable per deploy.
		appUrl: env.APP_URL ?? 'https://admin.teamorg.michelutke.com'
	};
};
