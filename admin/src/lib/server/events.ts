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
	responseDeadline: string | null;
	status: string;
	cancelledAt: string | null;
	teamIds: string[];
	subgroupIds: string[];
	externalSource: string | null;
	externalStatus: string | null;
	needsReview: boolean;
	presentCount: number;
	checkInStatus: string;
	checkInCompletedAt: string | null;
	defaultResponse: string;
	seriesId: string | null;
	seriesSequence: number | null;
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
	unexcused: boolean;
}
