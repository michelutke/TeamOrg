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

export function assertClubAccess(locals: App.Locals, clubId: string): void {
	if (!locals.user) throw redirect(302, '/admin/login');
	if (!locals.user.isSuperAdmin && !locals.user.managedClubIds.includes(clubId))
		throw error(403, 'You do not have access to this club');
}
