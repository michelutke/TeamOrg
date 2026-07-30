<script lang="ts">
	import type { Dict } from '$lib/i18n';
	let { m }: { m: Dict['pricing'] } = $props();
</script>

<section id="preise" class="scroll-mt-20 bg-surface">
	<div class="mx-auto max-w-content px-6 py-20 md:px-10 md:py-24">
		<hr class="border-accent/25" />
		<div class="mt-10 grid gap-12 md:grid-cols-[1fr_480px] md:gap-20">
			<div>
				<span
					data-reveal
					class="inline-block rounded-full bg-primary-container px-3.5 py-1.5 text-[12px] font-bold tracking-[0.16em] text-on-primary-container"
					>{m.eyebrow}</span
				>
				<h2
					data-reveal
					style="--reveal-delay:60ms"
					class="font-display mt-5 text-[32px] font-extrabold leading-[1.1] tracking-tight text-on-surface md:text-[40px]"
				>
					{m.title}
				</h2>
				<div data-reveal style="--reveal-delay:120ms" class="mt-6 flex items-baseline gap-3">
					<span class="font-display text-[72px] font-extrabold tracking-tight text-accent md:text-[96px]"
						>{m.price}</span
					>
					<span class="text-[17px] font-medium text-on-surface">{m.per}</span>
				</div>
				<p class="mt-3 text-[15px] font-bold text-on-surface">{m.example}</p>
				<p class="mt-2 text-[13px] text-body">{m.fine}</p>
				<a
					href="https://app.teamorg.ch/start"
					class="mt-7 inline-block rounded-full bg-accent px-8 py-4 text-[15px] font-bold text-on-accent transition-transform duration-150 hover:scale-[1.02]"
					>{m.cta}</a
				>
			</div>
			<ul>
				<li class="pb-3 text-[12px] font-bold tracking-[0.18em] text-accent">{m.includesTitle}</li>
				{#each m.includes as item (item)}
					<li class="flex items-center gap-3.5 border-b border-on-surface/15 py-4">
						<span class="h-2.5 w-2.5 shrink-0 rounded-[3px] bg-accent"></span>
						<span class="text-[15px] font-medium text-on-surface">{item}</span>
					</li>
				{/each}
			</ul>
		</div>
	</div>
</section>
