import { fail } from '@sveltejs/kit';
import { resetPassword } from '$lib/server/auth';
import { getMessages, resolveLocale } from '$lib/i18n';
import type { Actions, PageServerLoad } from './$types';

const MIN_PASSWORD_LENGTH = 8;
const MAX_PASSWORD_LENGTH = 72;

export const load: PageServerLoad = async ({ cookies }) => {
	const lang = resolveLocale(cookies.get('lang'));
	return { lang, m: getMessages(lang).passwordReset };
};

export const actions: Actions = {
	default: async ({ request, params, cookies }) => {
		const data = await request.formData();
		const password = String(data.get('password') ?? '');
		const confirm = String(data.get('confirm') ?? '');
		const m = getMessages(resolveLocale(cookies.get('lang'))).passwordReset;

		if (password.length < MIN_PASSWORD_LENGTH) return fail(400, { error: m.errTooShort, invalidLink: false });
		if (password.length > MAX_PASSWORD_LENGTH) return fail(400, { error: m.errTooLong, invalidLink: false });
		if (password !== confirm) return fail(400, { error: m.errMismatch, invalidLink: false });

		let result: Awaited<ReturnType<typeof resetPassword>>;
		try {
			result = await resetPassword(params.token, password);
		} catch {
			return fail(502, { error: m.errGeneric, invalidLink: false });
		}
		if (result === 'invalid_token') return fail(400, { error: m.errInvalidLink, invalidLink: true });
		if (result === 'error') return fail(502, { error: m.errGeneric, invalidLink: false });
		return { success: true, invalidLink: false };
	}
};
