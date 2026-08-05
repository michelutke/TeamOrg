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
		manageArea: string;
		club: string;
		billing: string;
		memberSection: string;
		logout: string;
	};
	login: {
		title: string;
		subtitle: string;
		email: string;
		password: string;
		submit: string;
		errRequired: string;
		newHere: string;
		startCta: string;
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
		cancelEvent: string;
		uncancelEvent: string;
		duplicate: string;
		cancelledBadge: string;
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
		minAttendeesWarning: string;
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
		fResponseDeadline: string;
		selectTeams: string;
		required: string;
		recurringSection: string;
		recurringEnable: string;
		fPattern: string;
		patternWeekly: string;
		patternDaily: string;
		patternCustom: string;
		fDefaultResponse: string;
		defaultResponseNone: string;
		defaultResponseAccepted: string;
		defaultResponseDeclined: string;
		fWeekdays: string;
		fIntervalDays: string;
		fSeriesEnd: string;
		weekdayMon: string;
		weekdayTue: string;
		weekdayWed: string;
		weekdayThu: string;
		weekdayFri: string;
		weekdaySat: string;
		weekdaySun: string;
	};
	schedule: {
		carryOverTitle: string;
		carryOverBody: string;
		carryOverAction: string;
		pickSeries: string;
		loading: string;
		loadFailed: string;
		empty: string;
		takeOver: string;
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
		changePasswordTitle: string;
		currentPasswordLabel: string;
		newPasswordLabel: string;
		confirmPasswordLabel: string;
		changePasswordButton: string;
		passwordChanged: string;
		passwordMismatch: string;
		passwordTooShort: string;
		passwordWrongCurrent: string;
		passwordChangeFailed: string;
		deleteSectionTitle: string;
		deleteSectionBody: string;
		deleteSectionLink: string;
		deleteTitle: string;
		deleteIntro: string;
		deleteRemovedTitle: string;
		deleteRemovedBody: string;
		deleteKeptTitle: string;
		deleteKeptBody: string;
		deleteCoachWarning: string;
		deleteIrreversible: string;
		deletePasswordLabel: string;
		deleteButton: string;
		deleteCancel: string;
		deleteWrongPassword: string;
		deleteOwnsClubs: string;
		deleteFailed: string;
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
		alreadyRedeemedTitle: string;
		alreadyRedeemedBody: string;
	};
	swissvolley: {
		importButton: string;
		title: string;
		subtitle: string;
		loading: string;
		noTeams: string;
		noKeyTitle: string;
		noKeyBody: string;
		loadFailed: string;
		selectAll: string;
		clearAll: string;
		genderM: string;
		genderF: string;
		genderMixed: string;
		import: string;
		importing: string;
		cancel: string;
		selectAtLeastOne: string;
		importFailed: string;
		importNoKey: string;
		resultCreated: string;
		resultSkipped: string;
		resultNone: string;
		postponed: string;
		sourceLabel: string;
	};
	reconcile: {
		needsReviewTitle: string;
		needsReviewBody: string;
		meetupAt: string;
		notes: string;
		minAttendees: string;
		availabilityTitle: string;
		keepAvailability: string;
		resetAvailability: string;
		submit: string;
		saving: string;
		reconciled: string;
		failed: string;
	};
	teamMigrate: {
		deprecatedBadge: string;
		action: string;
		title: string;
		subtitle: string;
		targetLabel: string;
		selectPlaceholder: string;
		noTargets: string;
		submit: string;
		submitting: string;
		cancel: string;
		selectTarget: string;
		resultMoved: string; // "{n} Mitglieder verschoben" — {n} replaced at call site
		errSourceNotDeprecated: string;
		errTargetNotLive: string;
		errFailed: string;
	};
	manage: {
		editClubTitle: string;
		locationLabel: string;
		logoTitle: string;
		logoHint: string;
		logoUpdated: string;
		uploadLogo: string;
		coManagersTitle: string;
		coManagersBody: string;
		inviteLinkGenerated: string;
		expiresLabel: string;
		inviteCoManager: string;
		emailPlaceholderColleague: string;
		sendInvite: string;
		teamsQuickLinkBody: string;
		viewTeamsLink: string;
		membersQuickLinkBody: string;
		manageMembersTitle: string;
		ndsImport: string;
		newTeam: string;
		teamNamePlaceholder: string;
		optionalPlaceholder: string;
		noTeamsYet: string;
		viewLink: string;
		backToTeams: string;
		attendance: string;
		archive: string;
		inviteMembersTitle: string;
		inviteMembersBody: string;
		inviteOnlyEmailPrefix: string;
		inviteOnlyEmailSuffix: string;
		inviteEmailedToPrefix: string;
		shareableInviteGenerated: string;
		inviteMember: string;
		emailOptionalLabel: string;
		emailPlaceholderPerson: string;
		createInvite: string;
		subgroupsTitle: string;
		subgroupsBody: string;
		memberSingular: string;
		rename: string;
		delete: string;
		addSubgroup: string;
		subgroupNameLabel: string;
		subgroupNamePlaceholder: string;
		subgroupSelectMemberPlaceholder: string;
		subgroupAddMemberLabel: string;
		noMembersYet: string;
		actionsLabel: string;
		makeCoach: string;
		makePlayer: string;
		remove: string;
		archiveTeamTitle: string;
		archiveConfirmPrefix: string;
		archiveConfirmSuffix: string;
		removeMemberTitle: string;
		removeConfirmPrefix: string;
		removeConfirmMiddle: string;
		removeConfirmSuffix: string;
		deleteSubgroupTitle: string;
		deleteSubgroupConfirmPrefix: string;
		deleteSubgroupConfirmSuffix: string;
		membersPageTitle: string;
		inviteByEmailTitle: string;
		teamLabel: string;
		inviteButton: string;
		noTeamsForInvite: string;
		filterPlaceholder: string;
		noMatch: string;
		noMembersFound: string;
		addTeamButton: string;
		loadingShort: string;
		loadMore: string;
		shownCountMiddle: string;
		shownCountSuffix: string;
		archivedTeamsTitle: string;
		unarchive: string;
	};
	onboarding: {
		welcomeTitle: string;
		welcomeSubtitle: string;
		joinCta: string;
		createCta: string;
		loginCta: string;
		joinTitle: string;
		joinCodeLabel: string;
		joinCodeInvalid: string;
		joinSubmit: string;
		createAccountTitle: string;
		createDetailsTitle: string;
		kindTeam: string;
		kindClub: string;
		kindTeamHint: string;
		kindClubHint: string;
		kindSwitchNote: string;
		nameLabel: string;
		sportLabel: string;
		locationLabel: string;
		billingEmailLabel: string;
		pricingNote: string;
		cardTitle: string;
		cardSubmit: string;
		cardProcessing: string;
		cardError: string;
		done: string;
		emailLabel: string;
		passwordLabel: string;
		registerSubmit: string;
		haveAccount: string;
		toLogin: string;
		nameRequired: string;
		billingEmailInvalid: string;
		detailsSubmit: string;
		registerFailed: string;
		emailTaken: string;
	};
	billing: {
		title: string;
		cardOnFile: string;
		noCard: string;
		memberCount: string;
		projectedCount: string;
		countBasisNote: string;
		manualNote: string;
		updateCard: string;
		cardSubmit: string;
		cardProcessing: string;
		cardUpdateError: string;
		currentKind: string;
		convertToClub: string;
		convertToTeam: string;
		convertBlocked: string;
		convertedToClubNote: string;
		statusActive: string;
		statusPastDue: string;
		statusFrozen: string;
		frozenBanner: string;
		frozenBannerCta: string;
		pastDueBanner: string;
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
		manageArea: 'Verein',
		club: 'Club',
		billing: 'Abrechnung',
		memberSection: 'Meine Teilnahme',
		logout: 'Abmelden'
	},
	login: {
		title: 'TeamOrg',
		subtitle: 'Melde dich mit deinem Konto an',
		email: 'E-Mail',
		password: 'Passwort',
		submit: 'Anmelden',
		errRequired: 'E-Mail und Passwort erforderlich',
		newHere: 'Neu hier?',
		startCta: 'Jetzt starten'
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
		allTeams: 'Alle Teams',
		cancelEvent: 'Absagen',
		uncancelEvent: 'Absage aufheben',
		duplicate: 'Duplizieren',
		cancelledBadge: 'Abgesagt'
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
		deadlinePassed: 'Die Antwortfrist ist abgelaufen.',
		minAttendeesWarning: 'Mindestteilnehmer nicht erreicht'
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
		fResponseDeadline: 'Anmeldeschluss (optional)',
		selectTeams: 'Mindestens ein Team wählen',
		required: 'Pflichtfeld',
		recurringSection: 'Wiederholung',
		recurringEnable: 'Als Serie wiederholen',
		fPattern: 'Muster',
		patternWeekly: 'Wöchentlich',
		patternDaily: 'Täglich',
		patternCustom: 'Benutzerdefiniert',
		fDefaultResponse: 'Standard-Rückmeldung',
		defaultResponseNone: 'Keine Vorgabe – Trainer muss auflösen',
		defaultResponseAccepted: 'Standard: Anwesend',
		defaultResponseDeclined: 'Standard: Abgemeldet',
		fWeekdays: 'Wochentage',
		fIntervalDays: 'Intervall (Tage)',
		fSeriesEnd: 'Serienende',
		weekdayMon: 'Mo',
		weekdayTue: 'Di',
		weekdayWed: 'Mi',
		weekdayThu: 'Do',
		weekdayFri: 'Fr',
		weekdaySat: 'Sa',
		weekdaySun: 'So'
	},
	schedule: {
		carryOverTitle: 'Vorherigen Trainingsplan übernehmen',
		carryOverBody:
			'Das Vorgängerteam hatte einen wiederkehrenden Trainingsplan. Übernimm einzelne Serien und passe Daten und Felder für die neue Saison an.',
		carryOverAction: 'Plan übernehmen',
		pickSeries: 'Serie zum Übernehmen wählen',
		loading: 'Wird geladen…',
		loadFailed: 'Serien konnten nicht geladen werden.',
		empty: 'Keine übernehmbaren Serien gefunden.',
		takeOver: 'Übernehmen'
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
		email: 'E-Mail',
		changePasswordTitle: 'Passwort ändern',
		currentPasswordLabel: 'Aktuelles Passwort',
		newPasswordLabel: 'Neues Passwort',
		confirmPasswordLabel: 'Neues Passwort bestätigen',
		changePasswordButton: 'Passwort ändern',
		passwordChanged: 'Passwort geändert.',
		passwordMismatch: 'Die Passwörter stimmen nicht überein.',
		passwordTooShort: 'Das Passwort muss mindestens 8 Zeichen lang sein.',
		passwordWrongCurrent: 'Das aktuelle Passwort ist falsch.',
		passwordChangeFailed: 'Passwort konnte nicht geändert werden. Bitte versuche es erneut.',
		deleteSectionTitle: 'Konto löschen',
		deleteSectionBody: 'Dein Konto und deine persönlichen Daten endgültig löschen.',
		deleteSectionLink: 'Konto löschen',
		deleteTitle: 'Konto endgültig löschen',
		deleteIntro: 'Wenn du dein Konto löschst, werden deine persönlichen Daten sofort entfernt.',
		deleteRemovedTitle: 'Das wird gelöscht',
		deleteRemovedBody:
			'E-Mail-Adresse und Name, Profilbild, alle Anwesenheits-Rückmeldungen und Abwesenheiten, Benachrichtigungen und deren Einstellungen, Team- und Vereins-Mitgliedschaften.',
		deleteKeptTitle: 'Das bleibt für dein Team erhalten',
		deleteKeptBody:
			'Events, die du erstellt hast, und Anwesenheiten, die du erfasst hast, bleiben für dein Team erhalten. Dein Name wird dort durch "Gelöschtes Konto" ersetzt.',
		deleteCoachWarning:
			'Teams, die du als Trainer betreust, haben danach keinen Trainer mehr, bis ein Vereinsmanager einen neuen zuweist.',
		deleteIrreversible: 'Das kann nicht rückgängig gemacht werden.',
		deletePasswordLabel: 'Passwort bestätigen',
		deleteButton: 'Konto löschen',
		deleteCancel: 'Abbrechen',
		deleteWrongPassword: 'Das Passwort ist falsch.',
		deleteOwnsClubs:
			'Du bist Besitzer von {clubs}. Kontaktiere info@teamorg.ch, damit wir den Verein übertragen oder auflösen können, bevor du dein Konto löschst.',
		deleteFailed: 'Konto konnte nicht gelöscht werden. Bitte versuche es erneut.'
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
		failed: 'Beitritt fehlgeschlagen. Bitte versuche es erneut.',
		alreadyRedeemedTitle: 'Einladung bereits verwendet',
		alreadyRedeemedBody: 'Diese Einladung wurde bereits verwendet.'
	},
	swissvolley: {
		importButton: 'Aus SwissVolley importieren',
		title: 'Teams aus SwissVolley importieren',
		subtitle: 'Wähle die Teams, die du als TeamOrg-Teams anlegen möchtest.',
		loading: 'Teams werden geladen…',
		noTeams: 'Keine Teams bei SwissVolley gefunden.',
		noKeyTitle: 'Kein gültiger SwissVolley-Schlüssel',
		noKeyBody: 'Hinterlege zuerst einen gültigen SwissVolley-API-Schlüssel in den Integrationseinstellungen.',
		loadFailed: 'Teams konnten nicht geladen werden. Bitte versuche es erneut.',
		selectAll: 'Alle auswählen',
		clearAll: 'Auswahl aufheben',
		genderM: 'Herren',
		genderF: 'Damen',
		genderMixed: 'Mixed',
		import: 'Importieren',
		importing: 'Wird importiert…',
		cancel: 'Abbrechen',
		selectAtLeastOne: 'Mindestens ein Team wählen.',
		importFailed: 'Import fehlgeschlagen. Bitte versuche es erneut.',
		importNoKey: 'Kein gültiger SwissVolley-Schlüssel mehr hinterlegt.',
		resultCreated: 'erstellt',
		resultSkipped: 'übersprungen',
		resultNone: 'Keine neuen Teams importiert.',
		postponed: 'Verschoben',
		sourceLabel: 'SwissVolley'
	},
	reconcile: {
		needsReviewTitle: 'Spiel wurde geändert',
		needsReviewBody:
			'SwissVolley hat die Spieldaten (Datum, Zeit oder Halle) aktualisiert. Bitte überprüfe die Angaben und entscheide, ob die bereits erfassten Rückmeldungen erhalten bleiben.',
		meetupAt: 'Besammlung',
		notes: 'Notizen',
		minAttendees: 'Mindestteilnehmer',
		availabilityTitle: 'Rückmeldungen',
		keepAvailability: 'Rückmeldungen behalten',
		resetAvailability: 'Rückmeldungen zurücksetzen und neu anfragen',
		submit: 'Überprüfung abschliessen',
		saving: 'Wird gespeichert…',
		reconciled: 'Überprüfung abgeschlossen.',
		failed: 'Speichern fehlgeschlagen. Bitte versuche es erneut.'
	},
	teamMigrate: {
		deprecatedBadge: 'Veraltet',
		action: 'Migrieren zu…',
		title: 'Team migrieren',
		subtitle: 'Verschiebe alle Mitglieder dieses Teams in ein aktives Nachfolge-Team.',
		targetLabel: 'Zielteam',
		selectPlaceholder: 'Zielteam wählen…',
		noTargets: 'Kein aktives Zielteam verfügbar.',
		submit: 'Mitglieder verschieben',
		submitting: 'Wird migriert…',
		cancel: 'Abbrechen',
		selectTarget: 'Bitte wähle ein Zielteam.',
		resultMoved: 'Mitglieder verschoben',
		errSourceNotDeprecated: 'Dieses Team ist nicht veraltet und kann nicht migriert werden.',
		errTargetNotLive: 'Das Zielteam ist kein aktives SwissVolley-Team.',
		errFailed: 'Migration fehlgeschlagen. Bitte versuche es erneut.'
	},
	manage: {
		editClubTitle: 'Verein bearbeiten',
		locationLabel: 'Standort',
		logoTitle: 'Vereinslogo',
		logoHint: 'JPG, PNG oder WebP, bis 2MB.',
		logoUpdated: 'Logo aktualisiert.',
		uploadLogo: 'Logo hochladen',
		coManagersTitle: 'Co-Manager',
		coManagersBody: 'Lade eine weitere Person ein, diesen Verein mitzuverwalten.',
		inviteLinkGenerated: 'Einladungslink erstellt!',
		expiresLabel: 'Gültig bis:',
		inviteCoManager: 'Co-Manager einladen',
		emailPlaceholderColleague: 'kollege@example.com',
		sendInvite: 'Einladung senden',
		teamsQuickLinkBody: 'Kader verwalten, Mitglieder einladen, Teaminfos aktualisieren.',
		viewTeamsLink: 'Teams ansehen',
		membersQuickLinkBody: 'Alle Club-Mitglieder anzeigen, zu Teams hinzufügen oder entfernen.',
		manageMembersTitle: 'Mitglieder verwalten',
		ndsImport: 'NDS-Import',
		newTeam: 'Neues Team',
		teamNamePlaceholder: 'z.B. U18 Herren',
		optionalPlaceholder: 'Optional',
		noTeamsYet: 'Noch keine Teams. Lege eines an, um loszulegen.',
		viewLink: 'Ansehen ›',
		backToTeams: '‹ Zurück zu Teams',
		attendance: 'Anwesenheit',
		archive: 'Archivieren',
		inviteMembersTitle: 'Mitglieder einladen',
		inviteMembersBody:
			'Gib eine E-Mail-Adresse an, um eine Person privat einzuladen — nur diese Adresse kann beitreten (empfohlen für Trainer). Lass das Feld leer für einen teilbaren Link, den jeder verwenden kann.',
		inviteOnlyEmailPrefix: 'Nur',
		inviteOnlyEmailSuffix: 'kann diese Einladung verwenden.',
		inviteEmailedToPrefix: 'Einladung gesendet an',
		shareableInviteGenerated: 'Teilbarer Einladungslink erstellt!',
		inviteMember: 'Mitglied einladen',
		emailOptionalLabel: 'E-Mail (optional)',
		emailPlaceholderPerson: 'person@example.com',
		createInvite: 'Einladung erstellen',
		subgroupsTitle: 'Untergruppen',
		subgroupsBody:
			'Organisiere das Kader in Untergruppen (z.B. Stammspieler, U18), um Termine an einen Teil des Teams zu richten.',
		memberSingular: 'Mitglied',
		rename: 'Umbenennen',
		delete: 'Löschen',
		addSubgroup: 'Untergruppe hinzufügen',
		subgroupNameLabel: 'Name der Untergruppe',
		subgroupNamePlaceholder: 'z.B. Stammspieler',
		subgroupSelectMemberPlaceholder: 'Mitglied wählen…',
		subgroupAddMemberLabel: 'Hinzufügen',
		noMembersYet: 'Noch keine Mitglieder. Erstelle einen Einladungslink, um Mitglieder hinzuzufügen.',
		actionsLabel: 'Aktionen',
		makeCoach: 'Zum Trainer machen',
		makePlayer: 'Zum Spieler machen',
		remove: 'Entfernen',
		archiveTeamTitle: 'Team archivieren',
		archiveConfirmPrefix: '',
		archiveConfirmSuffix: ' archivieren? Mitglieder sehen dieses Team danach nicht mehr. Dies kann später rückgängig gemacht werden.',
		removeMemberTitle: 'Mitglied entfernen',
		removeConfirmPrefix: '',
		removeConfirmMiddle: ' aus ',
		removeConfirmSuffix: ' entfernen? Der Zugriff wird sofort entzogen.',
		deleteSubgroupTitle: 'Untergruppe löschen',
		deleteSubgroupConfirmPrefix: 'Untergruppe ',
		deleteSubgroupConfirmSuffix: ' löschen? Mitglieder bleiben im Team; nur die Gruppierung wird entfernt.',
		membersPageTitle: 'Mitglieder',
		inviteByEmailTitle: 'Per E-Mail einladen',
		teamLabel: 'Team',
		inviteButton: 'Einladen',
		noTeamsForInvite: 'Erst ein Team anlegen, bevor Mitglieder eingeladen werden können.',
		filterPlaceholder: 'Filtern…',
		noMatch: 'Keine Übereinstimmung.',
		noMembersFound: 'Keine Mitglieder gefunden.',
		addTeamButton: '+ Team hinzufügen',
		loadingShort: 'Lädt…',
		loadMore: 'Mehr laden',
		shownCountMiddle: 'von',
		shownCountSuffix: 'angezeigt',
		archivedTeamsTitle: 'Archivierte Teams',
		unarchive: 'Wiederherstellen'
	},
	onboarding: {
		welcomeTitle: 'Willkommen bei TeamOrg',
		welcomeSubtitle: 'Tritt einem bestehenden Team bei oder erstelle einen neuen Verein.',
		joinCta: 'Team beitreten',
		createCta: 'Verein erstellen',
		loginCta: 'Ich habe bereits ein Konto',
		joinTitle: 'Team beitreten',
		joinCodeLabel: 'Einladungscode',
		joinCodeInvalid: 'Dieser Code ist ungültig oder abgelaufen.',
		joinSubmit: 'Beitreten',
		createAccountTitle: 'Konto erstellen',
		createDetailsTitle: 'Verein oder Team einrichten',
		kindTeam: 'Team',
		kindClub: 'Verein',
		kindTeamHint: 'Ein einzelnes Team ohne mehrere Mannschaften.',
		kindClubHint: 'Ein Verein mit mehreren Teams.',
		kindSwitchNote: 'Du kannst später jederzeit zwischen Team und Verein wechseln.',
		nameLabel: 'Name',
		sportLabel: 'Sportart',
		locationLabel: 'Standort',
		billingEmailLabel: 'Rechnungs-E-Mail',
		pricingNote: 'CHF 2 pro Mitglied und Jahr, jeweils im Januar abgerechnet.',
		cardTitle: 'Zahlungsmethode hinterlegen',
		cardSubmit: 'Bestätigen',
		cardProcessing: 'Wird verarbeitet…',
		cardError: 'Zahlungsmethode konnte nicht gespeichert werden. Bitte versuche es erneut.',
		done: 'Fertig',
		emailLabel: 'E-Mail',
		passwordLabel: 'Passwort',
		registerSubmit: 'Konto erstellen',
		haveAccount: 'Ich habe bereits ein Konto',
		toLogin: 'Anmelden',
		nameRequired: 'Bitte gib einen Namen ein.',
		billingEmailInvalid: 'Bitte gib eine gültige E-Mail-Adresse ein.',
		detailsSubmit: 'Weiter zur Zahlung',
		registerFailed: 'Registrierung fehlgeschlagen. Bitte versuche es erneut.',
		emailTaken: 'Für diese E-Mail existiert bereits ein Konto. Bitte melde dich an.'
	},
	billing: {
		title: 'Abrechnung',
		cardOnFile: 'Hinterlegte Karte',
		noCard: 'Keine Karte hinterlegt',
		memberCount: 'Aktuelle Mitgliederzahl',
		projectedCount: 'Voraussichtlich abgerechnete Mitglieder',
		countBasisNote:
			'Abgerechnet wird die höhere Zahl aus Mitgliederzahl per Jahresende und dem Mittelwert der Saison.',
		manualNote: 'Die Abrechnung für diesen Verein wird manuell verwaltet.',
		updateCard: 'Karte aktualisieren',
		cardSubmit: 'Karte speichern',
		cardProcessing: 'Wird verarbeitet…',
		cardUpdateError: 'Karte konnte nicht gespeichert werden. Bitte versuche es erneut.',
		currentKind: 'Aktuell',
		convertToClub: 'Zu Verein wechseln',
		convertToTeam: 'Zu Team wechseln',
		convertBlocked: 'Wechsel derzeit nicht möglich.',
		convertedToClubNote: 'Als Verein kannst du jetzt mehrere Teams verwalten.',
		statusActive: 'Aktiv',
		statusPastDue: 'Zahlung überfällig',
		statusFrozen: 'Gesperrt',
		frozenBanner: 'Dieser Verein ist wegen einer überfälligen Zahlung gesperrt.',
		frozenBannerCta: 'Zahlungsmethode aktualisieren',
		pastDueBanner:
			'Die Zahlung für diesen Verein ist überfällig. Bitte aktualisiere bald deine Zahlungsmethode.'
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
		manageArea: 'Club',
		club: 'Club',
		billing: 'Billing',
		memberSection: 'My participation',
		logout: 'Sign out'
	},
	login: {
		title: 'TeamOrg',
		subtitle: 'Sign in with your account',
		email: 'Email',
		password: 'Password',
		submit: 'Sign in',
		errRequired: 'Email and password required',
		newHere: 'New here?',
		startCta: 'Get started'
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
		allTeams: 'All teams',
		cancelEvent: 'Cancel',
		uncancelEvent: 'Restore',
		duplicate: 'Duplicate',
		cancelledBadge: 'Cancelled'
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
		deadlinePassed: 'The response deadline has passed.',
		minAttendeesWarning: 'Minimum attendees not reached'
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
		fResponseDeadline: 'Response deadline (optional)',
		selectTeams: 'Select at least one team',
		required: 'Required',
		recurringSection: 'Recurrence',
		recurringEnable: 'Repeat as series',
		fPattern: 'Pattern',
		patternWeekly: 'Weekly',
		patternDaily: 'Daily',
		patternCustom: 'Custom',
		fDefaultResponse: 'Default response',
		defaultResponseNone: 'No default – coach must resolve',
		defaultResponseAccepted: 'Default: Attending',
		defaultResponseDeclined: 'Default: Declined',
		fWeekdays: 'Weekdays',
		fIntervalDays: 'Interval (days)',
		fSeriesEnd: 'Series end',
		weekdayMon: 'Mon',
		weekdayTue: 'Tue',
		weekdayWed: 'Wed',
		weekdayThu: 'Thu',
		weekdayFri: 'Fri',
		weekdaySat: 'Sat',
		weekdaySun: 'Sun'
	},
	schedule: {
		carryOverTitle: 'Take over previous schedule',
		carryOverBody:
			'The predecessor team had a recurring schedule. Take over individual series and adjust dates and fields for the new season.',
		carryOverAction: 'Take over schedule',
		pickSeries: 'Select a series to take over',
		loading: 'Loading…',
		loadFailed: 'Could not load series.',
		empty: 'No importable series found.',
		takeOver: 'Take over'
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
		email: 'Email',
		changePasswordTitle: 'Change password',
		currentPasswordLabel: 'Current password',
		newPasswordLabel: 'New password',
		confirmPasswordLabel: 'Confirm new password',
		changePasswordButton: 'Change password',
		passwordChanged: 'Password changed.',
		passwordMismatch: 'Passwords do not match.',
		passwordTooShort: 'The password must be at least 8 characters long.',
		passwordWrongCurrent: 'The current password is incorrect.',
		passwordChangeFailed: 'Could not change password. Please try again.',
		deleteSectionTitle: 'Delete account',
		deleteSectionBody: 'Permanently delete your account and personal data.',
		deleteSectionLink: 'Delete account',
		deleteTitle: 'Permanently delete your account',
		deleteIntro: 'Deleting your account removes your personal data immediately.',
		deleteRemovedTitle: 'What gets deleted',
		deleteRemovedBody:
			'Your email address and name, profile picture, all attendance replies and absences, notifications and their settings, and your team and club memberships.',
		deleteKeptTitle: 'What stays with your team',
		deleteKeptBody:
			'Events you created and attendance you recorded stay with your team. Your name is replaced there with "Gelöschtes Konto".',
		deleteCoachWarning:
			'Teams you coach will have no coach until a club manager assigns a new one.',
		deleteIrreversible: 'This cannot be undone.',
		deletePasswordLabel: 'Confirm your password',
		deleteButton: 'Delete account',
		deleteCancel: 'Cancel',
		deleteWrongPassword: 'That password is incorrect.',
		deleteOwnsClubs:
			'You own {clubs}. Contact info@teamorg.ch so we can transfer or close the club before you delete your account.',
		deleteFailed: 'Could not delete your account. Please try again.'
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
		failed: 'Could not join. Please try again.',
		alreadyRedeemedTitle: 'Invitation already used',
		alreadyRedeemedBody: 'This invite has already been used.'
	},
	swissvolley: {
		importButton: 'Import from SwissVolley',
		title: 'Import teams from SwissVolley',
		subtitle: 'Select the teams you want to create as TeamOrg teams.',
		loading: 'Loading teams…',
		noTeams: 'No teams found at SwissVolley.',
		noKeyTitle: 'No valid SwissVolley key',
		noKeyBody: 'Set a valid SwissVolley API key in the integration settings first.',
		loadFailed: 'Could not load teams. Please try again.',
		selectAll: 'Select all',
		clearAll: 'Clear selection',
		genderM: 'Men',
		genderF: 'Women',
		genderMixed: 'Mixed',
		import: 'Import',
		importing: 'Importing…',
		cancel: 'Cancel',
		selectAtLeastOne: 'Select at least one team.',
		importFailed: 'Import failed. Please try again.',
		importNoKey: 'No valid SwissVolley key is set anymore.',
		resultCreated: 'created',
		resultSkipped: 'skipped',
		resultNone: 'No new teams imported.',
		postponed: 'Postponed',
		sourceLabel: 'SwissVolley'
	},
	reconcile: {
		needsReviewTitle: 'Game was changed',
		needsReviewBody:
			'SwissVolley updated the game details (date, time or venue). Please review and decide whether the already-collected availability should be kept.',
		meetupAt: 'Meetup time',
		notes: 'Notes',
		minAttendees: 'Min. attendees',
		availabilityTitle: 'Availability',
		keepAvailability: 'Keep availability',
		resetAvailability: 'Reset availability and ask again',
		submit: 'Finish review',
		saving: 'Saving…',
		reconciled: 'Review completed.',
		failed: 'Could not save. Please try again.'
	},
	teamMigrate: {
		deprecatedBadge: 'Deprecated',
		action: 'Migrate to…',
		title: 'Migrate team',
		subtitle: 'Move all members of this team into an active successor team.',
		targetLabel: 'Target team',
		selectPlaceholder: 'Select target team…',
		noTargets: 'No active target team available.',
		submit: 'Move members',
		submitting: 'Migrating…',
		cancel: 'Cancel',
		selectTarget: 'Please select a target team.',
		resultMoved: 'members moved',
		errSourceNotDeprecated: 'This team is not deprecated and cannot be migrated.',
		errTargetNotLive: 'The target team is not an active SwissVolley team.',
		errFailed: 'Migration failed. Please try again.'
	},
	manage: {
		editClubTitle: 'Edit club',
		locationLabel: 'Location',
		logoTitle: 'Club logo',
		logoHint: 'JPG, PNG or WebP, up to 2MB.',
		logoUpdated: 'Logo updated.',
		uploadLogo: 'Upload logo',
		coManagersTitle: 'Co-managers',
		coManagersBody: 'Invite another person to co-manage this club.',
		inviteLinkGenerated: 'Invite link generated!',
		expiresLabel: 'Expires:',
		inviteCoManager: 'Invite co-manager',
		emailPlaceholderColleague: 'colleague@example.com',
		sendInvite: 'Send invite',
		teamsQuickLinkBody: 'Manage rosters, invite members, update team info.',
		viewTeamsLink: 'View Teams',
		membersQuickLinkBody: 'View all club members, add to teams or remove.',
		manageMembersTitle: 'Manage members',
		ndsImport: 'NDS-Import',
		newTeam: 'New team',
		teamNamePlaceholder: 'e.g. U18 Boys',
		optionalPlaceholder: 'Optional',
		noTeamsYet: 'No teams yet. Create one to get started.',
		viewLink: 'View ›',
		backToTeams: '‹ Back to Teams',
		attendance: 'Attendance',
		archive: 'Archive',
		inviteMembersTitle: 'Invite members',
		inviteMembersBody:
			'Add an email to invite one person privately — only that address can join (best for coaches). Leave it empty for a shareable link anyone can use.',
		inviteOnlyEmailPrefix: 'Only',
		inviteOnlyEmailSuffix: 'can use this invite.',
		inviteEmailedToPrefix: 'Invite emailed to',
		shareableInviteGenerated: 'Shareable invite link generated!',
		inviteMember: 'Invite a member',
		emailOptionalLabel: 'Email (optional)',
		emailPlaceholderPerson: 'person@example.com',
		createInvite: 'Create invite',
		subgroupsTitle: 'Subgroups',
		subgroupsBody:
			'Organise the roster into subgroups (e.g. starters, U18) to target events at part of the team.',
		memberSingular: 'member',
		rename: 'Rename',
		delete: 'Delete',
		addSubgroup: 'Add subgroup',
		subgroupNameLabel: 'Subgroup name',
		subgroupNamePlaceholder: 'e.g. Starters',
		subgroupSelectMemberPlaceholder: 'Select member…',
		subgroupAddMemberLabel: 'Add',
		noMembersYet: 'No members yet. Generate an invite link to add members.',
		actionsLabel: 'Actions',
		makeCoach: 'Make Coach',
		makePlayer: 'Make Player',
		remove: 'Remove',
		archiveTeamTitle: 'Archive team',
		archiveConfirmPrefix: 'Archive ',
		archiveConfirmSuffix: '? Members will no longer see this team. This can be reversed later.',
		removeMemberTitle: 'Remove member',
		removeConfirmPrefix: 'Remove ',
		removeConfirmMiddle: ' from ',
		removeConfirmSuffix: '? They will lose access immediately.',
		deleteSubgroupTitle: 'Delete subgroup',
		deleteSubgroupConfirmPrefix: 'Delete subgroup ',
		deleteSubgroupConfirmSuffix: '? Members stay on the team; only the grouping is removed.',
		membersPageTitle: 'Members',
		inviteByEmailTitle: 'Invite by email',
		teamLabel: 'Team',
		inviteButton: 'Invite',
		noTeamsForInvite: 'Create a team first before you can invite members.',
		filterPlaceholder: 'Filter…',
		noMatch: 'No matches.',
		noMembersFound: 'No members found.',
		addTeamButton: '+ Add team',
		loadingShort: 'Loading…',
		loadMore: 'Load more',
		shownCountMiddle: 'of',
		shownCountSuffix: 'shown',
		archivedTeamsTitle: 'Archived teams',
		unarchive: 'Restore'
	},
	onboarding: {
		welcomeTitle: 'Welcome to TeamOrg',
		welcomeSubtitle: 'Join an existing team or create a new club.',
		joinCta: 'Join a team',
		createCta: 'Create a club',
		loginCta: 'I already have an account',
		joinTitle: 'Join a team',
		joinCodeLabel: 'Invite code',
		joinCodeInvalid: 'This code is invalid or expired.',
		joinSubmit: 'Join',
		createAccountTitle: 'Create account',
		createDetailsTitle: 'Set up your club or team',
		kindTeam: 'Team',
		kindClub: 'Club',
		kindTeamHint: 'A single team without multiple squads.',
		kindClubHint: 'A club with multiple teams.',
		kindSwitchNote: 'You can switch between team and club anytime later.',
		nameLabel: 'Name',
		sportLabel: 'Sport',
		locationLabel: 'Location',
		billingEmailLabel: 'Billing email',
		pricingNote: 'CHF 2 per member per year, billed each January.',
		cardTitle: 'Add payment method',
		cardSubmit: 'Confirm',
		cardProcessing: 'Processing…',
		cardError: 'Could not save payment method. Please try again.',
		done: 'Done',
		emailLabel: 'Email',
		passwordLabel: 'Password',
		registerSubmit: 'Create account',
		haveAccount: 'I already have an account',
		toLogin: 'Sign in',
		nameRequired: 'Please enter a name.',
		billingEmailInvalid: 'Please enter a valid email address.',
		detailsSubmit: 'Continue to payment',
		registerFailed: 'Registration failed. Please try again.',
		emailTaken: 'An account already exists for this email. Please sign in.'
	},
	billing: {
		title: 'Billing',
		cardOnFile: 'Card on file',
		noCard: 'No card on file',
		memberCount: 'Current member count',
		projectedCount: 'Projected billed members',
		countBasisNote:
			'You are billed for whichever is higher: the member count at year-end or the median count across the season.',
		manualNote: 'Billing for this club is managed manually.',
		updateCard: 'Update card',
		cardSubmit: 'Save card',
		cardProcessing: 'Processing…',
		cardUpdateError: 'Could not save the card. Please try again.',
		currentKind: 'Currently',
		convertToClub: 'Convert to club',
		convertToTeam: 'Convert to team',
		convertBlocked: 'Conversion is not possible right now.',
		convertedToClubNote: 'As a club, you can now manage multiple teams.',
		statusActive: 'Active',
		statusPastDue: 'Past due',
		statusFrozen: 'Frozen',
		frozenBanner: 'This club is frozen due to a past-due payment.',
		frozenBannerCta: 'Update payment method',
		pastDueBanner: 'Payment for this club is past due. Please update your payment method soon.'
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
