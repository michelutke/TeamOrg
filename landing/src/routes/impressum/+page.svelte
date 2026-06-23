<script lang="ts">
	import type { PageData } from './$types';
	let { data }: { data: PageData } = $props();
	const de = $derived(data.lang === 'de');
</script>

<svelte:head>
	<title>{data.m.legal.imprintTitle} · teamorg</title>
	<meta name="robots" content="noindex" />
</svelte:head>

<article class="mx-auto max-w-[760px] px-6 py-16 md:py-20">
	<a href="/" class="text-[14px] font-medium text-primary hover:underline">{data.m.legal.back}</a>
	<h1 class="font-display mt-6 text-[32px] font-extrabold tracking-tight text-on-surface md:text-[40px]">
		{data.m.legal.imprintTitle}
	</h1>

	<div class="mt-8 flex flex-col gap-7 text-[15px] leading-[1.65] text-on-surface-variant">
		{#if de}
			<section>
				<h2 class="text-[17px] font-bold text-on-surface">Verantwortlich für den Inhalt</h2>
				<p class="mt-2">Michel Utke</p>
				<p class="mt-3 text-[14px]">
					Die Inhalte dieser Website werden von Michel Utke verwaltet (<a
						href="https://michelutke.com"
						rel="noopener"
						class="text-primary hover:underline">michelutke.com</a
					>). teamorg.ch wird als Privatperson betrieben und ist nicht im Handelsregister eingetragen
					(keine Eintragungspflicht unter CHF&nbsp;100'000 Jahresumsatz). Die Postadresse wird auf
					Anfrage per E-Mail bekanntgegeben.
				</p>
			</section>
			<section>
				<h2 class="text-[17px] font-bold text-on-surface">Kontakt</h2>
				<p class="mt-2">
					E-Mail: <a href="mailto:info@teamorg.ch" class="text-primary hover:underline"
						>info@teamorg.ch</a
					><br />
					Web: teamorg.ch
				</p>
			</section>
			<section>
				<h2 class="text-[17px] font-bold text-on-surface">Haftungsausschluss</h2>
				<p class="mt-2">
					Die Inhalte dieser Website wurden mit grösstmöglicher Sorgfalt erstellt. Für die
					Richtigkeit, Vollständigkeit und Aktualität der Inhalte wird jedoch keine Gewähr
					übernommen. Für Inhalte externer Links sind ausschliesslich deren Betreiber
					verantwortlich.
				</p>
			</section>
			<section>
				<h2 class="text-[17px] font-bold text-on-surface">Urheberrecht</h2>
				<p class="mt-2">
					Sämtliche Inhalte dieser Website (Texte, Bilder, Grafiken, Logos) sind urheberrechtlich
					geschützt. Jede Verwendung ausserhalb der gesetzlichen Schranken bedarf der vorherigen
					schriftlichen Zustimmung.
				</p>
			</section>
		{:else}
			<section>
				<h2 class="text-[17px] font-bold text-on-surface">Responsible for content</h2>
				<p class="mt-2">Michel Utke</p>
				<p class="mt-3 text-[14px]">
					The content of this website is managed by Michel Utke (<a
						href="https://michelutke.com"
						rel="noopener"
						class="text-primary hover:underline">michelutke.com</a
					>). teamorg.ch is operated by a private individual and is not entered in the commercial
					register (no registration obligation below CHF&nbsp;100,000 annual turnover). The postal
					address is available on request by email.
				</p>
			</section>
			<section>
				<h2 class="text-[17px] font-bold text-on-surface">Contact</h2>
				<p class="mt-2">
					Email: <a href="mailto:info@teamorg.ch" class="text-primary hover:underline"
						>info@teamorg.ch</a
					><br />
					Web: teamorg.ch
				</p>
			</section>
			<section>
				<h2 class="text-[17px] font-bold text-on-surface">Disclaimer</h2>
				<p class="mt-2">
					The content of this website has been compiled with the greatest possible care. However,
					no guarantee is given for the accuracy, completeness or timeliness of the content.
					Operators of external linked sites are solely responsible for their content.
				</p>
			</section>
			<section>
				<h2 class="text-[17px] font-bold text-on-surface">Copyright</h2>
				<p class="mt-2">
					All content on this website (text, images, graphics, logos) is protected by copyright.
					Any use beyond the limits of copyright law requires prior written consent.
				</p>
			</section>
		{/if}
	</div>
</article>
