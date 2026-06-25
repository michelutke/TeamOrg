// Lightweight, dependency-free i18n.
// Locale is resolved server-side from the `lang` cookie (see +layout.server.ts)
// so SSR renders the correct language; the nav toggle switches via ?lang=.

export type Locale = 'de' | 'en';
export const locales: Locale[] = ['de', 'en'];
export const defaultLocale: Locale = 'de';

export function isLocale(v: unknown): v is Locale {
	return v === 'de' || v === 'en';
}

export interface Feature {
	icon: 'calendar' | 'check' | 'calendar-x' | 'bell' | 'users' | 'bar-chart';
	title: string;
	body: string;
}
export interface Step {
	num: string;
	title: string;
	body: string;
}

export interface Dict {
	meta: { title: string; description: string };
	nav: { features: string; pricing: string; contact: string; cta: string; login: string };
	hero: {
		eyebrow: string;
		headlineA: string;
		headlineB: string;
		sub: string;
		ctaPrimary: string;
		ctaSecondary: string;
		trust: string[];
	};
	features: { eyebrow: string; title: string; sub: string; items: Feature[] };
	how: { eyebrow: string; title: string; sub: string; steps: Step[] };
	pricing: {
		eyebrow: string;
		title: string;
		sub: string;
		planLabel: string;
		price: string;
		per: string;
		fine: string;
		example: string;
		includes: string[];
		cta: string;
		footnote: string;
	};
	contact: {
		eyebrow: string;
		title: string;
		sub: string;
		infoEmail: string;
		infoReply: string;
		infoFor: string;
		formTitle: string;
		fields: {
			club: string;
			clubPh: string;
			name: string;
			namePh: string;
			email: string;
			emailPh: string;
			members: string;
			membersPh: string;
			message: string;
			messagePh: string;
		};
		consent: string;
		submit: string;
		sending: string;
		direct: string;
		successTitle: string;
		successBody: string;
		errorValidation: string;
		errorCaptcha: string;
		errorServer: string;
	};
	footer: {
		tagline: string;
		madeIn: string;
		colProduct: string;
		colLegal: string;
		colContact: string;
		links: { features: string; pricing: string; contact: string };
		legal: { privacy: string; imprint: string };
		rights: string;
	};
	legal: {
		back: string;
		imprintTitle: string;
		privacyTitle: string;
	};
	invite: {
		eyebrow: string;
		teamTitle: string;
		clubTitle: string;
		teamLabel: string;
		roleLabel: string;
		invitedByLabel: string;
		expiresLabel: string;
		roles: { player: string; coach: string; club_manager: string };
		openApp: string;
		joinWeb: string;
		download: string;
		iosSoon: string;
		hint: string;
		invalidTitle: string;
		invalidBody: string;
		backHome: string;
	};
}

const de: Dict = {
	meta: {
		title: 'teamorg: Trainings, Spiele und Anwesenheit für Vereine',
		description:
			'Die mobile App für Sportvereine: Trainings und Spiele planen, Anwesenheit in Echtzeit erfassen und das ganze Team auf dem Laufenden halten. 1 CHF pro Mitglied und Jahr.'
	},
	nav: {
		features: 'Funktionen',
		pricing: 'Preise',
		contact: 'Kontakt',
		cta: 'Demo anfragen',
		login: 'Anmelden'
	},
	hero: {
		eyebrow: 'Für Schweizer Sportvereine · J+S-tauglich',
		headlineA: 'Trainings, Spiele und Anwesenheit.',
		headlineB: 'Alles im Griff.',
		sub: 'teamorg ist die mobile App für Vereine: plane Trainings und Spiele, erfasse Zu- und Absagen in Echtzeit und halte dein ganzes Team auf dem Laufenden, online wie offline.',
		ctaPrimary: 'Demo anfragen',
		ctaSecondary: 'Funktionen ansehen',
		trust: ['1 CHF / Mitglied / Jahr', 'Offline-fähig', 'Keine Einrichtungskosten']
	},
	features: {
		eyebrow: 'FUNKTIONEN',
		title: 'Alles, was dein Verein braucht.',
		sub: 'Von der Trainingsplanung bis zur Anwesenheitsstatistik: teamorg bündelt alles in einer App, die Coaches und Spieler gerne nutzen.',
		items: [
			{
				icon: 'calendar',
				title: 'Termine & Wiederholungen',
				body: 'Plane Trainings, Spiele und Events: einmalig, wöchentlich oder nach eigenem Muster.'
			},
			{
				icon: 'check',
				title: 'Anwesenheit in Echtzeit',
				body: 'Zusagen, Absagen und Unsicher-Meldungen. Coaches sehen die Live-Statusliste sofort.'
			},
			{
				icon: 'calendar-x',
				title: 'Abwesenheiten im Voraus',
				body: 'Wiederkehrende Abwesenheiten, Ferien und Verletzungen: einmal erfassen, fertig.'
			},
			{
				icon: 'bell',
				title: 'Push-Benachrichtigungen',
				body: 'Automatische Erinnerungen sorgen dafür, dass niemand ein Training verpasst.'
			},
			{
				icon: 'users',
				title: 'Teams, Rollen & Gruppen',
				body: 'Coaches, Spieler und Clubmanager mit passenden Rechten und Untergruppen.'
			},
			{
				icon: 'bar-chart',
				title: 'Anwesenheitsstatistik',
				body: 'Quote pro Spieler, Training gegen Spiel: Zahlen, die bei der Saison helfen.'
			}
		]
	},
	how: {
		eyebrow: 'SO EINFACH',
		title: 'In drei Schritten startklar.',
		sub: 'Kein Setup-Aufwand, keine Schulung. Dein Verein ist in wenigen Minuten einsatzbereit.',
		steps: [
			{
				num: '01',
				title: 'Verein erstellen',
				body: 'Lege deinen Club an, richte Teams ein und definiere, wer Coach oder Spieler ist.'
			},
			{
				num: '02',
				title: 'Mitglieder einladen',
				body: 'Hol Coaches und Spieler in Sekunden per Einladungslink oder E-Mail an Bord.'
			},
			{
				num: '03',
				title: 'Loslegen',
				body: 'Plane Trainings und Spiele, erfasse Anwesenheiten und behalte jederzeit den Überblick.'
			}
		]
	},
	pricing: {
		eyebrow: 'PREISE',
		title: 'Ein Preis. Keine Überraschungen.',
		sub: 'Fair und planbar: du zahlst nur für aktive Mitglieder.',
		planLabel: 'PRO VEREIN',
		price: '1 CHF',
		per: '/ Mitglied / Jahr',
		fine: 'Jährliche Abrechnung. Mindestlaufzeit eine Saison. Du zahlst nur für aktive Mitglieder.',
		example: 'Beispiel: 40 Mitglieder = 40 CHF pro Jahr',
		includes: [
			'Unbegrenzte Teams, Events und Mitglieder',
			'Anwesenheit in Echtzeit & Statistiken',
			'Push-Benachrichtigungen & Erinnerungen',
			'Offline-Modus mit automatischer Synchronisation',
			'J+S-Anwesenheitskontrolle',
			'Persönlicher E-Mail-Support'
		],
		cta: 'Kontakt aufnehmen',
		footnote: 'Nur für Vereine und Organisationen, keine Einzelpersonen.'
	},
	contact: {
		eyebrow: 'KONTAKT',
		title: 'Bring deinen Verein auf teamorg.',
		sub: 'Erzähl uns kurz von deinem Verein. Wir melden uns mit allen Infos und richten den Start gemeinsam ein.',
		infoEmail: 'info@teamorg.ch',
		infoReply: 'Antwort innert 1 bis 2 Werktagen',
		infoFor: 'Für Vereine, Clubs & Verbände',
		formTitle: 'Anfrage senden',
		fields: {
			club: 'Vereinsname',
			clubPh: 'z. B. FC Wankdorf',
			name: 'Ansprechperson',
			namePh: 'Vor- und Nachname',
			email: 'E-Mail',
			emailPh: 'name@verein.ch',
			members: 'Anzahl Mitglieder (optional)',
			membersPh: 'z. B. 40',
			message: 'Nachricht',
			messagePh: 'Erzähl uns kurz, was du brauchst …'
		},
		consent:
			'Mit dem Absenden stimmst du der Verarbeitung deiner Angaben zur Kontaktaufnahme zu.',
		submit: 'Anfrage senden',
		sending: 'Wird gesendet …',
		direct: 'Oder direkt: info@teamorg.ch',
		successTitle: 'Danke für deine Anfrage!',
		successBody: 'Wir haben deine Nachricht erhalten und melden uns innert 1 bis 2 Werktagen.',
		errorValidation: 'Bitte fülle die Pflichtfelder korrekt aus.',
		errorCaptcha: 'Die Sicherheitsprüfung ist fehlgeschlagen. Bitte versuche es erneut.',
		errorServer: 'Senden fehlgeschlagen. Bitte versuche es später erneut oder schreib an info@teamorg.ch.'
	},
	footer: {
		tagline: 'Trainings, Spiele und Anwesenheit für Sportvereine.',
		madeIn: 'Made in Switzerland',
		colProduct: 'PRODUKT',
		colLegal: 'RECHTLICHES',
		colContact: 'KONTAKT',
		links: { features: 'Funktionen', pricing: 'Preise', contact: 'Kontakt' },
		legal: { privacy: 'Datenschutz', imprint: 'Impressum' },
		rights: '© 2026 teamorg. Alle Rechte vorbehalten.'
	},
	legal: {
		back: '← Zurück zur Startseite',
		imprintTitle: 'Impressum',
		privacyTitle: 'Datenschutzerklärung'
	},
	invite: {
		eyebrow: 'EINLADUNG',
		teamTitle: 'Du wurdest in ein Team eingeladen',
		clubTitle: 'Du wurdest in einen Verein eingeladen',
		teamLabel: 'Team',
		roleLabel: 'Rolle',
		invitedByLabel: 'Eingeladen von',
		expiresLabel: 'Gültig bis',
		roles: { player: 'Spieler', coach: 'Trainer', club_manager: 'Club-Manager' },
		openApp: 'App öffnen',
		joinWeb: 'Im Web beitreten',
		download: 'Android-App herunterladen',
		iosSoon: 'iOS-App folgt bald',
		hint: 'Öffne die Einladung in der teamorg-App, um beizutreten.',
		invalidTitle: 'Einladung ungültig oder abgelaufen',
		invalidBody:
			'Diese Einladung ist nicht mehr gültig. Bitte den Verein um eine neue Einladung.',
		backHome: '← Zurück zur Startseite'
	}
};

const en: Dict = {
	meta: {
		title: 'teamorg: training, matches and attendance for clubs',
		description:
			'The mobile app for sports clubs: plan trainings and matches, track attendance in real time and keep your whole team in the loop. 1 CHF per member per year.'
	},
	nav: {
		features: 'Features',
		pricing: 'Pricing',
		contact: 'Contact',
		cta: 'Request a demo',
		login: 'Log in'
	},
	hero: {
		eyebrow: 'For Swiss sports clubs · J+S-ready',
		headlineA: 'Training, matches and attendance.',
		headlineB: 'All under control.',
		sub: 'teamorg is the mobile app for clubs: plan trainings and matches, track confirmations and absences in real time, and keep your whole team in the loop, online and offline.',
		ctaPrimary: 'Request a demo',
		ctaSecondary: 'See features',
		trust: ['1 CHF / member / year', 'Works offline', 'No setup fees']
	},
	features: {
		eyebrow: 'FEATURES',
		title: 'Everything your club needs.',
		sub: 'From scheduling to attendance stats, teamorg brings it all into one app that coaches and players actually enjoy using.',
		items: [
			{
				icon: 'calendar',
				title: 'Events & recurrence',
				body: 'Schedule trainings, matches and events: one-off, weekly or on your own pattern.'
			},
			{
				icon: 'check',
				title: 'Real-time attendance',
				body: 'Confirmations, declines and maybes. Coaches see the live status list instantly.'
			},
			{
				icon: 'calendar-x',
				title: 'Absences in advance',
				body: 'Recurring absences, holidays and injuries: record once, done.'
			},
			{
				icon: 'bell',
				title: 'Push notifications',
				body: 'Automatic reminders make sure nobody misses a training.'
			},
			{
				icon: 'users',
				title: 'Teams, roles & groups',
				body: 'Coaches, players and club managers with the right permissions and sub-groups.'
			},
			{
				icon: 'bar-chart',
				title: 'Attendance stats',
				body: 'Rate per player, training vs. match: numbers that help across the season.'
			}
		]
	},
	how: {
		eyebrow: 'IT’S SIMPLE',
		title: 'Up and running in three steps.',
		sub: 'No setup hassle, no training needed. Your club is ready to go in minutes.',
		steps: [
			{
				num: '01',
				title: 'Create your club',
				body: 'Set up your club, create teams and define who is a coach or a player.'
			},
			{
				num: '02',
				title: 'Invite members',
				body: 'Bring coaches and players on board in seconds via invite link or email.'
			},
			{
				num: '03',
				title: 'Get going',
				body: 'Plan trainings and matches, track attendance and keep the overview at all times.'
			}
		]
	},
	pricing: {
		eyebrow: 'PRICING',
		title: 'One price. No surprises.',
		sub: 'Fair and predictable: you only pay for active members.',
		planLabel: 'PER CLUB',
		price: '1 CHF',
		per: '/ member / year',
		fine: 'Billed annually. Minimum term one season. You only pay for active members.',
		example: 'Example: 40 members = 40 CHF per year',
		includes: [
			'Unlimited teams, events and members',
			'Real-time attendance & statistics',
			'Push notifications & reminders',
			'Offline mode with automatic sync',
			'J+S attendance control',
			'Personal email support'
		],
		cta: 'Get in touch',
		footnote: 'For clubs and organisations only, not for individuals.'
	},
	contact: {
		eyebrow: 'CONTACT',
		title: 'Bring your club to teamorg.',
		sub: 'Tell us briefly about your club. We’ll get back to you with all the details and set up the start together.',
		infoEmail: 'info@teamorg.ch',
		infoReply: 'Reply within 1 to 2 business days',
		infoFor: 'For clubs, teams & associations',
		formTitle: 'Send a request',
		fields: {
			club: 'Club name',
			clubPh: 'e.g. FC Wankdorf',
			name: 'Contact person',
			namePh: 'First and last name',
			email: 'Email',
			emailPh: 'name@club.ch',
			members: 'Number of members (optional)',
			membersPh: 'e.g. 40',
			message: 'Message',
			messagePh: 'Tell us briefly what you need …'
		},
		consent: 'By submitting you agree to your details being processed so we can get in touch.',
		submit: 'Send request',
		sending: 'Sending …',
		direct: 'Or directly: info@teamorg.ch',
		successTitle: 'Thanks for your request!',
		successBody: 'We’ve received your message and will get back to you within 1 to 2 business days.',
		errorValidation: 'Please fill in the required fields correctly.',
		errorCaptcha: 'The security check failed. Please try again.',
		errorServer: 'Sending failed. Please try again later or email info@teamorg.ch.'
	},
	footer: {
		tagline: 'Training, matches and attendance for sports clubs.',
		madeIn: 'Made in Switzerland',
		colProduct: 'PRODUCT',
		colLegal: 'LEGAL',
		colContact: 'CONTACT',
		links: { features: 'Features', pricing: 'Pricing', contact: 'Contact' },
		legal: { privacy: 'Privacy', imprint: 'Imprint' },
		rights: '© 2026 teamorg. All rights reserved.'
	},
	legal: {
		back: '← Back to home',
		imprintTitle: 'Imprint',
		privacyTitle: 'Privacy Policy'
	},
	invite: {
		eyebrow: 'INVITATION',
		teamTitle: 'You have been invited to a team',
		clubTitle: 'You have been invited to a club',
		teamLabel: 'Team',
		roleLabel: 'Role',
		invitedByLabel: 'Invited by',
		expiresLabel: 'Valid until',
		roles: { player: 'Spieler', coach: 'Trainer', club_manager: 'Club-Manager' },
		openApp: 'Open app',
		joinWeb: 'Join on the web',
		download: 'Download Android app',
		iosSoon: 'iOS app coming soon',
		hint: 'Open the invitation in the teamorg app to join.',
		invalidTitle: 'Invitation invalid or expired',
		invalidBody: 'This invitation is no longer valid. Please ask the club for a new one.',
		backHome: '← Back to home'
	}
};

export const messages: Record<Locale, Dict> = { de, en };

export function getMessages(lang: Locale): Dict {
	return messages[lang] ?? messages[defaultLocale];
}
