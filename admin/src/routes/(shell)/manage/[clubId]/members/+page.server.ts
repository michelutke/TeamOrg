import { apiGet, apiPost, apiPatch, apiDelete } from '$lib/server/api';
import { assertClubAccess, ApiError } from '$lib/server/guards';
import { fail } from '@sveltejs/kit';
import type { PageServerLoad, Actions } from './$types';

export interface TeamRoleRef { teamId: string; teamName: string; role: string }
export interface ClubUser { userId: string; displayName: string; email: string; avatarUrl: string | null; teamRoles: TeamRoleRef[] }

const PAGE = 50;

export const load: PageServerLoad = async ({ params, locals }) => {
    assertClubAccess(locals, params.clubId);
    const [users, teams] = await Promise.all([
        apiGet<ClubUser[]>(`/clubs/${params.clubId}/users?limit=${PAGE}&offset=0`, locals.token!),
        apiGet<{ id: string; name: string }[]>(`/clubs/${params.clubId}/teams`, locals.token!)
    ]);
    return { users, teams, pageSize: PAGE };
};

export const actions: Actions = {
    addMember: async ({ request, params, locals }) => {
        assertClubAccess(locals, params.clubId);
        const f = await request.formData();
        const teamId = f.get('teamId') as string;
        const userId = f.get('userId') as string;
        const role = f.get('role') as string;
        try { await apiPost(`/teams/${teamId}/members`, locals.token!, { userId, role }); return { ok: 'added' }; }
        catch (e) { return fail(e instanceof ApiError ? e.status : 500, { error: 'addFailed' }); }
    },
    changeRole: async ({ request, params, locals }) => {
        assertClubAccess(locals, params.clubId);
        const f = await request.formData();
        const teamId = f.get('teamId') as string; const userId = f.get('userId') as string; const role = f.get('role') as string;
        try { await apiPatch(`/teams/${teamId}/members/${userId}/role`, locals.token!, { role }); return { ok: 'role' }; }
        catch (e) { return fail(e instanceof ApiError ? e.status : 500, { error: 'roleFailed' }); }
    },
    removeMember: async ({ request, params, locals }) => {
        assertClubAccess(locals, params.clubId);
        const f = await request.formData();
        const teamId = f.get('teamId') as string; const userId = f.get('userId') as string;
        try { await apiDelete(`/teams/${teamId}/members/${userId}`, locals.token!); return { ok: 'removed' }; }
        catch (e) { return fail(e instanceof ApiError ? e.status : 500, { error: 'removeFailed' }); }
    },
    inviteByEmail: async ({ request, params, locals }) => {
        assertClubAccess(locals, params.clubId);
        const f = await request.formData();
        const teamId = f.get('teamId') as string; const role = f.get('role') as string; const email = f.get('email') as string;
        try { await apiPost(`/teams/${teamId}/invites`, locals.token!, { role, email }); return { ok: 'invited' }; }
        catch (e) { return fail(e instanceof ApiError ? e.status : 500, { error: 'inviteFailed' }); }
    },
    linkNds: async ({ request, params, locals }) => {
        assertClubAccess(locals, params.clubId);
        const f = await request.formData();
        const teamId = f.get('teamId') as string; const memberId = f.get('memberId') as string; const userId = f.get('userId') as string;
        try { await apiPost(`/teams/${teamId}/nds/members/${memberId}/link`, locals.token!, { userId }); return { ok: 'linked' }; }
        catch (e) { return fail(e instanceof ApiError ? e.status : 500, { error: 'linkFailed' }); }
    }
};
