import { dev } from '$app/environment';
import { env } from '$env/dynamic/public';
import { getSession, getToken } from '$lib/server/auth';
import {
	getImpersonationState,
	endImpersonation,
	ORIGINAL_TOKEN_COOKIE
} from '$lib/server/impersonation';
import type { Handle } from '@sveltejs/kit';

export const handle: Handle = async ({ event, resolve }) => {
	const user = await getSession(event.cookies);
	event.locals.user = user ?? undefined;
	event.locals.token = getToken(event.cookies) ?? undefined;

	const impersonation = getImpersonationState(event.cookies);
	if (impersonation.active && impersonation.expiresAt && Date.now() > impersonation.expiresAt) {
		await endImpersonation(event.cookies);
		// Re-get session with original token
		const refreshedUser = await getSession(event.cookies);
		event.locals.user = refreshedUser ?? undefined;
		event.locals.token = getToken(event.cookies) ?? undefined;
		event.locals.adminToken = event.locals.token;
		event.locals.impersonation = undefined;
	} else if (impersonation.active) {
		// During impersonation, admin API calls need the original SA token
		const originalToken = event.cookies.get(ORIGINAL_TOKEN_COOKIE) ?? undefined;
		event.locals.adminToken = originalToken ?? event.locals.token;
		event.locals.impersonation = impersonation;
	} else {
		event.locals.adminToken = event.locals.token;
		event.locals.impersonation = undefined;
	}

	const response = await resolve(event);
	applySecurityHeaders(response.headers);
	return response;
};

/**
 * Baseline response headers for every page.
 *
 * The CSP allows Stripe (Payment Element runs in a js.stripe.com iframe) and the server's
 * `/uploads` origin for club logos and avatars; everything else is same-origin. `unsafe-inline`
 * for styles is required by Svelte's scoped-style output — scripts do not get it.
 */
function applySecurityHeaders(headers: Headers): void {
	// Same source of truth as `$lib/urls` — uploads are served from the API origin.
	const serverOrigin = (env.PUBLIC_SERVER_URL || 'https://server.teamorg.ch').replace(/\/$/, '');

	headers.set('X-Content-Type-Options', 'nosniff');
	headers.set('X-Frame-Options', 'DENY');
	headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');
	headers.set('Permissions-Policy', 'geolocation=(), microphone=(), camera=()');
	if (!dev) {
		headers.set('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
	}
	// The Content-Security-Policy itself is configured in svelte.config.js: SvelteKit
	// emits inline scripts for hydration payloads, and only it knows their hashes.
	// `connect-src` has to cover the API origin, so it is appended here where the
	// runtime value of PUBLIC_SERVER_URL is known.
	const csp = headers.get('Content-Security-Policy');
	if (csp && serverOrigin && !csp.includes(serverOrigin)) {
		headers.set(
			'Content-Security-Policy',
			csp
				.replace('connect-src', `connect-src ${serverOrigin}`)
				.replace('img-src', `img-src ${serverOrigin}`)
		);
	}
}
