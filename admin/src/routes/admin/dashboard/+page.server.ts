import { apiGet } from '$lib/server/api';
import type { PageServerLoad } from './$types';

interface RecentSignup {
	userId: string;
	email: string;
	displayName: string;
	createdAt: string;
}

interface AdminStats {
	totalClubs: number;
	totalUsers: number;
	activeEventsToday: number;
	recentSignups: RecentSignup[];
}

export const load: PageServerLoad = async ({ locals }) => {
	const stats = await apiGet<AdminStats>('/admin/stats', locals.adminToken!);
	return { stats };
};
