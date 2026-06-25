import { requireUser } from '$lib/server/guards';
import { apiGet, apiPost } from '$lib/server/api';
import type { Actions, PageServerLoad } from './$types';

interface Notification {
	id: string;
	type: string;
	title: string;
	body: string;
	entityId: string | null;
	entityType: string | null;
	isRead: boolean;
	createdAt: string;
}

export const load: PageServerLoad = async ({ locals }) => {
	requireUser(locals);
	const notifications = await apiGet<Notification[]>('/notifications', locals.token!);
	return { notifications };
};

export const actions: Actions = {
	read: async ({ request, locals }) => {
		requireUser(locals);
		const id = (await request.formData()).get('id') as string;
		await apiPost(`/notifications/${id}/read`, locals.token!);
		return { ok: true };
	},
	readAll: async ({ locals }) => {
		requireUser(locals);
		await apiPost('/notifications/read-all', locals.token!);
		return { ok: true };
	}
};
