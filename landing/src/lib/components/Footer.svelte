<script lang="ts">
	import { Check } from 'lucide-svelte';
	import type { Dict, Locale } from '$lib/i18n';

	let { m, lang }: { m: Dict['footer']; lang: Locale } = $props();
</script>

<footer class="bg-inverse-surface">
	<div class="mx-auto max-w-content px-6 py-14 md:px-10">
		<div class="flex flex-col justify-between gap-10 md:flex-row">
			<!-- Brand -->
			<div class="max-w-[300px]">
				<div class="flex items-center gap-2.5">
					<span class="flex h-[34px] w-[34px] items-center justify-center rounded-[10px] bg-primary">
						<Check class="h-5 w-5 text-on-primary" strokeWidth={3} />
					</span>
					<span class="font-display text-[22px] font-extrabold tracking-tight text-white"
						>teamorg</span
					>
				</div>
				<p class="mt-4 text-[14px] leading-[1.5] text-white/70">{m.tagline}</p>
				<p class="mt-3 text-[13px] font-medium text-white/55">{m.madeIn}</p>
			</div>

			<!-- Link columns -->
			<div class="flex gap-12 md:gap-[72px]">
				<div class="flex flex-col gap-3.5">
					<p class="text-[13px] font-bold tracking-[0.12em] text-white/55">{m.colProduct}</p>
					<a href="/#funktionen" class="text-[14px] font-medium text-white/90 hover:text-white"
						>{m.links.features}</a
					>
					<a href="/#preise" class="text-[14px] font-medium text-white/90 hover:text-white"
						>{m.links.pricing}</a
					>
					<a href="/#kontakt" class="text-[14px] font-medium text-white/90 hover:text-white"
						>{m.links.contact}</a
					>
				</div>
				<div class="flex flex-col gap-3.5">
					<p class="text-[13px] font-bold tracking-[0.12em] text-white/55">{m.colLegal}</p>
					<a href="/datenschutz" class="text-[14px] font-medium text-white/90 hover:text-white"
						>{m.legal.privacy}</a
					>
					<a href="/impressum" class="text-[14px] font-medium text-white/90 hover:text-white"
						>{m.legal.imprint}</a
					>
				</div>
				<div class="flex flex-col gap-3.5">
					<p class="text-[13px] font-bold tracking-[0.12em] text-white/55">{m.colContact}</p>
					<a
						href="mailto:info@teamorg.ch"
						class="text-[14px] font-medium text-white/90 hover:text-white">info@teamorg.ch</a
					>
				</div>
			</div>
		</div>

		<hr class="my-9 border-white/15" />

		<div class="flex flex-col justify-between gap-3 text-[13px] text-white/55 sm:flex-row">
			<span>{m.rights}</span>
			<span>ch.teamorg · {lang.toUpperCase()}</span>
		</div>
	</div>
</footer>
