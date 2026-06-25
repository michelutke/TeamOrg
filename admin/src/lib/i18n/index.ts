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
		back: 'Zurück'
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
		back: 'Back'
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
