<script lang="ts">
	import type { Dict } from '$lib/i18n';

	let { m }: { m: Dict['how'] } = $props();

	const accents = [
		{ chip: 'bg-accent-green', on: 'text-accent-green-on' },
		{ chip: 'bg-accent-yellow', on: 'text-accent-yellow-on' },
		{ chip: 'bg-accent-red', on: 'text-accent-red-on' }
	];
</script>

<section class="bg-surface">
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

		<div class="mx-auto mt-12 grid max-w-[1080px] gap-8 md:mt-14 md:grid-cols-3 md:gap-7">
			{#each m.steps as step, i (step.num)}
				{@const accent = accents[i % 3]}
				<div data-reveal style="--reveal-delay:{i * 90}ms" class="flex flex-col items-start">
					<span
						class="font-display flex h-[60px] w-[60px] items-center justify-center rounded-full {accent.chip} text-[22px] font-extrabold {accent.on}"
						>{step.num}</span
					>
					<h3 class="font-display mt-5 text-[22px] font-extrabold tracking-tight text-on-surface">
						{step.title}
					</h3>
					<p class="mt-2 text-[15px] leading-[1.52] text-on-surface-variant">{step.body}</p>
				</div>
			{/each}
		</div>
	</div>
</section>
