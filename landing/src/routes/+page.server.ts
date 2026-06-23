import { fail } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';
import type { Actions } from './$types';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

async function verifyTurnstile(token: string, ip: string | null): Promise<boolean> {
	const secret = env.TURNSTILE_SECRET;
	// No secret configured (e.g. local dev) → skip verification.
	if (!secret) return true;
	if (!token) return false;
	try {
		const body = new URLSearchParams({ secret, response: token });
		if (ip) body.set('remoteip', ip);
		const res = await fetch('https://challenges.cloudflare.com/turnstile/v0/siteverify', {
			method: 'POST',
			body
		});
		const data = (await res.json()) as { success?: boolean };
		return data.success === true;
	} catch {
		return false;
	}
}

export const actions: Actions = {
	contact: async ({ request, fetch, getClientAddress }) => {
		const data = await request.formData();
		const honeypot = String(data.get('company') ?? '');
		const club = String(data.get('club') ?? '').trim();
		const name = String(data.get('name') ?? '').trim();
		const email = String(data.get('email') ?? '').trim();
		const members = String(data.get('members') ?? '').trim();
		const message = String(data.get('message') ?? '').trim();
		const token = String(data.get('cf-turnstile-response') ?? '');

		const values = { club, name, email, members, message };

		// Bot honeypot — silently accept so the bot thinks it succeeded.
		if (honeypot) return { success: true };

		if (!club || !name || !message || !EMAIL_RE.test(email)) {
			return fail(400, { error: 'validation', values });
		}

		const captchaOk = await verifyTurnstile(token, getClientAddress());
		if (!captchaOk) {
			return fail(400, { error: 'captcha', values });
		}

		const apiUrl = env.API_URL ?? 'http://localhost:8080';
		try {
			const res = await fetch(`${apiUrl.replace(/\/$/, '')}/contact`, {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
					...(env.CONTACT_SHARED_SECRET ? { 'X-Contact-Secret': env.CONTACT_SHARED_SECRET } : {})
				},
				body: JSON.stringify({ club, name, email, members, message })
			});
			if (!res.ok) {
				return fail(502, { error: 'server', values });
			}
		} catch {
			return fail(502, { error: 'server', values });
		}

		return { success: true };
	}
};
