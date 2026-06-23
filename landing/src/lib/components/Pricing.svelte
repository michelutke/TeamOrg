<script lang="ts">
	import { Check, Lightbulb } from 'lucide-svelte';
	import type { Dict } from '$lib/i18n';

	let { m }: { m: Dict['pricing'] } = $props();
</script>

<section id="preise" class="scroll-mt-20 bg-surface-low">
	<div class="mx-auto max-w-content px-6 py-20 md:px-10 md:py-24">
		<div class="mx-auto max-w-[760px] text-center">
			<span
				data-reveal
				class="inline-block rounded-full bg-primary-container px-3.5 py-1.5 text-[12px] font-bold tracking-[0.16em] text-on-primary-container"
				>{m.eyebrow}</span
			>
			<h2
				data-reveal
				style="--reveal-delay:60ms"
				class="font-display mt-4 text-[28px] font-extrabold leading-[1.12] tracking-tight text-on-surface md:text-[40px]"
			>
				{m.title}
			</h2>
			<p
				data-reveal
				style="--reveal-delay:120ms"
				class="mx-auto mt-4 max-w-[560px] text-[15px] leading-[1.5] text-on-surface-variant md:text-[17px]"
			>
				{m.sub}
			</p>
		</div>

		<div
			data-reveal
			style="--reveal-delay:120ms"
			class="mx-auto mt-12 w-full max-w-[560px] rounded-[2rem] border-[1.5px] border-primary bg-surface p-8 shadow-[0_24px_56px_-12px_rgba(40,25,90,0.14)] md:p-10"
		>
			<p class="text-[13px] font-bold tracking-[0.12em] text-on-surface-variant">{m.planLabel}</p>
			<div class="mt-2 flex items-baseline gap-2.5">
				<span class="font-display text-[48px] font-extrabold tracking-tight text-primary md:text-[60px]"
					>{m.price}</span
				>
				<span class="text-[16px] font-medium text-on-surface-variant md:text-[18px]">{m.per}</span>
			</div>
			<p class="mt-3 max-w-[480px] text-[15px] leading-[1.5] text-on-surface-variant">{m.fine}</p>

			<div
				class="mt-5 inline-flex items-center gap-2 rounded-full bg-primary-container px-3.5 py-2 text-[13px] font-bold text-on-primary-container"
			>
				<Lightbulb class="h-4 w-4" strokeWidth={2} />
				{m.example}
			</div>

			<hr class="my-7 border-outline-variant" />

			<ul class="flex flex-col gap-3.5">
				{#each m.includes as item (item)}
					<li class="flex items-center gap-3">
						<span
							class="flex h-[22px] w-[22px] shrink-0 items-center justify-center rounded-full bg-accent-green"
						>
							<Check class="h-3.5 w-3.5 text-accent-green-on" strokeWidth={3} />
						</span>
						<span class="text-[15px] font-medium text-on-surface">{item}</span>
					</li>
				{/each}
			</ul>

			<a
				href="#kontakt"
				class="mt-8 flex items-center justify-center rounded-full bg-primary py-4 text-[16px] font-bold text-on-primary transition-transform duration-150 hover:scale-[1.02] active:scale-[0.99]"
				>{m.cta}</a
			>
			<p class="mt-4 text-[13px] leading-[1.4] text-on-surface-variant">{m.footnote}</p>
		</div>
	</div>
</section>
