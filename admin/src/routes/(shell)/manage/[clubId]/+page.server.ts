import { apiPatch, apiPost, apiPostForm } from '$lib/server/api';
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

	uploadLogo: async ({ request, params, locals }) => {
		assertClubAccess(locals, params.clubId);
		const data = await request.formData();
		const file = data.get('logo');
		if (!(file instanceof File) || file.size === 0) {
			return fail(400, { logoError: 'Please choose an image file' });
		}
		if (file.size > 2 * 1024 * 1024) {
			return fail(400, { logoError: 'Logo must be smaller than 2MB' });
		}
		const forward = new FormData();
		forward.append('logo', file, file.name);
		try {
			await apiPostForm(`/clubs/${params.clubId}/logo`, locals.token!, forward);
			return { success: true, action: 'logo_uploaded' };
		} catch (err) {
			if (err instanceof ApiError && err.status === 403)
				return fail(403, { logoError: 'Not authorized to update this club' });
			if (err instanceof ApiError && err.status === 400)
				return fail(400, { logoError: 'Invalid image (use jpg, png or webp under 2MB)' });
			return fail(500, { logoError: 'Failed to upload logo' });
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
