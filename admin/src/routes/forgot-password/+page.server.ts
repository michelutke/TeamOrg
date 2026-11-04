import { fail } from '@sveltejs/kit';
import { requestPasswordReset } from '$lib/server/auth';
import { getMessages, resolveLocale } from '$lib/i18n';
import type { Actions, PageServerLoad } from './$types';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const load: PageServerLoad = async ({ cookies }) => {
	const lang = resolveLocale(cookies.get('lang'));
	return { lang, m: getMessages(lang).passwordReset };
};

export const actions: Actions = {
	default: async ({ request, cookies }) => {
		const data = await request.formData();
		const email = String(data.get('email') ?? '').trim();
		const m = getMessages(resolveLocale(cookies.get('lang'))).passwordReset;

		if (!EMAIL_PATTERN.test(email)) {
			return fail(400, { error: m.errEmail, email });
		}

		try {
			await requestPasswordReset(email);
		} catch {
			// Same neutral outcome as success: the page never reveals whether an account exists.
		}
		return { sent: true };
	}
};
