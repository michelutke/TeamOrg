import { json } from '@sveltejs/kit';
import { apiGet } from '$lib/server/api';
import { assertClubAccess } from '$lib/server/guards';
import type { RequestHandler } from './$types';

export const GET: RequestHandler = async ({ params, locals, url }) => {
    assertClubAccess(locals, params.clubId);
    const limit = url.searchParams.get('limit') ?? '50';
    const offset = url.searchParams.get('offset') ?? '0';
    const users = await apiGet(`/clubs/${params.clubId}/users?limit=${limit}&offset=${offset}`, locals.token!);
    return json(users);
};
