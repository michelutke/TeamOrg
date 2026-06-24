import { env } from '$env/dynamic/private';
import { defaultLocale, getMessages, isLocale } from '$lib/i18n';
import type { PageServerLoad } from './$types';

export interface InviteDetails {
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

export const load: PageServerLoad = async ({ params, fetch, cookies }) => {
	const cookieLang = cookies.get('lang');
	const lang = isLocale(cookieLang) ? cookieLang : defaultLocale;
	const m = getMessages(lang);

	const apiUrl = (env.API_URL ?? 'http://localhost:8080').replace(/\/$/, '');

	try {
		const res = await fetch(`${apiUrl}/invites/${encodeURIComponent(params.token)}`, {
			signal: AbortSignal.timeout(10000)
		});
		// 404 (unknown) / 410 (inactive or expired) → friendly state, still a 200 page.
		if (res.status === 404 || res.status === 410) {
			return { lang, m, token: params.token, invite: null as InviteDetails | null };
		}
		if (!res.ok) {
			return { lang, m, token: params.token, invite: null as InviteDetails | null };
		}
		const raw = (await res.json()) as InviteDetails;
		// Never ship the invited email to the browser — it is not rendered on the web page
		// and would otherwise leak into the public HTML/hydration payload.
		const invite: InviteDetails = { ...raw, invitedEmail: null };
		return { lang, m, token: params.token, invite };
	} catch {
		return { lang, m, token: params.token, invite: null as InviteDetails | null };
	}
};
