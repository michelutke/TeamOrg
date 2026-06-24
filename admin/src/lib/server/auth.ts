import type { Cookies } from '@sveltejs/kit';

const API_BASE = process.env.API_URL || 'http://localhost:8080';
const COOKIE_NAME = 'admin_session';

interface AuthResponse {
	token: string;
	userId: string;
	displayName: string;
	avatarUrl: string | null;
}

interface UserInfo {
	id: string;
	email: string;
	displayName: string;
	isSuperAdmin: boolean;
	managedClubIds: string[];
}

interface ClubRoleEntry {
	clubId: string;
	role: string;
}

interface UserRolesResponse {
	clubRoles: ClubRoleEntry[];
	teamRoles: unknown[];
}

export async function login(
	email: string,
	password: string,
	cookies: Cookies
): Promise<{ success: boolean; error?: string }> {
	const res = await fetch(`${API_BASE}/auth/login`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ email, password })
	});

	if (!res.ok) {
		return { success: false, error: 'Invalid email or password' };
	}

	const data: AuthResponse = await res.json();

	// Verify user identity and fetch roles in parallel
	const [meRes, rolesRes] = await Promise.all([
		fetch(`${API_BASE}/auth/me`, {
			headers: { Authorization: `Bearer ${data.token}` }
		}),
		fetch(`${API_BASE}/auth/me/roles`, {
			headers: { Authorization: `Bearer ${data.token}` }
		})
	]);

	if (!meRes.ok) {
		return { success: false, error: 'Failed to verify user' };
	}

	const user: Omit<UserInfo, 'managedClubIds'> = await meRes.json();

	let managedClubIds: string[] = [];
	if (rolesRes.ok) {
		const roles: UserRolesResponse = await rolesRes.json();
		managedClubIds = roles.clubRoles
			.filter((r) => r.role === 'club_manager')
			.map((r) => r.clubId);
	}

	const isAllowed = user.isSuperAdmin || managedClubIds.length > 0;
	if (!isAllowed) {
		return { success: false, error: 'Only managers and admins can sign in here.' };
	}

	// Store JWT in httpOnly cookie — never exposed to browser JS
	cookies.set(COOKIE_NAME, data.token, {
		path: '/',
		httpOnly: true,
		secure: false, // true in production
		sameSite: 'lax',
		maxAge: 60 * 60 * 24 * 30 // 30 days
	});

	return { success: true };
}

export async function getSession(cookies: Cookies): Promise<UserInfo | null> {
	const token = cookies.get(COOKIE_NAME);
	if (!token) return null;

	try {
		const [meRes, rolesRes] = await Promise.all([
			fetch(`${API_BASE}/auth/me`, {
				headers: { Authorization: `Bearer ${token}` }
			}),
			fetch(`${API_BASE}/auth/me/roles`, {
				headers: { Authorization: `Bearer ${token}` }
			})
		]);

		if (!meRes.ok) return null;

		const user: Omit<UserInfo, 'managedClubIds'> = await meRes.json();

		let managedClubIds: string[] = [];
		if (rolesRes.ok) {
			const roles: UserRolesResponse = await rolesRes.json();
			managedClubIds = roles.clubRoles
				.filter((r) => r.role === 'club_manager')
				.map((r) => r.clubId);
		}

		return { ...user, managedClubIds };
	} catch {
		return null;
	}
}

export function getToken(cookies: Cookies): string | null {
	return cookies.get(COOKIE_NAME) || null;
}

export function logout(cookies: Cookies): void {
	cookies.delete(COOKIE_NAME, { path: '/' });
}
