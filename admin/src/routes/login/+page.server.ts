import { login, landingPathFor } from '$lib/server/auth';
import { redirect, fail } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals }) => {
	if (locals.user) throw redirect(302, landingPathFor(locals.user));
};

export const actions: Actions = {
	default: async ({ request, cookies }) => {
		const data = await request.formData();
		const email = data.get('email') as string;
		const password = data.get('password') as string;

		if (!email || !password) {
			return fail(400, { error: 'E-Mail und Passwort erforderlich', email });
		}

		const result = await login(email, password, cookies);
		if (!result.success || !result.user) {
			return fail(401, { error: result.error, email });
		}

		throw redirect(302, landingPathFor(result.user));
	}
};
