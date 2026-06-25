import { redirect, error } from '@sveltejs/kit';

export class ApiError extends Error {
	constructor(
		public readonly status: number,
		message: string
	) {
		super(message);
		this.name = 'ApiError';
	}
}

type User = NonNullable<App.Locals['user']>;

// ── Role predicates (cosmetic + load-guard use; backend is the real authority) ──

export function isClubManager(user: User, clubId: string): boolean {
	return user.isSuperAdmin || user.managedClubIds.includes(clubId);
}

export function teamRolesFor(user: User, teamId: string): string[] {
	return user.teamRoles.filter((r) => r.teamId === teamId).map((r) => r.role);
}

export function isCoach(user: User, teamId: string): boolean {
	return teamRolesFor(user, teamId).includes('coach');
}

export function isTeamMember(user: User, teamId: string): boolean {
	return user.teamRoles.some((r) => r.teamId === teamId);
}

/** Can manage the team (events, roster, check-in): coach, the club's manager, or super-admin. */
export function canManageTeam(user: User, teamId: string, clubId?: string): boolean {
	if (isCoach(user, teamId)) return true;
	const cid = clubId ?? user.teamRoles.find((r) => r.teamId === teamId)?.clubId;
	return cid ? isClubManager(user, cid) : false;
}

// ── Throwing guards for page loads / actions (UX layer, not the security boundary) ──

export function requireUser(locals: App.Locals): User {
	if (!locals.user) throw redirect(302, '/login');
	return locals.user;
}

export function assertClubAccess(locals: App.Locals, clubId: string): void {
	const user = requireUser(locals);
	if (!isClubManager(user, clubId)) throw error(403, 'You do not have access to this club');
}

export function requireTeamMember(locals: App.Locals, teamId: string, clubId?: string): User {
	const user = requireUser(locals);
	if (user.isSuperAdmin || isTeamMember(user, teamId)) return user;
	if (clubId && isClubManager(user, clubId)) return user;
	throw error(403, 'You are not a member of this team');
}

export function requireTeamManage(locals: App.Locals, teamId: string, clubId?: string): User {
	const user = requireUser(locals);
	if (user.isSuperAdmin || canManageTeam(user, teamId, clubId)) return user;
	throw error(403, 'You cannot manage this team');
}
