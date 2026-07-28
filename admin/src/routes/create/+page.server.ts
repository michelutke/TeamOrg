import { fail, redirect } from '@sveltejs/kit';
import { register } from '$lib/server/auth';
import { createSelfServe } from '$lib/server/billing';
import { ApiError } from '$lib/server/guards';
import { getMessages, resolveLocale } from '$lib/i18n';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, cookies }) => {
	const lang = resolveLocale(cookies.get('lang'));
	const m = getMessages(lang);
	const step = locals.user ? ('details' as const) : ('account' as const);
	return { lang, m, step, billingEmail: locals.user?.email ?? '' };
};

export const actions: Actions = {
	register: async ({ request, cookies }) => {
		const m = getMessages(resolveLocale(cookies.get('lang'))).onboarding;
		const form = await request.formData();
		const displayName = (form.get('name') as string)?.trim();
		const email = (form.get('email') as string)?.trim();
		const password = form.get('password') as string;

		if (!displayName || !email || !password) {
			return fail(400, { error: m.registerFailed, name: displayName, email });
		}

		const reg = await register(email, password, displayName, cookies);
		if (!reg.success) {
			if (reg.error === 'email_taken') return fail(409, { error: m.emailTaken, name: displayName, email });
			return fail(400, { error: m.registerFailed, name: displayName, email });
		}

		throw redirect(303, '/create');
	},

	create: async ({ request, locals, cookies }) => {
		if (!locals.user || !locals.token) throw redirect(303, '/create');

		const m = getMessages(resolveLocale(cookies.get('lang'))).onboarding;
		const form = await request.formData();
		const kind = form.get('kind') as string;
		const name = (form.get('name') as string) ?? '';
		const sportType = form.get('sportType') as string | null;
		const location = form.get('location') as string | null;
		const billingEmail = (form.get('billingEmail') as string) ?? '';

		if (kind !== 'club' && kind !== 'team') {
			return fail(400, { error: m.registerFailed, kind, name, sportType, location, billingEmail });
		}
		if (!name.trim()) {
			return fail(400, { error: m.nameRequired, kind, name, sportType, location, billingEmail });
		}
		if (!billingEmail.includes('@')) {
			return fail(400, { error: m.billingEmailInvalid, kind, name, sportType, location, billingEmail });
		}

		let result: Awaited<ReturnType<typeof createSelfServe>>;
		try {
			result = await createSelfServe(locals.token, {
				kind,
				name: name.trim(),
				sportType: sportType?.trim() || undefined,
				location: location?.trim() || undefined,
				billingEmail: billingEmail.trim()
			});
		} catch (e) {
			if (e instanceof ApiError) {
				return fail(e.status, { error: e.message, kind, name, sportType, location, billingEmail });
			}
			throw e;
		}

		cookies.set('to_onboarding', JSON.stringify(result), {
			path: '/create',
			httpOnly: true,
			secure: false,
			sameSite: 'lax',
			maxAge: 1800
		});

		throw redirect(303, '/create/billing');
	}
};
