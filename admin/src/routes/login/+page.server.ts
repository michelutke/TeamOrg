import { login, landingPathFor } from '$lib/server/auth';
import { getMessages, resolveLocale } from '$lib/i18n';
import { redirect, fail } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, cookies }) => {
	if (locals.user) throw redirect(302, landingPathFor(locals.user));
	const lang = resolveLocale(cookies.get('lang'));
	return { lang, m: getMessages(lang).login };
};

export const actions: Actions = {
	default: async ({ request, cookies }) => {
		const data = await request.formData();
		const email = data.get('email') as string;
		const password = data.get('password') as string;
		const m = getMessages(resolveLocale(cookies.get('lang'))).login;

		if (!email || !password) {
			return fail(400, { error: m.errRequired, email });
		}

		const result = await login(email, password, cookies);
		if (!result.success || !result.user) {
			return fail(401, { error: result.error, email });
		}

		throw redirect(302, landingPathFor(result.user));
	}
};
