import { redirect, fail } from '@sveltejs/kit';
import { lookupInviteCode } from '$lib/server/billing';
import { ApiError } from '$lib/server/guards';
import { getMessages, resolveLocale } from '$lib/i18n';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ url, cookies }) => {
	const lang = resolveLocale(cookies.get('lang'));
	const code = url.searchParams.get('code') ?? '';
	return { lang, m: getMessages(lang).onboarding, code };
};

export const actions: Actions = {
	default: async ({ request, cookies }) => {
		const data = await request.formData();
		const rawCode = String(data.get('code') ?? '');
		const m = getMessages(resolveLocale(cookies.get('lang'))).onboarding;

		const normalizedCode = rawCode.trim().replaceAll(/[\s-]/g, '').toUpperCase();
		if (normalizedCode.length !== 8) {
			return fail(400, { error: m.joinCodeInvalid, code: rawCode });
		}

		let result: Awaited<ReturnType<typeof lookupInviteCode>>;
		try {
			result = await lookupInviteCode(normalizedCode);
		} catch (e) {
			if (e instanceof ApiError && e.status === 404) {
				return fail(404, { error: m.joinCodeInvalid, code: rawCode });
			}
			throw e;
		}

		throw redirect(303, `/i/${result.token}`);
	}
};
