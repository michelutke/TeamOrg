import type { Cookies } from '@sveltejs/kit';

const API_BASE = process.env.API_URL || 'http://localhost:8080';

// Member-neutral session cookie. `admin_session` is the legacy name — read it as a
// fallback for one release (grace period) so existing sessions are not logged out,
// but only ever write `to_session`.
export const SESSION_COOKIE = 'to_session';
export const LEGACY_SESSION_COOKIE = 'admin_session';

const SESSION_MAX_AGE = 60 * 60 * 24 * 30; // 30 days

interface AuthResponse {
	token: string;
	userId: string;
	displayName: string;
	avatarUrl: string | null;
}

export interface TeamRole {
	teamId: string;
	clubId: string;
	role: string; // "coach" | "player"
}

export interface UserInfo {
	id: string;
	email: string;
	displayName: string;
	isSuperAdmin: boolean;
	managedClubIds: string[];
	teamRoles: TeamRole[];
}

interface ClubRoleEntry {
	clubId: string;
	role: string;
}

interface TeamRoleEntry {
	teamId: string;
	clubId: string;
	role: string;
}

interface UserRolesResponse {
	clubRoles: ClubRoleEntry[];
	teamRoles: TeamRoleEntry[];
}

/** Reads the session token, preferring the new cookie and falling back to the legacy name. */
function readToken(cookies: Cookies): string | null {
	return cookies.get(SESSION_COOKIE) ?? cookies.get(LEGACY_SESSION_COOKIE) ?? null;
}

function writeSessionCookie(cookies: Cookies, token: string): void {
	// JWT in httpOnly cookie — never exposed to browser JS.
	cookies.set(SESSION_COOKIE, token, {
		path: '/',
		httpOnly: true,
		secure: false, // true in production
		sameSite: 'lax',
		maxAge: SESSION_MAX_AGE
	});
}

/** Resolves a token to the full user info (identity + club + team roles). */
async function resolveUser(token: string): Promise<UserInfo | null> {
	const [meRes, rolesRes] = await Promise.all([
		fetch(`${API_BASE}/auth/me`, { headers: { Authorization: `Bearer ${token}` } }),
		fetch(`${API_BASE}/auth/me/roles`, { headers: { Authorization: `Bearer ${token}` } })
	]);

	if (!meRes.ok) return null;

	const me: Omit<UserInfo, 'managedClubIds' | 'teamRoles'> = await meRes.json();

	let managedClubIds: string[] = [];
	let teamRoles: TeamRole[] = [];
	if (rolesRes.ok) {
		const roles: UserRolesResponse = await rolesRes.json();
		managedClubIds = roles.clubRoles
			.filter((r) => r.role === 'club_manager')
			.map((r) => r.clubId);
		teamRoles = roles.teamRoles.map((r) => ({
			teamId: r.teamId,
			clubId: r.clubId,
			role: r.role
		}));
	}

	return { ...me, managedClubIds, teamRoles };
}

export async function login(
	email: string,
	password: string,
	cookies: Cookies
): Promise<{ success: boolean; error?: string; user?: UserInfo }> {
	const res = await fetch(`${API_BASE}/auth/login`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ email, password })
	});

	if (!res.ok) {
		return { success: false, error: 'Invalid email or password' };
	}

	const data: AuthResponse = await res.json();

	const user = await resolveUser(data.token);
	if (!user) {
		return { success: false, error: 'Failed to verify user' };
	}

	// Any authenticated user may sign in — super-admins, managers, coaches, players.
	// Landing redirect (and zero-role empty state) is decided by landingPathFor().
	writeSessionCookie(cookies, data.token);

	return { success: true, user };
}

/** Registers a new account, sets the session cookie, and returns the user. */
export async function register(
	email: string,
	password: string,
	displayName: string,
	cookies: Cookies
): Promise<{ success: boolean; error?: 'email_taken' | 'invalid'; user?: UserInfo; token?: string }> {
	const res = await fetch(`${API_BASE}/auth/register`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ email, password, displayName })
	});

	if (res.status === 409) return { success: false, error: 'email_taken' };
	if (!res.ok) return { success: false, error: 'invalid' };

	const data: AuthResponse = await res.json();
	const user = await resolveUser(data.token);
	if (!user) return { success: false, error: 'invalid' };

	writeSessionCookie(cookies, data.token);
	return { success: true, user, token: data.token };
}

export async function getSession(cookies: Cookies): Promise<UserInfo | null> {
	const token = readToken(cookies);
	if (!token) return null;
	try {
		return await resolveUser(token);
	} catch {
		return null;
	}
}

export function getToken(cookies: Cookies): string | null {
	return readToken(cookies);
}

export function logout(cookies: Cookies): void {
	cookies.delete(SESSION_COOKIE, { path: '/' });
	cookies.delete(LEGACY_SESSION_COOKIE, { path: '/' });
}

/** Role-based landing path for a freshly authenticated user.
 * Players/coaches (and dual-role users) land on the member dashboard; a pure
 * club manager lands on their club; super-admins on the admin dashboard. */
export function landingPathFor(user: UserInfo): string {
	if (user.isSuperAdmin) return '/admin/dashboard';
	if (user.teamRoles.length > 0) return '/app';
	if (user.managedClubIds.length > 0) return `/manage/${user.managedClubIds[0]}`;
	return '/app';
}
