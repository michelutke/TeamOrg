<script lang="ts">
	import type { PageData } from './$types';
	let { data }: { data: PageData } = $props();
	const de = $derived(data.lang === 'de');
</script>

<svelte:head>
	<title>{data.m.legal.privacyTitle} · teamorg</title>
	<meta name="robots" content="noindex" />
</svelte:head>

<article class="mx-auto max-w-[760px] px-6 py-16 md:py-20">
	<a href="/" class="text-[14px] font-medium text-primary hover:underline">{data.m.legal.back}</a>
	<h1 class="font-display mt-6 text-[32px] font-extrabold tracking-tight text-on-surface md:text-[40px]">
		{data.m.legal.privacyTitle}
	</h1>

	<div class="mt-8 flex flex-col gap-7 text-[15px] leading-[1.65] text-on-surface-variant">
		{#if de}
			<p class="text-[14px]">Stand: 30. Oktober 2026</p>

			<p>
				Diese Datenschutzerklärung informiert über die Bearbeitung von Personendaten im Zusammenhang
				mit der Website teamorg.ch, der Web-App (app.teamorg.ch) und den teamorg-Apps für iOS und
				Android, gemäss dem schweizerischen Datenschutzgesetz (revDSG) und, soweit anwendbar, der DSGVO.
			</p>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">1. Verantwortliche Stelle</h2>
				<p class="mt-2">
					Michel Utke<br />
					Web: <a href="https://michelutke.com" rel="noopener" class="text-primary hover:underline"
						>michelutke.com</a
					><br />
					Kontakt: <a href="mailto:info@teamorg.ch" class="text-primary hover:underline"
						>info@teamorg.ch</a
					>
				</p>
				<p class="mt-3 text-[14px]">
					Die Inhalte dieser Website werden von Michel Utke verwaltet. Die Postadresse wird auf
					Anfrage per E-Mail bekanntgegeben.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">2. Geltungsbereich</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>Website:</strong> teamorg.ch (Informationen und Kontaktformular).
					</li>
					<li>
						<strong>Web-App:</strong> app.teamorg.ch (Anmeldung und Nutzung von teamorg im Browser).
					</li>
					<li>
						<strong>Mobile Apps:</strong> die teamorg-Apps für iOS und Android.
					</li>
				</ul>
				<p class="mt-3">
					teamorg ist ein Werkzeug für Sportvereine: Vereine organisieren darin ihre Teams, Termine und Anwesenheiten. Für die Bearbeitung der in dieser Erklärung beschriebenen Daten ist die unter Ziffer&nbsp;1 genannte Stelle verantwortlich.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">3. Bearbeitung beim Kontaktformular (Website)</h2>
				<p class="mt-2">
					Wenn du das Kontaktformular nutzt, bearbeiten wir die von dir angegebenen Daten:
					Vereinsname, Name der Ansprechperson, E-Mail-Adresse, optional die Anzahl Mitglieder sowie
					deine Nachricht. Zweck ist ausschliesslich die Bearbeitung und Beantwortung deiner
					Anfrage. Rechtsgrundlage ist deine Einwilligung bzw. unser berechtigtes Interesse an der
					Kommunikation mit Interessenten. Die Angabe ist freiwillig; ohne sie können wir deine
					Anfrage nicht beantworten.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">4. Daten bei der Nutzung der App und der Web-App</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>Konto:</strong> Name (Anzeigename), E-Mail-Adresse und Passwort. Das Passwort wird nur als Hash gespeichert, nicht im Klartext.
					</li>
					<li>
						<strong>Profil:</strong> optional ein Profilbild.
					</li>
					<li>
						<strong>Zugehörigkeit:</strong> Verein, Teams, Untergruppen und deine Rolle (Clubmanager, Coach oder Spieler).
					</li>
					<li>
						<strong>Termine:</strong> Trainings, Spiele und Events deiner Teams (Titel, Zeit, Ort, Notizen).
					</li>
					<li>
						<strong>Anwesenheit:</strong> deine Zusagen, Absagen und «Unsicher»-Meldungen mit Zeitpunkt. Bei «Unsicher» ist eine kurze Begründung erforderlich, bei Absagen ist sie möglich. Coaches können die Anwesenheit nachtragen oder korrigieren.
					</li>
					<li>
						<strong>Absenzen:</strong> von dir erfasste Abwesenheiten (Ferien, Verletzung, Arbeit, Schule, Reisen, Sonstiges) mit Bezeichnung, Zeitraum bzw. Wochentagen und optional dem betroffenen Körperteil.
					</li>
					<li>
						<strong>Benachrichtigungen:</strong> deine Benachrichtigungseinstellungen, Erinnerungen und die Meldungen in der App-Inbox.
					</li>
					<li>
						<strong>Einladungen:</strong> die E-Mail-Adresse einer eingeladenen Person und der Einladungslink.
					</li>
					<li>
						<strong>Technische Daten:</strong> beim Zugriff auf unsere Server fallen technisch notwendige Server-Logs an (u. a. IP-Adresse, Zeitpunkt, aufgerufene Adresse).
					</li>
				</ul>
				<p class="mt-3">
					<strong>Hinweis zu Gesundheitsdaten:</strong> Absenzen wie «Verletzung» (mit optionalem Körperteil) und Begründungen bei Absagen können Angaben zu deiner Gesundheit enthalten. Diese gelten als besonders schützenswert. Die Angabe ist freiwillig. Bitte halte Begründungen kurz und gib nur an, was für die Planung nötig ist (z.&nbsp;B. «verletzt», keine Diagnosen). Begründungen zu Zu- und Absagen sind für die Mitglieder deines Teams sichtbar, Coaches und Clubmanager sehen zudem die Anwesenheitsübersicht (siehe Ziffer&nbsp;6).
				</p>
				<p class="mt-3">
					<strong>Daten, die Vereine einspielen:</strong> Clubmanager können Mitgliederlisten aus der J+S-Datenbank NDS importieren (Name, Vorname, Geburtsdatum, Personennummer, Funktion) und, mit dem API-Schlüssel ihres Vereins, Spielpläne von Swiss Volley abgleichen. Der Verein entscheidet, welche Daten er einspielt.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">5. Zwecke und Rechtsgrundlagen</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>Betrieb der App:</strong> Konto, Teams, Termine, Zu- und Absagen, Absenzen, Erinnerungen und Abrechnung. Rechtsgrundlage ist die Erfüllung des Nutzungsverhältnisses.
					</li>
					<li>
						<strong>Sicherheit und Stabilität:</strong> Schutz vor Missbrauch, Fehleranalyse, Server-Logs. Rechtsgrundlage ist unser berechtigtes Interesse.
					</li>
					<li>
						<strong>Freiwillige Angaben:</strong> Profilbild, Absenzgründe und Push-Benachrichtigungen. Rechtsgrundlage ist deine Einwilligung, die du jederzeit widerrufen kannst.
					</li>
					<li>
						<strong>Gesetzliche Pflichten:</strong> z.&nbsp;B. Aufbewahrungspflichten für Rechnungsdaten.
					</li>
				</ul>
				<p class="mt-3">
					Wir verwenden deine Daten nicht für andere Zwecke.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">6. Wer welche Daten sieht</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>Du selbst:</strong> alle deine eigenen Daten.
					</li>
					<li>
						<strong>Mitglieder deiner Teams:</strong> Namen, Profilbilder und Rollen der Teammitglieder, die Termine des Teams sowie die Zu- und Absagen der Teammitglieder zu den Terminen, inklusive der angegebenen Kurzbegründung.
					</li>
					<li>
						<strong>Coaches:</strong> zusätzlich Termine erstellen und ändern, die Anwesenheit der Spieler ihrer Teams einsehen, nachtragen und abschliessen sowie die Anwesenheitsübersicht der Spieler ihrer Teams einsehen.
					</li>
					<li>
						<strong>Clubmanager:</strong> die Mitglieder, Teams, Rollen und Einladungen ihres Vereins sowie die Abrechnung des Vereins; bei den Teams ihres Vereins dieselben Einsichten wie Coaches.
					</li>
					<li>
						<strong>Betreiber:</strong> technischer Zugriff auf die Daten für Betrieb und Support. Support-Zugriffe im Namen eines Kontos werden protokolliert.
					</li>
				</ul>
				<p class="mt-3">
					Daten eines Vereins sind für andere Vereine nicht sichtbar. Wir geben deine Daten nicht an Dritte weiter, ausser an die in Ziffer&nbsp;9 genannten Dienstleister.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">7. Push-Benachrichtigungen</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						Push-Benachrichtigungen (z.&nbsp;B. Erinnerung an einen Termin, neuer oder geänderter Termin) werden nur gesendet, wenn du sie erlaubst. Beim ersten Start fragt das Betriebssystem dich danach.
					</li>
					<li>
						Dafür setzen wir <strong>OneSignal</strong> (OneSignal Inc., USA) ein. OneSignal erhält ein Geräte-Token, technische Geräte- und App-Informationen, eine zufällige Kennung deines Kontos sowie den Inhalt der Nachricht (Titel und Text).
					</li>
					<li>
						Du kannst Push-Benachrichtigungen jederzeit in den Systemeinstellungen deines Geräts oder in den Benachrichtigungseinstellungen der App deaktivieren.
					</li>
				</ul>
				<p class="mt-3">
					Die Web-App sendet keine Push-Benachrichtigungen.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">8. Zahlungen</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						Vereine können kostenpflichtige Pläne abschliessen. Die Zahlung per Karte wird in der Android-App und in der Web-App angeboten, nicht in der iOS-App.
					</li>
					<li>
						Die Kartendaten gibst du direkt bei <strong>Stripe</strong> ein (Stripe Payments Europe, Ltd., Irland, bzw. Stripe, Inc., USA). Sie gelangen nicht auf unsere Server.
					</li>
					<li>
						Stripe erhält die Rechnungs-E-Mail-Adresse und den Namen des Vereins. Bei uns gespeichert werden Referenzen auf Stripe (Kunden- und Abonnement-Kennung), die Rechnungs-E-Mail-Adresse, Kartenmarke, die letzten vier Ziffern und das Ablaufdatum der Karte sowie die Anzahl Mitglieder des Vereins für die Abrechnung.
					</li>
				</ul>
				<p class="mt-3">
					Rechtsgrundlage ist die Erfüllung des Vertrags mit dem Verein.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">9. Auftragsbearbeiter & Drittdienste</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>E-Mail-Versand (Infomaniak Network AG, Schweiz):</strong> Deine Anfrage wird per E-Mail an info@teamorg.ch zugestellt und dort gespeichert.
					</li>
					<li>
						<strong>E-Mail-Versand der App (Infomaniak Network AG, Schweiz):</strong> Einladungen zu Teams und Vereinen werden per E-Mail an die eingeladene Adresse versandt. Dabei werden die Adresse, der Name der einladenden Person, Verein bzw. Team und der Einladungslink übermittelt.
					</li>
					<li>
						<strong>Spam-Schutz (Cloudflare Turnstile, Cloudflare Inc.):</strong> Zur Abwehr von Missbrauch wird beim Absenden des Kontaktformulars eine Sicherheitsprüfung durchgeführt. Dabei können technische Daten (u. a. IP-Adresse, Browserinformationen) an Cloudflare übermittelt werden.
					</li>
					<li>
						<strong>Hosting (Hetzner, Deutschland):</strong> Website, Web-App, Server, Datenbank und hochgeladene Dateien (z.&nbsp;B. Profilbilder) werden bei Hetzner in Nürnberg (Deutschland, EU) betrieben. Dabei fallen technisch notwendige Server-Logs an.
					</li>
					<li>
						<strong>Push-Benachrichtigungen (OneSignal Inc., USA):</strong> siehe Ziffer&nbsp;7.
					</li>
					<li>
						<strong>Zahlungen (Stripe Payments Europe, Ltd., Irland, und Stripe, Inc., USA):</strong> siehe Ziffer&nbsp;8.
					</li>
					<li>
						<strong>Spielplan-Abgleich (Swiss Volley):</strong> Nur wenn ein Verein den Abgleich einrichtet, ruft teamorg mit dem API-Schlüssel des Vereins dessen Spielplan bei Swiss Volley ab.
					</li>
					<li>
						<strong>Schriftarten:</strong> Schriften werden lokal von teamorg.ch ausgeliefert (selbst gehostet). Es werden keine Daten an Drittanbieter wie Google Fonts übermittelt.
					</li>
				</ul>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">10. Bekanntgabe ins Ausland</h2>
				<p class="mt-2">
					Der E-Mail-Anbieter Infomaniak verarbeitet die Daten in der <strong>Schweiz</strong>;
					das Hosting erfolgt bei Hetzner in <strong>Deutschland (EU)</strong>. Stripe Payments Europe
					hat seinen Sitz in <strong>Irland (EU)</strong>. OneSignal, Stripe, Inc. und Cloudflare haben
					ihren Sitz in den <strong>USA</strong>; dabei können Daten in die USA übermittelt werden
					(bei OneSignal Push-Daten, bei Stripe Zahlungsdaten, bei Cloudflare technische Daten des
					Kontaktformulars). Diese Übermittlung stützt sich auf das Swiss-U.S. Data Privacy Framework
					bzw. auf Standardvertragsklauseln als Garantie für ein angemessenes Datenschutzniveau
					(Art.&nbsp;16 revDSG).
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">11. Cookies und lokale Speicherung</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>Website:</strong> ein funktionales Cookie (<code>lang</code>), das deine Sprachauswahl speichert.
					</li>
					<li>
						<strong>Web-App:</strong> ein technisch notwendiges Sitzungs-Cookie (<code>to_session</code>), das dich angemeldet hält (30 Tage, nur über HTTPS, nicht für Skripte lesbar).
					</li>
					<li>
						<strong>Apps:</strong> die Anmeldung und, für den Offline-Betrieb, Termine und Antworten werden lokal auf deinem Gerät gespeichert.
					</li>
				</ul>
				<p class="mt-3">
					Diese Cookies und lokalen Daten dienen nicht der Analyse oder Werbung.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">12. Kein Tracking, keine Werbung</h2>
				<p class="mt-2">
					teamorg verwendet keine Analyse- oder Tracking-Dienste, keine Absturz-Statistiken von Drittanbietern und keine Werbung. Wir verkaufen keine Personendaten und geben sie nicht zu Werbezwecken weiter.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">13. Aufbewahrung und Löschung</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>Kontaktanfragen</strong> werden so lange aufbewahrt, wie es zur Bearbeitung sowie zur Erfüllung gesetzlicher Pflichten erforderlich ist, und anschliessend gelöscht.
					</li>
					<li>
						<strong>App-Daten</strong> bleiben gespeichert, solange dein Konto besteht. Meldungen in der App-Inbox werden nach 90 Tagen automatisch gelöscht.
					</li>
					<li>
						<strong>Rechnungs- und Zahlungsdaten</strong> werden so lange aufbewahrt, wie es gesetzliche Aufbewahrungspflichten verlangen.
					</li>
				</ul>
				<p class="mt-3">
					<strong>Konto löschen:</strong> In der App öffnest du den Tab «Profile» und tippst auf «Delete account». Hast du noch kein Team, findest du dieselbe Funktion auf dem Startbildschirm. Danach bestätigst du mit deinem Passwort. In der Web-App geht das unter app.teamorg.ch/app/profile/delete.
				</p>
				<p class="mt-3">
					<strong>Was dabei passiert:</strong> Gelöscht werden deine Anwesenheitsantworten, Absenzen, Benachrichtigungen und Einstellungen, Erinnerungen, Team-, Gruppen- und Vereinsrollen sowie dein Profilbild. Dein Konto wird anonymisiert: E-Mail-Adresse, Name und Passwort werden ersetzt, der Eintrag erscheint als «Gelöschtes Konto», und eine Anmeldung ist nicht mehr möglich. Der anonymisierte Eintrag bleibt bestehen, weil Termine und Protokolle darauf verweisen. Deine E-Mail-Adresse wird auch aus Einladungen entfernt. Bestehende Sitzungen werden sofort beendet. Von einem Verein importierte Mitgliederlisten bleiben beim Verein bestehen.
				</p>
				<p class="mt-3">
					Wenn du Eigentümer eines Vereins bist, muss der Verein vorher übertragen oder geschlossen werden. Schreib uns dazu an <a href="mailto:info@teamorg.ch" class="text-primary hover:underline">info@teamorg.ch</a>.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">14. Kinder und Jugendliche</h2>
				<p class="mt-2">
					Vereine nutzen teamorg auch für Jugendteams. Konten von Minderjährigen sollen nur mit Einwilligung der Eltern bzw. Erziehungsberechtigten erstellt werden. Vereine, die Jugendliche einladen, stellen sicher, dass diese Einwilligung vorliegt. Eltern und Erziehungsberechtigte können jederzeit Auskunft oder Löschung des Kontos verlangen: <a href="mailto:info@teamorg.ch" class="text-primary hover:underline">info@teamorg.ch</a>.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">15. Deine Rechte</h2>
				<p class="mt-2">
					Du hast im Rahmen des anwendbaren Rechts das Recht auf Auskunft, Berichtigung, Löschung
					und Einschränkung der Bearbeitung deiner Personendaten sowie das Recht, eine Einwilligung
					zu widerrufen. Wende dich dazu an <a
						href="mailto:info@teamorg.ch"
						class="text-primary hover:underline">info@teamorg.ch</a
					>. Zuständige Aufsichtsbehörde in der Schweiz ist der EDÖB.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">16. Änderungen</h2>
				<p class="mt-2">
					Wir können diese Datenschutzerklärung anpassen. Massgebend ist die jeweils auf dieser
					Seite veröffentlichte Fassung.
				</p>
			</section>
		{:else}
			<p class="text-[14px]">Last updated: 30 October 2026</p>

			<p>
				This privacy policy explains how personal data is processed in connection with the website
				teamorg.ch, the web app (app.teamorg.ch) and the teamorg apps for iOS and Android, in
				accordance with the Swiss Data Protection Act (revFADP) and, where applicable, the GDPR.
			</p>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">1. Controller</h2>
				<p class="mt-2">
					Michel Utke<br />
					Web: <a href="https://michelutke.com" rel="noopener" class="text-primary hover:underline"
						>michelutke.com</a
					><br />
					Contact: <a href="mailto:info@teamorg.ch" class="text-primary hover:underline"
						>info@teamorg.ch</a
					>
				</p>
				<p class="mt-3 text-[14px]">
					The content of this website is managed by Michel Utke. The postal address is available on
					request by email.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">2. Scope</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>Website:</strong> teamorg.ch (information and contact form).
					</li>
					<li>
						<strong>Web app:</strong> app.teamorg.ch (signing in and using teamorg in the browser).
					</li>
					<li>
						<strong>Mobile apps:</strong> the teamorg apps for iOS and Android.
					</li>
				</ul>
				<p class="mt-3">
					teamorg is a tool for sports clubs: clubs use it to organise their teams, events and attendance. The controller for the data described in this policy is the party named in section&nbsp;1.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">3. Contact form processing (website)</h2>
				<p class="mt-2">
					When you use the contact form, we process the data you provide: club name, contact
					person’s name, email address, optionally the number of members, and your message. The sole
					purpose is to handle and respond to your enquiry. The legal basis is your consent and/or
					our legitimate interest in communicating with prospective customers. Providing the data
					is voluntary; without it we cannot answer your request.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">4. Data processed when using the app and web app</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>Account:</strong> name (display name), email address and password. The password is stored only as a hash, never in plain text.
					</li>
					<li>
						<strong>Profile:</strong> optionally a profile picture.
					</li>
					<li>
						<strong>Membership:</strong> club, teams, subgroups and your role (club manager, coach or player).
					</li>
					<li>
						<strong>Events:</strong> trainings, games and events of your teams (title, time, place, notes).
					</li>
					<li>
						<strong>Attendance:</strong> your confirmations, declines and “unsure” replies with timestamp. A short reason is required for “unsure” and optional for declines. Coaches can add or correct attendance.
					</li>
					<li>
						<strong>Absences:</strong> absences you record (holidays, injury, work, school, travel, other) with a label, period or weekdays and, optionally, the affected body part.
					</li>
					<li>
						<strong>Notifications:</strong> your notification settings, reminders and the messages in the in-app inbox.
					</li>
					<li>
						<strong>Invitations:</strong> the email address of an invited person and the invitation link.
					</li>
					<li>
						<strong>Technical data:</strong> when you access our servers, technically necessary server logs are produced (incl. IP address, time, requested address).
					</li>
				</ul>
				<p class="mt-3">
					<strong>Note on health data:</strong> absences such as “injury” (with an optional body part) and reasons given when declining can contain information about your health, which is considered particularly sensitive. Providing it is voluntary. Please keep reasons brief and only state what is needed for planning (e.g. “injured”, no diagnoses). Reasons given for confirmations and declines are visible to the members of your team, and coaches and club managers also see the attendance overview (see section&nbsp;6).
				</p>
				<p class="mt-3">
					<strong>Data supplied by clubs:</strong> club managers can import member lists from the J+S database NDS (last name, first name, date of birth, person number, function) and, using their club’s API key, sync game schedules from Swiss Volley. The club decides which data it supplies.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">5. Purposes and legal bases</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>Running the app:</strong> account, teams, events, confirmations and declines, absences, reminders and billing. The legal basis is performance of the user relationship.
					</li>
					<li>
						<strong>Security and stability:</strong> protection against abuse, error analysis, server logs. The legal basis is our legitimate interest.
					</li>
					<li>
						<strong>Voluntary information:</strong> profile picture, absence reasons and push notifications. The legal basis is your consent, which you can withdraw at any time.
					</li>
					<li>
						<strong>Legal obligations:</strong> e.g. retention obligations for invoice data.
					</li>
				</ul>
				<p class="mt-3">
					We do not use your data for any other purpose.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">6. Who can see which data</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>You:</strong> all of your own data.
					</li>
					<li>
						<strong>Members of your teams:</strong> names, profile pictures and roles of team members, the team’s events, and the confirmations and declines of team members for those events, including the short reason given.
					</li>
					<li>
						<strong>Coaches:</strong> in addition, create and edit events, view, add and finalise the attendance of the players in their teams, and view the attendance overview of those players.
					</li>
					<li>
						<strong>Club managers:</strong> the members, teams, roles and invitations of their club and the club’s billing; for the teams in their club, the same access as coaches.
					</li>
					<li>
						<strong>Operator:</strong> technical access to the data for operations and support. Support access on behalf of an account is logged.
					</li>
				</ul>
				<p class="mt-3">
					A club’s data is not visible to other clubs. We do not pass your data on to third parties, except to the service providers listed in section&nbsp;9.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">7. Push notifications</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						Push notifications (e.g. an event reminder, a new or changed event) are only sent if you allow them. Your operating system asks you when you first launch the app.
					</li>
					<li>
						We use <strong>OneSignal</strong> (OneSignal Inc., USA) for this. OneSignal receives a device token, technical device and app information, a random identifier of your account and the content of the message (title and text).
					</li>
					<li>
						You can turn push notifications off at any time in your device’s system settings or in the app’s notification settings.
					</li>
				</ul>
				<p class="mt-3">
					The web app does not send push notifications.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">8. Payments</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						Clubs can subscribe to paid plans. Card payment is offered in the Android app and the web app, not in the iOS app.
					</li>
					<li>
						You enter card details directly with <strong>Stripe</strong> (Stripe Payments Europe, Ltd., Ireland, or Stripe, Inc., USA). They do not reach our servers.
					</li>
					<li>
						Stripe receives the billing email address and the club’s name. We store references to Stripe (customer and subscription identifiers), the billing email address, the card brand, the last four digits and expiry date of the card, and the club’s member count for billing.
					</li>
				</ul>
				<p class="mt-3">
					The legal basis is performance of the contract with the club.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">9. Processors & third parties</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>Email delivery (Infomaniak Network AG, Switzerland):</strong> your request is delivered by email to info@teamorg.ch and stored there.
					</li>
					<li>
						<strong>App email delivery (Infomaniak Network AG, Switzerland):</strong> invitations to teams and clubs are sent by email to the invited address. The address, the inviter’s name, the club or team and the invitation link are transmitted.
					</li>
					<li>
						<strong>Spam protection (Cloudflare Turnstile, Cloudflare Inc.):</strong> a security check runs when the contact form is submitted to prevent abuse. Technical data (incl. IP address, browser info) may be transmitted to Cloudflare.
					</li>
					<li>
						<strong>Hosting (Hetzner, Germany):</strong> the website, web app, server, database and uploaded files (e.g. profile pictures) run on a server at Hetzner in Nuremberg (Germany, EU), producing technically necessary server logs.
					</li>
					<li>
						<strong>Push notifications (OneSignal Inc., USA):</strong> see section&nbsp;7.
					</li>
					<li>
						<strong>Payments (Stripe Payments Europe, Ltd., Ireland, and Stripe, Inc., USA):</strong> see section&nbsp;8.
					</li>
					<li>
						<strong>Game schedule sync (Swiss Volley):</strong> only if a club sets up the sync, teamorg retrieves the club’s game schedule from Swiss Volley using the club’s API key.
					</li>
					<li>
						<strong>Fonts:</strong> fonts are served locally from teamorg.ch (self-hosted). No data is transmitted to third parties such as Google Fonts.
					</li>
				</ul>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">10. Disclosure abroad</h2>
				<p class="mt-2">
					The email provider Infomaniak processes the data in <strong>Switzerland</strong>;
					hosting is provided by Hetzner in <strong>Germany (EU)</strong>. Stripe Payments Europe is
					based in <strong>Ireland (EU)</strong>. OneSignal, Stripe, Inc. and Cloudflare are based in
					the <strong>USA</strong>; data may therefore be transferred to the USA (push data for
					OneSignal, payment data for Stripe, technical contact form data for Cloudflare). This
					transfer relies on the Swiss-U.S. Data Privacy Framework or on standard contractual clauses
					as a safeguard for an adequate level of data protection (Art.&nbsp;16 revFADP).
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">11. Cookies and local storage</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>Website:</strong> one functional cookie (<code>lang</code>) that stores your language choice.
					</li>
					<li>
						<strong>Web app:</strong> one technically necessary session cookie (<code>to_session</code>) that keeps you signed in (30 days, HTTPS only, not readable by scripts).
					</li>
					<li>
						<strong>Apps:</strong> your sign-in and, for offline use, events and replies are stored locally on your device.
					</li>
				</ul>
				<p class="mt-3">
					These cookies and local data are not used for analytics or advertising.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">12. No tracking, no advertising</h2>
				<p class="mt-2">
					teamorg uses no analytics or tracking services, no third-party crash reporting and no advertising. We do not sell personal data and do not share it for advertising purposes.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">13. Retention and deletion</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>Enquiries</strong> are kept for as long as needed to process them and to meet legal obligations, then deleted.
					</li>
					<li>
						<strong>App data</strong> is kept as long as your account exists. Messages in the in-app inbox are deleted automatically after 90 days.
					</li>
					<li>
						<strong>Invoice and payment data</strong> is kept for as long as legal retention obligations require.
					</li>
				</ul>
				<p class="mt-3">
					<strong>Deleting your account:</strong> in the app, open the “Profile” tab and tap “Delete account”. If you have no team yet, the same function is on the start screen. You then confirm with your password. In the web app, go to app.teamorg.ch/app/profile/delete.
				</p>
				<p class="mt-3">
					<strong>What happens:</strong> your attendance replies, absences, notifications and settings, reminders, team, group and club roles and your profile picture are deleted. Your account is anonymised: email address, name and password are replaced, the entry appears as “Gelöschtes Konto” (deleted account), and signing in is no longer possible. The anonymised entry remains because events and logs refer to it. Your email address is also removed from invitations. Existing sessions end immediately. Member lists imported by a club remain with the club.
				</p>
				<p class="mt-3">
					If you own a club, the club must be transferred or closed first. Write to us at <a href="mailto:info@teamorg.ch" class="text-primary hover:underline">info@teamorg.ch</a>.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">14. Children and young people</h2>
				<p class="mt-2">
					Clubs also use teamorg for youth teams. Accounts of minors should only be created with the consent of a parent or legal guardian. Clubs that invite young people make sure this consent exists. Parents and guardians can request access to or deletion of the account at any time: <a href="mailto:info@teamorg.ch" class="text-primary hover:underline">info@teamorg.ch</a>.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">15. Your rights</h2>
				<p class="mt-2">
					Subject to applicable law, you have the right to access, rectify, delete and restrict the
					processing of your personal data, and to withdraw consent. Contact <a
						href="mailto:info@teamorg.ch"
						class="text-primary hover:underline">info@teamorg.ch</a
					>. The competent authority in Switzerland is the FDPIC.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">16. Changes</h2>
				<p class="mt-2">
					We may update this privacy policy. The version published on this page applies.
				</p>
			</section>
		{/if}
	</div>
</article>
