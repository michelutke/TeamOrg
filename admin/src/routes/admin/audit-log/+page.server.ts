import { apiGet } from '$lib/server/api';
import type { PageServerLoad } from './$types';

interface AuditLogEntry {
	id: string;
	timestamp: string;
	actorId: string;
	actorEmail: string;
	action: string;
	targetType: string | null;
	targetId: string | null;
	details: Record<string, unknown> | null;
	impersonationContext: { impersonatorEmail: string; impersonatorId: string } | null;
}

interface AuditLogResponse {
	entries: AuditLogEntry[];
	page: number;
	pageSize: number;
	totalCount: number;
}

export const load: PageServerLoad = async ({ locals, url }) => {
	const action = url.searchParams.get('action') || undefined;
	const actor = url.searchParams.get('actor') || undefined;
	const startDate = url.searchParams.get('startDate') || undefined;
	const endDate = url.searchParams.get('endDate') || undefined;
	const page = parseInt(url.searchParams.get('page') || '1');

	const params = new URLSearchParams();
	params.set('page', String(page));
	params.set('pageSize', '50');
	if (action) params.set('action', action);
	if (actor) params.set('actor', actor);
	if (startDate) params.set('startDate', startDate);
	if (endDate) params.set('endDate', endDate);

	const log = await apiGet<AuditLogResponse>(
		`/admin/audit-log?${params.toString()}`,
		locals.adminToken!
	);
	return { log, filters: { action, actor, startDate, endDate }, page };
};
