import { apiGet } from './api';

type User = NonNullable<App.Locals['user']>;

export interface TeamAppearance {
	shape: string;
	color: string;
}

export interface Team {
	id: string;
	clubId: string;
	name: string;
	memberCount: number;
	description: string | null;
	appearance: TeamAppearance | null;
}

export interface TeamCard {
	id: string;
	name: string;
	memberCount: number;
	appearance: TeamAppearance | null;
	role: 'coach' | 'player' | 'club_manager';
	canManage: boolean;
}

/**
 * Resolves the user's teams (as coach/player, plus teams of clubs they manage)
 * into display cards with the caller's role + manage capability per team.
 */
export async function loadUserTeams(user: User, token: string): Promise<TeamCard[]> {
	const cards = new Map<string, TeamCard>();

	await Promise.all(
		user.teamRoles.map(async (tr) => {
			try {
				const team = await apiGet<Team>(`/teams/${tr.teamId}`, token);
				const canManage = tr.role === 'coach' || user.managedClubIds.includes(tr.clubId);
				cards.set(team.id, {
					id: team.id,
					name: team.name,
					memberCount: team.memberCount,
					appearance: team.appearance,
					role: tr.role === 'coach' ? 'coach' : 'player',
					canManage
				});
			} catch {
				// skip teams we cannot read
			}
		})
	);

	await Promise.all(
		user.managedClubIds.map(async (clubId) => {
			try {
				const teams = await apiGet<Team[]>(`/clubs/${clubId}/teams`, token);
				for (const team of teams) {
					if (!cards.has(team.id)) {
						cards.set(team.id, {
							id: team.id,
							name: team.name,
							memberCount: team.memberCount,
							appearance: team.appearance,
							role: 'club_manager',
							canManage: true
						});
					}
				}
			} catch {
				// skip clubs we cannot read
			}
		})
	);

	return [...cards.values()];
}
