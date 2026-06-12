import { apiPost, apiPatch, apiDelete } from '$lib/server/api';
import { fail, redirect } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ params, parent }) => {
	const { impersonating } = await parent();
	if (impersonating) throw redirect(302, `/admin/clubs/${params.clubId}/teams`);
};

export const actions: Actions = {
	edit: async ({ request, params, locals }) => {
		const data = await request.formData();
		const name = (data.get('name') as string) || undefined;
		const location = (data.get('location') as string) || undefined;
		const sportType = (data.get('sportType') as string) || undefined;
		await apiPatch(`/admin/clubs/${params.clubId}`, locals.adminToken!, { name, location, sportType });
		return { success: true };
	},

	deactivate: async ({ params, locals }) => {
		await apiPost(`/admin/clubs/${params.clubId}/deactivate`, locals.adminToken!);
		return { success: true, action: 'deactivated' };
	},

	reactivate: async ({ params, locals }) => {
		await apiPost(`/admin/clubs/${params.clubId}/reactivate`, locals.adminToken!);
		return { success: true, action: 'reactivated' };
	},

	delete: async ({ params, locals }) => {
		await apiDelete(`/admin/clubs/${params.clubId}`, locals.adminToken!);
		throw redirect(302, '/admin/clubs');
	},

	addManager: async ({ request, params, locals }) => {
		const data = await request.formData();
		const email = data.get('email') as string;
		if (!email) return fail(400, { error: 'Email required' });
		try {
			await apiPost(`/admin/clubs/${params.clubId}/managers`, locals.adminToken!, { email });
			return { success: true, action: 'manager_added' };
		} catch {
			return fail(404, { error: 'User not found' });
		}
	},

	removeManager: async ({ request, params, locals }) => {
		const data = await request.formData();
		const userId = data.get('userId') as string;
		await apiDelete(`/admin/clubs/${params.clubId}/managers/${userId}`, locals.adminToken!);
		return { success: true, action: 'manager_removed' };
	}
};
