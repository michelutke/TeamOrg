import { apiGet } from '$lib/server/api';
import { assertClubAccess } from '$lib/server/guards';
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
