<script lang="ts">
	import { Calendar, CircleCheck, CalendarX, Bell, Users, BarChart3 } from 'lucide-svelte';
	import Shape, { type ShapeName } from './Shape.svelte';
	import type { Dict, Feature } from '$lib/i18n';

	let { m }: { m: Dict['features'] } = $props();

	const icons: Record<Feature['icon'], typeof Calendar> = {
		calendar: Calendar,
		check: CircleCheck,
		'calendar-x': CalendarX,
		bell: Bell,
		users: Users,
		'bar-chart': BarChart3
	};

	// Rotating pastel accents: green / yellow / red (the app's status colors).
	const accents = [
		{ fill: 'text-accent-green', on: 'text-accent-green-on' },
		{ fill: 'text-accent-yellow', on: 'text-accent-yellow-on' },
		{ fill: 'text-accent-red', on: 'text-accent-red-on' }
	];
	// Varied Material 3 Expressive shapes per card.
	const shapes: ShapeName[] = ['clover', 'flower', 'sunny', 'squircle', 'flower', 'clover'];
</script>

<section id="funktionen" class="scroll-mt-20 bg-surface-low">
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
				class="mx-auto mt-4 max-w-[620px] text-[15px] leading-[1.5] text-on-surface-variant md:text-[17px]"
			>
				{m.sub}
			</p>
		</div>

		<div class="mt-12 grid gap-5 md:mt-14 md:grid-cols-3">
			{#each m.items as item, i (item.title)}
				{@const Icon = icons[item.icon]}
				{@const accent = accents[i % 3]}
				<div
					data-reveal
					style="--reveal-delay:{(i % 3) * 70}ms"
					class="group rounded-3xl border border-outline-variant bg-surface p-7 transition-all duration-200 hover:-translate-y-1 hover:border-primary hover:shadow-[0_16px_40px_-16px_rgba(40,25,90,0.18)]"
				>
					<span
						class="relative flex h-[54px] w-[54px] items-center justify-center transition-transform duration-200 group-hover:scale-105"
					>
						<Shape name={shapes[i]} class="absolute inset-0 h-full w-full {accent.fill}" />
						<Icon class="relative h-[26px] w-[26px] {accent.on}" strokeWidth={2} />
					</span>
					<h3 class="font-display mt-4 text-[19px] font-extrabold tracking-tight text-on-surface">
						{item.title}
					</h3>
					<p class="mt-2 text-[15px] leading-[1.48] text-on-surface-variant">{item.body}</p>
				</div>
			{/each}
		</div>
	</div>
</section>
