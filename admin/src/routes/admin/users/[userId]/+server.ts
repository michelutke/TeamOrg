import { apiGet } from '$lib/server/api';
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

export const GET: RequestHandler = async ({ params, locals }) => {
	const detail = await apiGet(`/admin/users/${params.userId}`, locals.adminToken!);
	return json(detail);
};
