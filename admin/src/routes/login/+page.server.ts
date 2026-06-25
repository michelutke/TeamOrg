import { login, landingPathFor } from '$lib/server/auth';
import { safeRedirect } from '$lib/server/guards';
import { getMessages, resolveLocale } from '$lib/i18n';
import { redirect, fail } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, url, cookies }) => {
	const redirectTo = safeRedirect(url.searchParams.get('redirectTo'));
	if (locals.user) throw redirect(302, redirectTo ?? landingPathFor(locals.user));
	const lang = resolveLocale(cookies.get('lang'));
	return { lang, m: getMessages(lang).login, redirectTo };
};

export const actions: Actions = {
	default: async ({ request, cookies }) => {
		const data = await request.formData();
		const email = data.get('email') as string;
		const password = data.get('password') as string;
		const redirectTo = safeRedirect(data.get('redirectTo') as string);
		const m = getMessages(resolveLocale(cookies.get('lang'))).login;

		if (!email || !password) {
			return fail(400, { error: m.errRequired, email });
		}

		const result = await login(email, password, cookies);
		if (!result.success || !result.user) {
			return fail(401, { error: result.error, email });
		}

		throw redirect(302, redirectTo ?? landingPathFor(result.user));
	}
};
