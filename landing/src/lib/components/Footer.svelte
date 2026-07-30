<script lang="ts">
	import LogoMark from './LogoMark.svelte';
	import type { Dict, Locale } from '$lib/i18n';

	let { m, lang }: { m: Dict['footer']; lang: Locale } = $props();
</script>

<footer class="relative">
	<div class="absolute inset-0" style="background: var(--color-surface); opacity: 0.62"></div>
	<div class="relative mx-auto max-w-content px-6 py-14 md:px-10">
		<div class="flex flex-col justify-between gap-10 md:flex-row">
			<!-- Brand -->
			<div class="max-w-[300px]">
				<div class="flex items-center gap-2.5">
					<LogoMark size={34} blocks="var(--color-accent)" stem="var(--color-on-surface)" />
					<span class="font-display text-[22px] font-extrabold tracking-tight"
						><span class="text-on-surface">Team</span><span class="text-accent">Org</span></span
					>
				</div>
				<p class="mt-4 text-[14px] leading-[1.5] text-body">{m.tagline}</p>
				<p class="mt-3 text-[13px] font-medium text-placeholder">{m.madeIn}</p>
			</div>

			<!-- Link columns -->
			<div class="flex gap-12 md:gap-[72px]">
				<div class="flex flex-col gap-3.5">
					<p class="text-[13px] font-bold tracking-[0.12em] text-accent">{m.colProduct}</p>
					<a href="/#funktionen" class="text-[14px] font-medium text-on-surface hover:text-accent"
						>{m.links.features}</a
					>
					<a href="/#preise" class="text-[14px] font-medium text-on-surface hover:text-accent"
						>{m.links.pricing}</a
					>
					<a href="/#kontakt" class="text-[14px] font-medium text-on-surface hover:text-accent"
						>{m.links.contact}</a
					>
				</div>
				<div class="flex flex-col gap-3.5">
					<p class="text-[13px] font-bold tracking-[0.12em] text-accent">{m.colLegal}</p>
					<a href="/datenschutz" class="text-[14px] font-medium text-on-surface hover:text-accent"
						>{m.legal.privacy}</a
					>
					<a href="/impressum" class="text-[14px] font-medium text-on-surface hover:text-accent"
						>{m.legal.imprint}</a
					>
				</div>
				<div class="flex flex-col gap-3.5">
					<p class="text-[13px] font-bold tracking-[0.12em] text-accent">{m.colContact}</p>
					<a
						href="mailto:info@teamorg.ch"
						class="text-[14px] font-medium text-on-surface hover:text-accent">info@teamorg.ch</a
					>
				</div>
			</div>
		</div>

		<hr class="my-9 border-outline-variant" />

		<div class="flex flex-col justify-between gap-3 text-[13px] text-placeholder sm:flex-row">
			<span>{m.rights}</span>
			<span>ch.teamorg · {lang.toUpperCase()}</span>
		</div>
	</div>
</footer>
