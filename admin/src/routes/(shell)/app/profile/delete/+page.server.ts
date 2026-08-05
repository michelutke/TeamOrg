import { fail, redirect } from '@sveltejs/kit';
import { requireUser, ApiError } from '$lib/server/guards';
import { apiDeleteJson } from '$lib/server/api';
import { logout } from '$lib/server/auth';
import { getMessages, resolveLocale } from '$lib/i18n';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals }) => {
	const user = requireUser(locals);
	// Drives the "your teams will have no coach" warning. locals.user already carries the
	// caller's team roles, so this needs no extra request.
	return { isCoach: user.teamRoles.some((r) => r.role === 'coach') };
};

export const actions: Actions = {
	default: async ({ request, locals, cookies }) => {
		requireUser(locals);
		const m = getMessages(resolveLocale(cookies.get('lang'))).profile;
		const form = await request.formData();
		const password = form.get('password') as string;

		if (!password) {
			return fail(400, { error: m.deleteWrongPassword });
		}

		try {
			await apiDeleteJson('/auth/me', locals.token!, { password });
		} catch (e) {
			if (e instanceof ApiError) {
				if (e.status === 401) return fail(401, { error: m.deleteWrongPassword });
				if (e.status === 409) {
					const clubs = (e.payload as { clubs?: string[] } | undefined)?.clubs ?? [];
					const names = clubs.length > 0 ? clubs.join(', ') : '—';
					return fail(409, { error: m.deleteOwnsClubs.replace('{clubs}', names) });
				}
			}
			return fail(500, { error: m.deleteFailed });
		}

		logout(cookies);
		throw redirect(303, '/login');
	}
};
