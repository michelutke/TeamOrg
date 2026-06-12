import { apiGet } from '$lib/server/api';
import type { PageServerLoad } from './$types';

interface UserSearchResult {
	users: Array<{
		id: string;
		email: string;
		displayName: string;
		clubs: string[];
		roles: string[];
		joinedAt: string;
	}>;
	page: number;
	pageSize: number;
	totalCount: number;
}

export const load: PageServerLoad = async ({ locals, url }) => {
	const query = url.searchParams.get('q') || '';
	const page = parseInt(url.searchParams.get('page') || '1');

	// Only search if query has at least 2 chars (per UI spec)
	if (query.length >= 2) {
		const users = await apiGet<UserSearchResult>(
			`/admin/users?q=${encodeURIComponent(query)}&page=${page}&pageSize=50`,
			locals.adminToken!
		);
		return { users, query, page };
	}

	return {
		users: { users: [], page: 1, pageSize: 50, totalCount: 0 },
		query,
		page
	};
};
