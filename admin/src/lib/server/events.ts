export interface MatchedTeam {
	id: string;
	name: string;
}

export interface AppEvent {
	id: string;
	title: string;
	type: string;
	startAt: string;
	endAt: string;
	meetupAt: string | null;
	location: string | null;
	description: string | null;
	minAttendees: number | null;
	status: string;
	cancelledAt: string | null;
	teamIds: string[];
	subgroupIds: string[];
	externalSource: string | null;
	externalStatus: string | null;
	needsReview: boolean;
	presentCount: number;
}

export interface EventWithTeams {
	event: AppEvent;
	matchedTeams: MatchedTeam[];
}

export interface AttendanceResponse {
	eventId: string;
	userId: string;
	status: string;
	reason: string | null;
	manualOverride: boolean;
}
