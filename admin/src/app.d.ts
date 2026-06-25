// See https://svelte.dev/docs/kit/types#app.d.ts

declare global {
	namespace App {
		interface Locals {
			user?: {
				id: string;
				email: string;
				displayName: string;
				isSuperAdmin: boolean;
				managedClubIds: string[];
				teamRoles: { teamId: string; clubId: string; role: string }[];
			};
			token?: string;
			adminToken?: string;
			impersonation?: {
				active: boolean;
				targetName?: string;
				targetEmail?: string;
				sessionId?: string;
				expiresAt?: number;
				clubId?: string;
				clubName?: string;
			};
		}
		// interface PageData {}
		// interface PageState {}
		// interface Platform {}
	}
}

export {};
