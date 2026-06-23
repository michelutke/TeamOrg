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
			<p>
				Diese Datenschutzerklärung informiert über die Bearbeitung von Personendaten im Zusammenhang
				mit der Website teamorg.ch, gemäss dem schweizerischen Datenschutzgesetz (revDSG) und –
				soweit anwendbar – der DSGVO.
			</p>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">1. Verantwortliche Stelle</h2>
				<p class="mt-2">
					[Vorname Nachname], [Strasse Nr.], [PLZ Ort], Schweiz.<br />
					Kontakt: <a href="mailto:info@teamorg.ch" class="text-primary hover:underline"
						>info@teamorg.ch</a
					>
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">2. Bearbeitung beim Kontaktformular</h2>
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
				<h2 class="text-[17px] font-bold text-on-surface">3. Auftragsbearbeiter & Drittdienste</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>E-Mail-Versand (Proton Mail / Proton AG, Schweiz):</strong> Deine Anfrage wird per
						E-Mail an info@teamorg.ch zugestellt und dort gespeichert.
					</li>
					<li>
						<strong>Spam-Schutz (Cloudflare Turnstile, Cloudflare Inc.):</strong> Zur Abwehr von
						Missbrauch wird beim Absenden eine Sicherheitsprüfung durchgeführt. Dabei können technische
						Daten (u. a. IP-Adresse, Browserinformationen) an Cloudflare übermittelt werden.
					</li>
					<li>
						<strong>Hosting:</strong> Die Website wird auf einem Server in [Standort/Provider, z. B.
						Schweiz/EU] betrieben. Dabei fallen technisch notwendige Server-Logs an.
					</li>
					<li>
						<strong>Schriftarten (Google Fonts, Google – USA):</strong> Die Schriften werden von
						Servern von Google geladen, wobei deine IP-Adresse übermittelt wird. [Optional: künftig
						selbst gehostet, um diese Übermittlung zu vermeiden.]
					</li>
				</ul>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">4. Bekanntgabe ins Ausland</h2>
				<p class="mt-2">
					Proton und Infomaniak verarbeiten die Daten in der <strong>Schweiz</strong>. Cloudflare und
					Google haben ihren Sitz in den <strong>USA</strong>; dabei können Personendaten in die USA
					übermittelt werden. Die Übermittlung stützt sich auf das Swiss-U.S. Data Privacy Framework
					bzw. auf Standardvertragsklauseln als Garantie für ein angemessenes Datenschutzniveau
					(Art.&nbsp;16 revDSG).
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">5. Cookies</h2>
				<p class="mt-2">
					Wir setzen lediglich ein funktionales Cookie (<code>lang</code>), das deine
					Sprachauswahl speichert. Es dient nicht der Analyse oder Werbung. Tracking- oder
					Analyse-Cookies werden nicht eingesetzt.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">6. Aufbewahrung</h2>
				<p class="mt-2">
					Anfragen werden so lange aufbewahrt, wie es zur Bearbeitung sowie zur Erfüllung
					gesetzlicher Pflichten erforderlich ist, und anschliessend gelöscht.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">7. Deine Rechte</h2>
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
				<h2 class="text-[17px] font-bold text-on-surface">8. Änderungen</h2>
				<p class="mt-2">
					Wir können diese Datenschutzerklärung anpassen. Massgebend ist die jeweils auf dieser
					Seite veröffentlichte Fassung.
				</p>
			</section>
		{:else}
			<p>
				This privacy policy explains how personal data is processed in connection with the website
				teamorg.ch, in accordance with the Swiss Data Protection Act (revFADP) and, where
				applicable, the GDPR.
			</p>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">1. Controller</h2>
				<p class="mt-2">
					[Name / Company], [Street No.], [Postal code City], Switzerland.<br />
					Contact: <a href="mailto:info@teamorg.ch" class="text-primary hover:underline"
						>info@teamorg.ch</a
					>
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">2. Contact form processing</h2>
				<p class="mt-2">
					When you use the contact form, we process the data you provide: club name, contact
					person’s name, email address, optionally the number of members, and your message. The sole
					purpose is to handle and respond to your enquiry. The legal basis is your consent and/or
					our legitimate interest in communicating with prospective customers. Providing the data
					is voluntary; without it we cannot answer your request.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">3. Processors & third parties</h2>
				<ul class="mt-2 flex list-disc flex-col gap-2 pl-5">
					<li>
						<strong>Email delivery (Proton Mail / Proton AG, Switzerland):</strong> your request is
						delivered by email to info@teamorg.ch and stored there.
					</li>
					<li>
						<strong>Spam protection (Cloudflare Turnstile, Cloudflare Inc.):</strong> a security check
						runs on submission to prevent abuse. Technical data (incl. IP address, browser info) may
						be transmitted to Cloudflare.
					</li>
					<li>
						<strong>Hosting:</strong> the website runs on a server in [location/provider, e.g.
						Switzerland/EU], producing technically necessary server logs.
					</li>
					<li>
						<strong>Fonts (Google Fonts, Google – USA):</strong> fonts are loaded from Google
						servers, which transmits your IP address. [Optional: to be self-hosted in future to
						avoid this.]
					</li>
				</ul>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">4. Disclosure abroad</h2>
				<p class="mt-2">
					Proton and Infomaniak process the data in <strong>Switzerland</strong>. Cloudflare and
					Google are based in the <strong>USA</strong>; personal data may therefore be transferred to
					the USA. The transfer relies on the Swiss-U.S. Data Privacy Framework or on standard
					contractual clauses as a safeguard for an adequate level of data protection
					(Art.&nbsp;16 revFADP).
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">5. Cookies</h2>
				<p class="mt-2">
					We only set one functional cookie (<code>lang</code>) that stores your language choice. It
					is not used for analytics or advertising. No tracking or analytics cookies are used.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">6. Retention</h2>
				<p class="mt-2">
					Enquiries are kept for as long as needed to process them and to meet legal obligations,
					then deleted.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">7. Your rights</h2>
				<p class="mt-2">
					Subject to applicable law, you have the right to access, rectify, delete and restrict the
					processing of your personal data, and to withdraw consent. Contact <a
						href="mailto:info@teamorg.ch"
						class="text-primary hover:underline">info@teamorg.ch</a
					>. The competent authority in Switzerland is the FDPIC.
				</p>
			</section>

			<section>
				<h2 class="text-[17px] font-bold text-on-surface">8. Changes</h2>
				<p class="mt-2">
					We may update this privacy policy. The version published on this page applies.
				</p>
			</section>
		{/if}
	</div>
</article>
