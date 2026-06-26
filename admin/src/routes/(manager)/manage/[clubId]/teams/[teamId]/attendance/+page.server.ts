import { apiGet } from '$lib/server/api';
import { error } from '@sveltejs/kit';
import type { AppEvent } from '$lib/server/events';
import type { PageServerLoad } from './$types';

interface Team {
	id: string;
	clubId: string;
	name: string;
}

interface TeamMember {
	userId: string;
	displayName: string;
}

interface AttendanceRow {
	eventId: string;
	userId: string;
	status: string;
}

interface EventAttendance {
	id: string;
	title: string;
	startAt: string;
	status: string;
	confirmed: number;
	unsure: number;
	declined: number;
	noResponse: number;
}

export const load: PageServerLoad = async ({ params, locals }) => {
	const token = locals.token!;
	const team = await apiGet<Team>(`/teams/${params.teamId}`, token);
	if (team.clubId !== params.clubId) throw error(403, 'Team does not belong to this club');

	const [members, events, rows] = await Promise.all([
		apiGet<TeamMember[]>(`/teams/${params.teamId}/members`, token),
		apiGet<AppEvent[]>(`/teams/${params.teamId}/events`, token),
		apiGet<AttendanceRow[]>(`/teams/${params.teamId}/attendance`, token)
	]);

	// Group responses by event → status, then derive per-event counts.
	const byEvent = new Map<string, Map<string, string>>();
	for (const r of rows) {
		if (!byEvent.has(r.eventId)) byEvent.set(r.eventId, new Map());
		byEvent.get(r.eventId)!.set(r.userId, r.status);
	}

	const memberCount = members.length;
	const summary: EventAttendance[] = events
		.map((e) => {
			const responses = byEvent.get(e.id) ?? new Map<string, string>();
			let confirmed = 0;
			let unsure = 0;
			let declined = 0;
			for (const status of responses.values()) {
				if (status === 'confirmed') confirmed++;
				else if (status === 'unsure') unsure++;
				else if (status === 'declined' || status === 'declined-auto') declined++;
			}
			return {
				id: e.id,
				title: e.title,
				startAt: e.startAt,
				status: e.status,
				confirmed,
				unsure,
				declined,
				noResponse: Math.max(0, memberCount - confirmed - unsure - declined)
			};
		})
		.sort((a, b) => b.startAt.localeCompare(a.startAt));

	return { team, clubId: params.clubId, memberCount, summary };
};
