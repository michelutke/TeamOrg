import { error, fail } from '@sveltejs/kit';
import { requireUser, isCoach, isClubManager, ApiError } from '$lib/server/guards';
import { apiGet, apiPatch, apiPost } from '$lib/server/api';
import type { Team } from '$lib/server/teams';
import type { Actions, PageServerLoad } from './$types';

interface Member {
	userId: string;
	displayName: string;
	avatarUrl: string | null;
	role: string;
	jerseyNumber: number | null;
	position: string | null;
}

interface NdsMember {
	id: string;
	userId: string | null;
	lastName: string;
	firstName: string;
	birthDate: string | null;
	personNumber: string | null;
	funktion: string;
	claimed: boolean;
}

interface NdsPreflightIssue {
	severity: string;
	code: string;
	message: string;
}
interface NdsPreflightReport {
	ok: boolean;
	issues: NdsPreflightIssue[];
}

export const load: PageServerLoad = async ({ locals, params }) => {
	const user = requireUser(locals);
	const token = locals.token!;
	const { teamId } = params;

	try {
		const [team, members] = await Promise.all([
			apiGet<Team>(`/teams/${teamId}`, token),
			apiGet<Member[]>(`/teams/${teamId}/members`, token)
		]);

		const canManage = isCoach(user, teamId) || isClubManager(user, team.clubId);

		// NDS roster + export pre-flight (only relevant to managers; tolerate absence).
		let ndsMembers: NdsMember[] = [];
		let ndsPreflight: NdsPreflightReport | null = null;
		if (canManage) {
			try {
				ndsMembers = await apiGet<NdsMember[]>(`/teams/${teamId}/nds/members`, token);
				if (ndsMembers.length > 0) {
					ndsPreflight = await apiGet<NdsPreflightReport>(
						`/teams/${teamId}/nds/export/preflight`,
						token
					);
				}
			} catch {
				ndsMembers = [];
			}
		}

		return { team, members, canManage, ndsMembers, ndsPreflight };
	} catch (e) {
		if (e instanceof ApiError) throw error(e.status === 403 ? 403 : e.status, 'Kein Zugriff');
		throw e;
	}
};

export const actions: Actions = {
	setNdsPersonNumber: async ({ request, params, locals }) => {
		const data = await request.formData();
		const memberId = data.get('memberId') as string;
		const personNumber = (data.get('personNumber') as string)?.trim();
		if (!memberId) return fail(400, { ndsError: 'invalid' });
		try {
			await apiPatch(`/teams/${params.teamId}/nds/members/${memberId}`, locals.token!, {
				personNumber
			});
			return { ndsSaved: true };
		} catch (e) {
			if (e instanceof ApiError && e.status === 400) return fail(400, { ndsError: 'badNumber' });
			return fail(500, { ndsError: 'failed' });
		}
	},

	inviteNdsMember: async ({ request, params, locals }) => {
		const data = await request.formData();
		const memberId = data.get('memberId') as string;
		const email = ((data.get('email') as string) || '').trim() || undefined;
		if (!memberId) return fail(400, { ndsError: 'invalid' });
		try {
			const res = await apiPost<{ token: string; inviteUrl: string; expiresAt: string }>(
				`/teams/${params.teamId}/nds/members/${memberId}/invite`,
				locals.token!,
				{ email }
			);
			return { ndsInvite: res };
		} catch {
			return fail(500, { ndsError: 'failed' });
		}
	}
};
