import { fail } from '@sveltejs/kit';
import { requireUser, ApiError } from '$lib/server/guards';
import { apiPost } from '$lib/server/api';
import { getMessages, resolveLocale } from '$lib/i18n';
import type { Actions } from './$types';

export const actions: Actions = {
	changePassword: async ({ request, locals, cookies }) => {
		requireUser(locals);
		const m = getMessages(resolveLocale(cookies.get('lang'))).profile;
		const form = await request.formData();
		const currentPassword = form.get('currentPassword') as string;
		const newPassword = form.get('newPassword') as string;
		const confirmPassword = form.get('confirmPassword') as string;

		if (newPassword !== confirmPassword) {
			return fail(400, { error: m.passwordMismatch });
		}

		try {
			await apiPost('/auth/change-password', locals.token!, { currentPassword, newPassword });
			return { success: true };
		} catch (e) {
			if (e instanceof ApiError) {
				if (e.status === 401) return fail(401, { error: m.passwordWrongCurrent });
				if (e.status === 400) return fail(400, { error: m.passwordTooShort });
			}
			return fail(500, { error: m.passwordChangeFailed });
		}
	}
};
