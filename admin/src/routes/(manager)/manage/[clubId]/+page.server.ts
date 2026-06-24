import { apiPatch, apiPost } from '$lib/server/api';
import { ApiError, assertClubAccess } from '$lib/server/guards';
import { fail } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';

interface InviteResponse {
	token: string;
	inviteUrl: string;
	expiresAt: string;
}

export const load: PageServerLoad = async () => {
	// Club data is loaded by the layout; nothing extra needed here
	return {};
};

export const actions: Actions = {
	edit: async ({ request, params, locals }) => {
		assertClubAccess(locals, params.clubId);
		const data = await request.formData();
		const name = (data.get('name') as string) || undefined;
		const location = (data.get('location') as string) || undefined;
		try {
			await apiPatch(`/clubs/${params.clubId}`, locals.token!, { name, location });
			return { success: true };
		} catch (err) {
			if (err instanceof ApiError && err.status === 403)
				return fail(403, { error: 'Not authorized to edit this club' });
			return fail(500, { error: 'Failed to save changes' });
		}
	},

	inviteCoManager: async ({ request, params, locals }) => {
		assertClubAccess(locals, params.clubId);
		const data = await request.formData();
		const email = data.get('email') as string;
		if (!email) return fail(400, { error: 'Email required' });

		try {
			const invite = await apiPost<InviteResponse>(
				`/clubs/${params.clubId}/invites`,
				locals.token!,
				{ role: 'club_manager', email }
			);
			return { success: true, action: 'invite_sent', inviteUrl: invite.inviteUrl, expiresAt: invite.expiresAt };
		} catch (err) {
			if (err instanceof ApiError && err.status === 403)
				return fail(403, { error: 'Not authorized to invite co-managers' });
			return fail(400, { error: 'Failed to send invite' });
		}
	}
};
