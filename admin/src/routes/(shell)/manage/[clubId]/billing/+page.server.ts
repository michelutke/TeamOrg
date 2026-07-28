import { error, fail, redirect } from '@sveltejs/kit';
import { env } from '$env/dynamic/public';
import { getBilling, updateCard, confirmBilling, convertClub } from '$lib/server/billing';
import { ApiError, assertClubAccess } from '$lib/server/guards';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ params, locals }) => {
	try {
		const billing = await getBilling(locals.token!, params.clubId);
		return { billing, publishableKey: env.PUBLIC_STRIPE_PUBLISHABLE_KEY ?? '' };
	} catch (err) {
		if (err instanceof ApiError && err.status === 403) {
			throw error(403, 'Only the club owner can view billing');
		}
		throw err;
	}
};

export const actions: Actions = {
	updateCard: async ({ params, locals }) => {
		assertClubAccess(locals, params.clubId);
		try {
			const result = await updateCard(locals.token!, params.clubId);
			return { clientSecret: result.setupIntentClientSecret };
		} catch (err) {
			if (err instanceof ApiError) return fail(err.status, { cardError: true });
			return fail(500, { cardError: true });
		}
	},

	confirmCard: async ({ request, params, locals }) => {
		assertClubAccess(locals, params.clubId);
		const data = await request.formData();
		const setupIntentId = data.get('setupIntentId') as string | null;
		if (!setupIntentId) return fail(400, { cardError: true });

		try {
			await confirmBilling(locals.token!, params.clubId, setupIntentId);
		} catch (err) {
			if (err instanceof ApiError) return fail(err.status, { cardError: true });
			return fail(500, { cardError: true });
		}
		throw redirect(303, `/manage/${params.clubId}/billing`);
	},

	convert: async ({ request, params, locals }) => {
		assertClubAccess(locals, params.clubId);
		const data = await request.formData();
		const targetKind = data.get('targetKind');
		if (targetKind !== 'club' && targetKind !== 'team') return fail(400, { convertError: true });

		try {
			await convertClub(locals.token!, params.clubId, targetKind);
		} catch (err) {
			if (err instanceof ApiError && err.status === 409) return fail(409, { convertError: true });
			if (err instanceof ApiError) return fail(err.status, { convertError: true });
			return fail(500, { convertError: true });
		}
		throw redirect(303, `/manage/${params.clubId}/billing?converted=${targetKind}`);
	}
};
