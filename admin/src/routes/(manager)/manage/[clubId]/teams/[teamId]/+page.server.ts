import { apiGet, apiPost, apiPatch, apiDelete } from '$lib/server/api';
import { fail, redirect, error } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';

interface Team {
	id: string;
	clubId: string;
	name: string;
	memberCount: number;
	description: string | null;
	archivedAt: string | null;
	createdAt: string;
}

interface TeamMember {
	userId: string;
	displayName: string;
	avatarUrl: string | null;
	role: string;
	jerseyNumber: number | null;
	position: string | null;
}

interface InviteResponse {
	token: string;
	inviteUrl: string;
	expiresAt: string;
}

export const load: PageServerLoad = async ({ params, locals }) => {
	const team = await apiGet<Team>(`/teams/${params.teamId}`, locals.token!);

	// Verify this team belongs to the club in the URL (backend also enforces)
	if (team.clubId !== params.clubId) {
		throw error(403, 'Team does not belong to this club');
	}

	const members = await apiGet<TeamMember[]>(`/teams/${params.teamId}/members`, locals.token!);

	return { team, members, clubId: params.clubId };
};

export const actions: Actions = {
	updateTeam: async ({ request, params, locals }) => {
		const data = await request.formData();
		const name = (data.get('name') as string) || undefined;
		const description = (data.get('description') as string) || undefined;
		await apiPatch(`/teams/${params.teamId}`, locals.token!, { name, description });
		return { success: true };
	},

	archive: async ({ params, locals }) => {
		await apiDelete(`/teams/${params.teamId}`, locals.token!);
		throw redirect(302, `/manage/${params.clubId}/teams`);
	},

	changeRole: async ({ request, params, locals }) => {
		const data = await request.formData();
		const userId = data.get('userId') as string;
		const role = data.get('role') as string;
		await apiPatch(`/teams/${params.teamId}/members/${userId}/role`, locals.token!, { role });
		return { success: true, action: 'role_changed' };
	},

	removeMember: async ({ request, params, locals }) => {
		const data = await request.formData();
		const userId = data.get('userId') as string;
		if (!userId) return fail(400, { error: 'User ID required' });
		await apiDelete(`/teams/${params.teamId}/members/${userId}`, locals.token!);
		return { success: true, action: 'member_removed' };
	},

	createInvite: async ({ request, params, locals }) => {
		const data = await request.formData();
		const role = (data.get('role') as string) || 'player';
		const invite = await apiPost<InviteResponse>(
			`/teams/${params.teamId}/invites`,
			locals.token!,
			{ role }
		);
		return { success: true, action: 'invite_created', inviteUrl: invite.inviteUrl, expiresAt: invite.expiresAt };
	}
};
