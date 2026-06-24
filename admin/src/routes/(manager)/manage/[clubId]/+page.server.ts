import { apiPatch, apiPost } from '$lib/server/api';
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
		const data = await request.formData();
		const name = (data.get('name') as string) || undefined;
		const location = (data.get('location') as string) || undefined;
		await apiPatch(`/clubs/${params.clubId}`, locals.token!, { name, location });
		return { success: true };
	},

	inviteCoManager: async ({ request, params, locals }) => {
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
		} catch {
			return fail(400, { error: 'Failed to send invite' });
		}
	}
};
