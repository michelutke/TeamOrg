import { apiGet, apiPost, apiPatch, apiPut, apiDelete } from '$lib/server/api';
import { ApiError, assertClubAccess } from '$lib/server/guards';
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

interface SubGroup {
	id: string;
	teamId: string;
	name: string;
	memberCount: number;
}

export const load: PageServerLoad = async ({ params, locals }) => {
	const team = await apiGet<Team>(`/teams/${params.teamId}`, locals.token!);

	// Verify this team belongs to the club in the URL (backend also enforces)
	if (team.clubId !== params.clubId) {
		throw error(403, 'Team does not belong to this club');
	}

	const members = await apiGet<TeamMember[]>(`/teams/${params.teamId}/members`, locals.token!);
	// Subgroups are non-critical; don't 500 the page if the call fails.
	const subGroups = await apiGet<SubGroup[]>(`/teams/${params.teamId}/subgroups`, locals.token!).catch(
		() => [] as SubGroup[]
	);

	return { team, members, subGroups, clubId: params.clubId };
};

export const actions: Actions = {
	updateTeam: async ({ request, params, locals }) => {
		assertClubAccess(locals, params.clubId);
		const data = await request.formData();
		const name = (data.get('name') as string) || undefined;
		const description = (data.get('description') as string) || undefined;
		try {
			await apiPatch(`/teams/${params.teamId}`, locals.token!, { name, description });
			return { success: true };
		} catch (err) {
			if (err instanceof ApiError && err.status === 403)
				return fail(403, { error: 'Not authorized to update this team' });
			return fail(500, { error: 'Failed to update team' });
		}
	},

	archive: async ({ params, locals }) => {
		assertClubAccess(locals, params.clubId);
		try {
			await apiDelete(`/teams/${params.teamId}`, locals.token!);
		} catch (err) {
			if (err instanceof ApiError && err.status === 403)
				return fail(403, { error: 'Not authorized to archive this team' });
			return fail(500, { error: 'Failed to archive team' });
		}
		throw redirect(302, `/manage/${params.clubId}/teams`);
	},

	changeRole: async ({ request, params, locals }) => {
		assertClubAccess(locals, params.clubId);
		const data = await request.formData();
		const userId = data.get('userId') as string;
		const role = data.get('role') as string;
		try {
			await apiPatch(`/teams/${params.teamId}/members/${userId}/role`, locals.token!, { role });
			return { success: true, action: 'role_changed' };
		} catch (err) {
			if (err instanceof ApiError && err.status === 403)
				return fail(403, { error: 'Not authorized to change roles' });
			return fail(500, { error: 'Failed to change role' });
		}
	},

	removeMember: async ({ request, params, locals }) => {
		assertClubAccess(locals, params.clubId);
		const data = await request.formData();
		const userId = data.get('userId') as string;
		if (!userId) return fail(400, { error: 'User ID required' });
		try {
			await apiDelete(`/teams/${params.teamId}/members/${userId}`, locals.token!);
			return { success: true, action: 'member_removed' };
		} catch (err) {
			if (err instanceof ApiError && err.status === 403)
				return fail(403, { error: 'Not authorized to remove members' });
			return fail(500, { error: 'Failed to remove member' });
		}
	},

	createInvite: async ({ request, params, locals }) => {
		assertClubAccess(locals, params.clubId);
		const data = await request.formData();
		const role = (data.get('role') as string) || 'player';
		// Optional email → a personal, email-locked invite (only that address can join).
		// Empty → a shareable link anyone can use.
		const email = (data.get('email') as string)?.trim() || undefined;
		try {
			const invite = await apiPost<InviteResponse>(
				`/teams/${params.teamId}/invites`,
				locals.token!,
				email ? { role, email } : { role }
			);
			return {
				success: true,
				action: email ? 'invite_sent' : 'invite_created',
				inviteUrl: invite.inviteUrl,
				expiresAt: invite.expiresAt,
				email
			};
		} catch (err) {
			if (err instanceof ApiError && err.status === 403)
				return fail(403, { error: 'Not authorized to create invites' });
			if (err instanceof ApiError && err.status === 400)
				return fail(400, { error: 'Invalid email address' });
			return fail(500, { error: 'Failed to create invite' });
		}
	},

	createSubGroup: async ({ request, params, locals }) => {
		assertClubAccess(locals, params.clubId);
		const data = await request.formData();
		const name = (data.get('name') as string)?.trim();
		if (!name) return fail(400, { error: 'Subgroup name required' });
		try {
			await apiPost(`/teams/${params.teamId}/subgroups`, locals.token!, { name });
			return { success: true, action: 'subgroup_created' };
		} catch (err) {
			if (err instanceof ApiError && err.status === 403)
				return fail(403, { error: 'Not authorized to manage subgroups' });
			return fail(500, { error: 'Failed to create subgroup' });
		}
	},

	renameSubGroup: async ({ request, params, locals }) => {
		assertClubAccess(locals, params.clubId);
		const data = await request.formData();
		const subGroupId = data.get('subGroupId') as string;
		const name = (data.get('name') as string)?.trim();
		if (!subGroupId || !name) return fail(400, { error: 'Subgroup name required' });
		try {
			await apiPut(`/teams/${params.teamId}/subgroups/${subGroupId}`, locals.token!, { name });
			return { success: true, action: 'subgroup_renamed' };
		} catch (err) {
			if (err instanceof ApiError && err.status === 403)
				return fail(403, { error: 'Not authorized to manage subgroups' });
			return fail(500, { error: 'Failed to rename subgroup' });
		}
	},

	deleteSubGroup: async ({ request, params, locals }) => {
		assertClubAccess(locals, params.clubId);
		const data = await request.formData();
		const subGroupId = data.get('subGroupId') as string;
		if (!subGroupId) return fail(400, { error: 'Subgroup required' });
		try {
			await apiDelete(`/teams/${params.teamId}/subgroups/${subGroupId}`, locals.token!);
			return { success: true, action: 'subgroup_deleted' };
		} catch (err) {
			if (err instanceof ApiError && err.status === 403)
				return fail(403, { error: 'Not authorized to manage subgroups' });
			return fail(500, { error: 'Failed to delete subgroup' });
		}
	}
};
