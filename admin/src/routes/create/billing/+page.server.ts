import { fail, redirect } from '@sveltejs/kit';
import { env } from '$env/dynamic/public';
import { confirmBilling } from '$lib/server/billing';
import { ApiError } from '$lib/server/guards';
import { getMessages, resolveLocale } from '$lib/i18n';
import type { Actions, PageServerLoad } from './$types';

function readOnboardingCookie(raw: string | undefined) {
	if (!raw) return null;
	try {
		const parsed = JSON.parse(raw);
		if (!parsed || typeof parsed.clubId !== 'string' || typeof parsed.setupIntentClientSecret !== 'string') {
			return null;
		}
		return parsed as {
			clubId: string;
			teamId?: string | null;
			setupIntentClientSecret: string;
			userId?: string;
		};
	} catch {
		return null;
	}
}

export const load: PageServerLoad = async ({ locals, cookies }) => {
	const m = getMessages(resolveLocale(cookies.get('lang')));

	if (!locals.user || !locals.token) throw redirect(303, '/create');

	const onboarding = readOnboardingCookie(cookies.get('to_onboarding'));
	if (!onboarding) throw redirect(303, '/create');
	// The handoff belongs to the account that created the club — a different user in
	// the same browser must not resume someone else's card step.
	if (onboarding.userId && onboarding.userId !== locals.user.id) throw redirect(303, '/create');

	return {
		clubId: onboarding.clubId,
		clientSecret: onboarding.setupIntentClientSecret,
		publishableKey: env.PUBLIC_STRIPE_PUBLISHABLE_KEY ?? '',
		m
	};
};

export const actions: Actions = {
	confirm: async ({ request, locals, cookies }) => {
		const m = getMessages(resolveLocale(cookies.get('lang'))).onboarding;

		const onboarding = readOnboardingCookie(cookies.get('to_onboarding'));
		if (!onboarding || !locals.token) {
			return fail(400, { error: m.cardError });
		}
		if (onboarding.userId && onboarding.userId !== locals.user?.id) {
			return fail(403, { error: m.cardError });
		}

		const form = await request.formData();
		const setupIntentId = form.get('setupIntentId') as string | null;
		if (!setupIntentId) {
			return fail(400, { error: m.cardError });
		}

		try {
			await confirmBilling(locals.token, onboarding.clubId, setupIntentId);
		} catch (e) {
			if (e instanceof ApiError) {
				return fail(e.status, { error: m.cardError });
			}
			throw e;
		}

		cookies.delete('to_onboarding', { path: '/create' });
		throw redirect(303, '/manage/' + onboarding.clubId);
	}
};
