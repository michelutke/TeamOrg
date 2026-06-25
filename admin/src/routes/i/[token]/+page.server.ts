import { fail, redirect } from '@sveltejs/kit';
import { register } from '$lib/server/auth';
import { apiPost } from '$lib/server/api';
import { ApiError } from '$lib/server/guards';
import { getMessages, resolveLocale } from '$lib/i18n';
import type { Actions, PageServerLoad } from './$types';

const API_BASE = process.env.API_URL || 'http://localhost:8080';

interface InviteDetails {
	token: string;
	scope: 'team' | 'club';
	teamName: string | null;
	clubName: string;
	role: 'player' | 'coach' | 'club_manager';
	invitedBy: string;
	invitedEmail: string | null;
	reusable: boolean;
	expiresAt: string;
	alreadyRedeemed: boolean;
}

async function fetchInvite(token: string): Promise<InviteDetails | null> {
	try {
		const res = await fetch(`${API_BASE}/invites/${encodeURIComponent(token)}`, {
			signal: AbortSignal.timeout(10000)
		});
		if (!res.ok) return null; // 404 unknown / 410 inactive|expired
		return (await res.json()) as InviteDetails;
	} catch {
		return null;
	}
}

function emailMatches(invite: InviteDetails, userEmail: string): boolean {
	if (!invite.invitedEmail) return true; // reusable / no-email invite
	return invite.invitedEmail.trim().toLowerCase() === userEmail.trim().toLowerCase();
}

export const load: PageServerLoad = async ({ params, locals, cookies }) => {
	const lang = resolveLocale(cookies.get('lang'));
	const m = getMessages(lang);
	const invite = await fetchInvite(params.token);

	if (!invite) {
		return { lang, m, token: params.token, invite: null, state: 'invalid' as const };
	}

	if (locals.user) {
		const state = emailMatches(invite, locals.user.email) ? ('redeemable' as const) : ('mismatch' as const);
		return {
			lang,
			m,
			token: params.token,
			invite: { ...invite, invitedEmail: null },
			state
		};
	}

	// Unauthenticated: prefill (locked) email for personal invites so a new account
	// can't be created with a mismatching address. Reusable invites get an open field.
	return {
		lang,
		m,
		token: params.token,
		invite: { ...invite, invitedEmail: null },
		state: 'anonymous' as const,
		prefillEmail: invite.invitedEmail
	};
};

function redeemError(e: unknown, m: ReturnType<typeof getMessages>['invite']) {
	if (e instanceof ApiError) {
		if (e.status === 403) return fail(403, { error: m.mismatchBody });
		if (e.status === 410) return fail(410, { error: m.invalidBody });
		return fail(e.status, { error: m.failed });
	}
	throw e;
}

export const actions: Actions = {
	// Authenticated confirm → redeem with the current session.
	redeem: async ({ locals, params, cookies }) => {
		const m = getMessages(resolveLocale(cookies.get('lang'))).invite;
		if (!locals.token) throw redirect(302, `/login?redirectTo=/i/${params.token}`);
		try {
			await apiPost(`/invites/${params.token}/redeem`, locals.token);
		} catch (e) {
			return redeemError(e, m);
		}
		throw redirect(303, '/app');
	},

	// Invite-gated registration → auto-login → redeem. No public /register exists.
	register: async ({ request, params, cookies }) => {
		const m = getMessages(resolveLocale(cookies.get('lang'))).invite;
		const invite = await fetchInvite(params.token);
		if (!invite) return fail(410, { error: m.invalidBody });

		const form = await request.formData();
		const displayName = (form.get('name') as string)?.trim();
		const password = form.get('password') as string;
		// Personal invites: force the invited email (ignore any submitted value).
		const email = invite.invitedEmail ?? ((form.get('email') as string)?.trim() ?? '');

		if (!email || !displayName || !password) {
			return fail(400, { error: m.failed, name: displayName });
		}

		const reg = await register(email, password, displayName, cookies);
		if (!reg.success || !reg.token) {
			if (reg.error === 'email_taken') return fail(409, { error: m.emailTaken, name: displayName });
			return fail(400, { error: m.failed, name: displayName });
		}

		try {
			await apiPost(`/invites/${params.token}/redeem`, reg.token);
		} catch (e) {
			// Account is created + session set; surface the redeem failure but the user is signed in.
			return redeemError(e, m);
		}
		throw redirect(303, '/app');
	}
};
