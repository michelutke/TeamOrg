<script lang="ts">
	// "Tribüne" decorative pattern — rows of small rounded squares, alternating
	// offset and color, opacity easing smoothly between rows. Extracted from Hero
	// so sections can reuse it; `intensity` scales all square opacities (1 = hero look).
	const PATTERN_COLS = 34;
	const PATTERN_ROWS = 14;
	const X_PITCH = 48;
	const Y_PITCH = 52;
	const SQUARE = 20;
	const OFFSET = 24;

	let { intensity = 1, class: klass = '' }: { intensity?: number; class?: string } = $props();

	type PatternRect = { x: number; y: number; color: string; opacity: number };

	const patternRects: PatternRect[] = $derived(
		Array.from({ length: PATTERN_ROWS }, (_, row) => {
			const isEven = row % 2 === 0;
			const color = isEven ? '#64D8E8' : '#E2E4E8';
			const xOffset = isEven ? 0 : OFFSET;
			const opacity = +(
				(0.08 + 0.42 * (0.5 + 0.5 * Math.sin(row * 0.7))) *
				intensity
			).toFixed(3);
			return Array.from({ length: PATTERN_COLS }, (_, col) => ({
				x: xOffset + col * X_PITCH,
				y: row * Y_PITCH,
				color,
				opacity
			}));
		}).flat()
	);
</script>

<svg
	class="pointer-events-none absolute inset-0 h-full w-full {klass}"
	viewBox="0 0 1632 728"
	preserveAspectRatio="xMidYMid slice"
	aria-hidden="true"
>
	{#each patternRects as r (r.x + ',' + r.y)}
		<rect x={r.x} y={r.y} width={SQUARE} height={SQUARE} rx="6" fill={r.color} opacity={r.opacity} />
	{/each}
</svg>
