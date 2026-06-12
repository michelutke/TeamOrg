import type { Cookies } from '@sveltejs/kit';

const API_BASE = process.env.API_URL || 'http://localhost:8080';
const IMPERSONATION_COOKIE = 'admin_impersonation';
const ORIGINAL_TOKEN_COOKIE = 'admin_session_original';

interface ImpersonationResponse {
	token: string;
	sessionId: string;
	targetUser: { id: string; email: string; displayName: string };
	expiresInSeconds: number;
}

interface ImpersonationState {
	active: boolean;
	targetName?: string;
	targetEmail?: string;
	sessionId?: string;
	expiresAt?: number; // Unix timestamp ms
	clubId?: string;
	clubName?: string;
}

export async function startImpersonation(
	targetUserId: string,
	cookies: Cookies,
	currentToken: string,
	club?: { id: string; name: string }
): Promise<{ success: boolean; error?: string }> {
	const res = await fetch(`${API_BASE}/admin/impersonate/start`, {
		method: 'POST',
		headers: {
			Authorization: `Bearer ${currentToken}`,
			'Content-Type': 'application/json'
		},
		body: JSON.stringify({ targetUserId })
	});

	if (!res.ok) {
		const text = await res.text();
		return { success: false, error: text };
	}

	const data: ImpersonationResponse = await res.json();

	// Store original token so we can restore after impersonation
	cookies.set(ORIGINAL_TOKEN_COOKIE, currentToken, {
		path: '/',
		httpOnly: true,
		secure: false,
		sameSite: 'lax',
		maxAge: 3600
	});

	// Store impersonation metadata
	const state: ImpersonationState = {
		active: true,
		targetName: data.targetUser.displayName,
		targetEmail: data.targetUser.email,
		sessionId: data.sessionId,
		expiresAt: Date.now() + data.expiresInSeconds * 1000,
		clubId: club?.id,
		clubName: club?.name
	};

	cookies.set(IMPERSONATION_COOKIE, JSON.stringify(state), {
		path: '/',
		httpOnly: true,
		secure: false,
		sameSite: 'lax',
		maxAge: 3600
	});

	// Replace the session cookie with impersonation token
	cookies.set('admin_session', data.token, {
		path: '/',
		httpOnly: true,
		secure: false,
		sameSite: 'lax',
		maxAge: 3600
	});

	return { success: true };
}

export async function endImpersonation(cookies: Cookies): Promise<void> {
	const currentToken = cookies.get('admin_session');

	// Call server to end impersonation
	if (currentToken) {
		try {
			await fetch(`${API_BASE}/admin/impersonate/end`, {
				method: 'POST',
				headers: { Authorization: `Bearer ${currentToken}` }
			});
		} catch {
			// Best effort
		}
	}

	// Restore original SA token
	const originalToken = cookies.get(ORIGINAL_TOKEN_COOKIE);
	if (originalToken) {
		cookies.set('admin_session', originalToken, {
			path: '/',
			httpOnly: true,
			secure: false,
			sameSite: 'lax',
			maxAge: 60 * 60 * 24 * 30
		});
	}

	// Clean up impersonation cookies
	cookies.delete(IMPERSONATION_COOKIE, { path: '/' });
	cookies.delete(ORIGINAL_TOKEN_COOKIE, { path: '/' });
}

export function getImpersonationState(cookies: Cookies): ImpersonationState {
	const raw = cookies.get(IMPERSONATION_COOKIE);
	if (!raw) return { active: false };

	try {
		const state: ImpersonationState = JSON.parse(raw);
		// Check if expired
		if (state.expiresAt && Date.now() > state.expiresAt) {
			return { active: false };
		}
		return state;
	} catch {
		return { active: false };
	}
}
