import { startImpersonation } from '$lib/server/impersonation';
import { redirect, fail } from '@sveltejs/kit';
import type { Actions } from './$types';

export const actions: Actions = {
	default: async ({ request, cookies, locals }) => {
		const data = await request.formData();
		const targetUserId = data.get('targetUserId') as string;
		const redirectTo = (data.get('redirectTo') as string) || '/admin/dashboard';
		const clubId = data.get('clubId') as string | null;
		const clubName = data.get('clubName') as string | null;
		if (!targetUserId) return fail(400, { error: 'Target user ID required' });

		const club = clubId && clubName ? { id: clubId, name: clubName } : undefined;
		const result = await startImpersonation(targetUserId, cookies, locals.token!, club);
		if (!result.success) return fail(400, { error: result.error });

		throw redirect(302, redirectTo);
	}
};
