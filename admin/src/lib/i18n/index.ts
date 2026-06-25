// Lightweight, dependency-free i18n for the /app member surface.
// Mirrors the landing site's approach (landing/src/lib/i18n): locale resolved
// server-side from the `lang` cookie so SSR renders the right language; the shell
// toggle switches via ?lang=. No runtime dependency, fully type-checked via Dict.

export type Locale = 'de' | 'en';
export const locales: Locale[] = ['de', 'en'];
export const defaultLocale: Locale = 'de';

export function isLocale(v: unknown): v is Locale {
	return v === 'de' || v === 'en';
}

export interface Dict {
	nav: {
		start: string;
		termine: string;
		teams: string;
		inbox: string;
		profil: string;
		adminArea: string;
		logout: string;
	};
	login: {
		title: string;
		subtitle: string;
		email: string;
		password: string;
		submit: string;
		errRequired: string;
	};
	home: {
		greeting: string; // prefix before first name, e.g. "Hallo"
		teamsSub: string;
		emptyTitle: string;
		emptyBody: string;
		open: string;
	};
	roles: {
		coach: string;
		player: string;
		club_manager: string;
	};
	teams: {
		title: string;
		members: string;
	};
	roster: {
		title: string;
		coaches: string;
		players: string;
		empty: string;
	};
	common: {
		back: string;
		edit: string;
		save: string;
		cancel: string;
		create: string;
	};
	events: {
		title: string;
		none: string;
		when: string;
		where: string;
		description: string;
		allTeams: string;
	};
	eventTypes: {
		training: string;
		match: string;
		other: string;
	};
	rsvp: {
		yourResponse: string;
		confirmed: string;
		unsure: string;
		declined: string;
		noResponse: string;
		reason: string;
		reasonRequired: string;
		save: string;
		saved: string;
		responses: string;
		deadlinePassed: string;
	};
	eventForm: {
		newTitle: string;
		editTitle: string;
		fTeams: string;
		fTitle: string;
		fType: string;
		fStart: string;
		fEnd: string;
		fMeetup: string;
		fLocation: string;
		fDescription: string;
		fMinAttendees: string;
		selectTeams: string;
		required: string;
	};
	checkin: {
		title: string;
		present: string;
		absent: string;
		excused: string;
		empty: string;
	};
	member: {
		jersey: string;
		position: string;
		editOwn: string;
		saved: string;
	};
	inbox: {
		title: string;
		empty: string;
		markAllRead: string;
	};
	profile: {
		title: string;
		account: string;
		language: string;
		name: string;
		email: string;
	};
	invite: {
		eyebrow: string;
		teamTitle: string;
		clubTitle: string;
		teamLabel: string;
		roleLabel: string;
		invitedByLabel: string;
		expiresLabel: string;
		join: string;
		mismatchTitle: string;
		mismatchBody: string;
		signOut: string;
		haveAccount: string;
		toLogin: string;
		newHere: string;
		name: string;
		email: string;
		password: string;
		createAndJoin: string;
		invalidTitle: string;
		invalidBody: string;
		emailTaken: string;
		failed: string;
	};
}

const de: Dict = {
	nav: {
		start: 'Start',
		termine: 'Termine',
		teams: 'Teams',
		inbox: 'Inbox',
		profil: 'Profil',
		adminArea: 'Admin-Bereich',
		logout: 'Abmelden'
	},
	login: {
		title: 'TeamOrg',
		subtitle: 'Melde dich mit deinem Konto an',
		email: 'E-Mail',
		password: 'Passwort',
		submit: 'Anmelden',
		errRequired: 'E-Mail und Passwort erforderlich'
	},
	home: {
		greeting: 'Hallo',
		teamsSub: 'Deine Teams auf einen Blick',
		emptyTitle: 'Noch kein Team',
		emptyBody: 'Du gehörst noch keinem Team an. Löse eine Einladung ein, um loszulegen.',
		open: 'Öffnen'
	},
	roles: {
		coach: 'Trainer',
		player: 'Spieler',
		club_manager: 'Manager'
	},
	teams: {
		title: 'Teams',
		members: 'Mitglieder'
	},
	roster: {
		title: 'Kader',
		coaches: 'Trainer',
		players: 'Spieler',
		empty: 'Noch keine Mitglieder in diesem Team.'
	},
	common: {
		back: 'Zurück',
		edit: 'Bearbeiten',
		save: 'Speichern',
		cancel: 'Abbrechen',
		create: 'Erstellen'
	},
	events: {
		title: 'Termine',
		none: 'Keine anstehenden Termine.',
		when: 'Wann',
		where: 'Wo',
		description: 'Beschreibung',
		allTeams: 'Alle Teams'
	},
	eventTypes: {
		training: 'Training',
		match: 'Spiel',
		other: 'Anlass'
	},
	rsvp: {
		yourResponse: 'Deine Rückmeldung',
		confirmed: 'Zusagen',
		unsure: 'Unsicher',
		declined: 'Absage',
		noResponse: 'Keine Antwort',
		reason: 'Grund',
		reasonRequired: 'Bitte gib einen Grund an.',
		save: 'Speichern',
		saved: 'Gespeichert',
		responses: 'Rückmeldungen',
		deadlinePassed: 'Die Antwortfrist ist abgelaufen.'
	},
	eventForm: {
		newTitle: 'Neuer Termin',
		editTitle: 'Termin bearbeiten',
		fTeams: 'Teams',
		fTitle: 'Titel',
		fType: 'Art',
		fStart: 'Beginn',
		fEnd: 'Ende',
		fMeetup: 'Treffpunkt (optional)',
		fLocation: 'Ort',
		fDescription: 'Beschreibung',
		fMinAttendees: 'Mindestteilnehmer',
		selectTeams: 'Mindestens ein Team wählen',
		required: 'Pflichtfeld'
	},
	checkin: {
		title: 'Check-in',
		present: 'Anwesend',
		absent: 'Abwesend',
		excused: 'Entschuldigt',
		empty: 'Keine Mitglieder zum Einchecken.'
	},
	member: {
		jersey: 'Trikotnummer',
		position: 'Position',
		editOwn: 'Mein Profil bearbeiten',
		saved: 'Gespeichert'
	},
	inbox: {
		title: 'Inbox',
		empty: 'Keine Benachrichtigungen.',
		markAllRead: 'Alle gelesen'
	},
	profile: {
		title: 'Profil',
		account: 'Konto',
		language: 'Sprache',
		name: 'Name',
		email: 'E-Mail'
	},
	invite: {
		eyebrow: 'EINLADUNG',
		teamTitle: 'Du wurdest in ein Team eingeladen',
		clubTitle: 'Du wurdest in einen Verein eingeladen',
		teamLabel: 'Team',
		roleLabel: 'Rolle',
		invitedByLabel: 'Eingeladen von',
		expiresLabel: 'Gültig bis',
		join: 'Beitreten',
		mismatchTitle: 'Andere E-Mail-Adresse',
		mismatchBody:
			'Diese Einladung wurde an eine andere Adresse gesendet. Melde dich mit dem eingeladenen Konto an.',
		signOut: 'Abmelden',
		haveAccount: 'Ich habe bereits ein Konto',
		toLogin: 'Anmelden',
		newHere: 'Neu hier? Konto erstellen',
		name: 'Name',
		email: 'E-Mail',
		password: 'Passwort',
		createAndJoin: 'Konto erstellen & beitreten',
		invalidTitle: 'Einladung ungültig oder abgelaufen',
		invalidBody: 'Diese Einladung ist nicht mehr gültig. Bitte fordere beim Verein eine neue an.',
		emailTaken: 'Für diese E-Mail existiert bereits ein Konto. Bitte melde dich an.',
		failed: 'Beitritt fehlgeschlagen. Bitte versuche es erneut.'
	}
};

const en: Dict = {
	nav: {
		start: 'Home',
		termine: 'Events',
		teams: 'Teams',
		inbox: 'Inbox',
		profil: 'Profile',
		adminArea: 'Admin area',
		logout: 'Sign out'
	},
	login: {
		title: 'TeamOrg',
		subtitle: 'Sign in with your account',
		email: 'Email',
		password: 'Password',
		submit: 'Sign in',
		errRequired: 'Email and password required'
	},
	home: {
		greeting: 'Hi',
		teamsSub: 'Your teams at a glance',
		emptyTitle: 'No team yet',
		emptyBody: "You're not on any team yet. Redeem an invite to get started.",
		open: 'Open'
	},
	roles: {
		coach: 'Coach',
		player: 'Player',
		club_manager: 'Manager'
	},
	teams: {
		title: 'Teams',
		members: 'Members'
	},
	roster: {
		title: 'Roster',
		coaches: 'Coaches',
		players: 'Players',
		empty: 'No members in this team yet.'
	},
	common: {
		back: 'Back',
		edit: 'Edit',
		save: 'Save',
		cancel: 'Cancel',
		create: 'Create'
	},
	events: {
		title: 'Events',
		none: 'No upcoming events.',
		when: 'When',
		where: 'Where',
		description: 'Description',
		allTeams: 'All teams'
	},
	eventTypes: {
		training: 'Training',
		match: 'Match',
		other: 'Event'
	},
	rsvp: {
		yourResponse: 'Your response',
		confirmed: 'Going',
		unsure: 'Unsure',
		declined: 'Declined',
		noResponse: 'No response',
		reason: 'Reason',
		reasonRequired: 'Please provide a reason.',
		save: 'Save',
		saved: 'Saved',
		responses: 'Responses',
		deadlinePassed: 'The response deadline has passed.'
	},
	eventForm: {
		newTitle: 'New event',
		editTitle: 'Edit event',
		fTeams: 'Teams',
		fTitle: 'Title',
		fType: 'Type',
		fStart: 'Start',
		fEnd: 'End',
		fMeetup: 'Meetup (optional)',
		fLocation: 'Location',
		fDescription: 'Description',
		fMinAttendees: 'Min. attendees',
		selectTeams: 'Select at least one team',
		required: 'Required'
	},
	checkin: {
		title: 'Check-in',
		present: 'Present',
		absent: 'Absent',
		excused: 'Excused',
		empty: 'No members to check in.'
	},
	member: {
		jersey: 'Jersey number',
		position: 'Position',
		editOwn: 'Edit my profile',
		saved: 'Saved'
	},
	inbox: {
		title: 'Inbox',
		empty: 'No notifications.',
		markAllRead: 'Mark all read'
	},
	profile: {
		title: 'Profile',
		account: 'Account',
		language: 'Language',
		name: 'Name',
		email: 'Email'
	},
	invite: {
		eyebrow: 'INVITATION',
		teamTitle: 'You have been invited to a team',
		clubTitle: 'You have been invited to a club',
		teamLabel: 'Team',
		roleLabel: 'Role',
		invitedByLabel: 'Invited by',
		expiresLabel: 'Valid until',
		join: 'Join',
		mismatchTitle: 'Different email address',
		mismatchBody:
			'This invitation was sent to a different address. Please sign in with the invited account.',
		signOut: 'Sign out',
		haveAccount: 'I already have an account',
		toLogin: 'Sign in',
		newHere: 'New here? Create an account',
		name: 'Name',
		email: 'Email',
		password: 'Password',
		createAndJoin: 'Create account & join',
		invalidTitle: 'Invitation invalid or expired',
		invalidBody: 'This invitation is no longer valid. Please ask the club for a new one.',
		emailTaken: 'An account already exists for this email. Please sign in.',
		failed: 'Could not join. Please try again.'
	}
};

export const messages: Record<Locale, Dict> = { de, en };

export function getMessages(lang: Locale): Dict {
	return messages[lang] ?? messages[defaultLocale];
}

/** Resolves locale from a `lang` cookie value, defaulting to German. */
export function resolveLocale(cookieLang: string | undefined): Locale {
	return isLocale(cookieLang) ? cookieLang : defaultLocale;
}
