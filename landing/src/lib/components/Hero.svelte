<script lang="ts">
	import { Check } from 'lucide-svelte';
	import PhoneMockup from './PhoneMockup.svelte';
	import RosterPattern from './RosterPattern.svelte';
	import type { Dict } from '$lib/i18n';

	const eventsImg = '/mockups/events.png';
	const detailImg = '/mockups/event-detail.png';

	let { m }: { m: Dict['hero'] } = $props();
</script>

<section
	class="relative overflow-hidden bg-[#181C23]"
	style="background-image: linear-gradient(to bottom right, #2C313B, #181C23);"
>
	<RosterPattern />

	<div
		class="relative mx-auto flex max-w-content flex-col items-center gap-10 px-6 pb-16 pt-12 md:flex-row md:px-10 md:pb-20 md:pt-16"
	>
		<!-- Copy -->
		<div class="w-full md:max-w-[560px]">
			<span
				data-reveal
				class="inline-flex items-center gap-2 rounded-full bg-primary-container px-3.5 py-1.5 text-[13px] font-bold text-on-primary-container"
			>
				<span class="h-2 w-2 rounded-full bg-primary"></span>
				{m.eyebrow}
			</span>

			<h1
				data-reveal
				style="--reveal-delay:60ms"
				class="font-display mt-5 text-[34px] font-extrabold leading-[1.05] tracking-tight text-[#E2E4E8] md:text-[52px]"
			>
				{m.headlineA}<br /><span class="text-[#64D8E8]">{m.headlineB}</span>
			</h1>

			<p
				data-reveal
				style="--reveal-delay:120ms"
				class="mt-5 max-w-[520px] text-[17px] leading-[1.5] text-[#9AA3AD] md:text-[18px]"
			>
				{m.sub}
			</p>

			<div data-reveal style="--reveal-delay:180ms" class="mt-7 flex flex-wrap gap-3.5">
				<a
					href="https://app.teamorg.ch/start"
					class="rounded-full bg-[#64D8E8] px-7 py-4 text-[15px] font-bold text-[#00363F] shadow-sm transition-transform duration-150 hover:scale-[1.03] active:scale-[0.98]"
					>{m.ctaPrimary}</a
				>
				<a
					href="#funktionen"
					class="rounded-full border-[1.5px] border-[#E2E4E8] px-7 py-4 text-[15px] font-bold text-[#E2E4E8] transition-colors hover:bg-white/5"
					>{m.ctaSecondary}</a
				>
			</div>

			<ul data-reveal style="--reveal-delay:240ms" class="mt-7 flex flex-wrap gap-x-6 gap-y-2.5">
				{#each m.trust as item (item)}
					<li class="flex items-center gap-2 text-[13px] font-medium text-[#9AA3AD]">
						<Check class="h-4 w-4 text-[#64D8E8]" strokeWidth={2.5} />
						{item}
					</li>
				{/each}
			</ul>
		</div>

		<!-- Device stage -->
		<div class="relative flex w-full flex-1 items-center justify-center md:justify-end">
			<div
				class="pointer-events-none absolute left-1/2 top-1/2 h-[360px] w-[360px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#64D8E8]/25 blur-[90px]"
			></div>

			<!-- Desktop: two phones side by side, horizontally overlapping -->
			<div class="relative hidden items-center md:flex">
				<PhoneMockup
					src={eventsImg}
					alt="teamorg Events"
					width={290}
					float
					class="relative z-10 -mr-12"
				/>
				<PhoneMockup
					src={detailImg}
					alt="teamorg Event Detail"
					width={262}
					class="relative z-0 mt-14 rotate-[4deg]"
				/>
			</div>

			<!-- Mobile: single phone -->
			<div class="relative z-10 md:hidden">
				<PhoneMockup src={eventsImg} alt="teamorg Events" width={270} float />
			</div>
		</div>
	</div>
</section>
