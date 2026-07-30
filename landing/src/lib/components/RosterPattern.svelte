<script lang="ts">
	const PATTERN_COLS = 34;
	const X_PITCH = 48; const Y_PITCH = 52; const SQUARE = 20; const OFFSET = 24;

	let { intensity = 1, rows = 14, outline = false, uniformOpacity = 0.18, class: klass = '' }: {
		intensity?: number; rows?: number; outline?: boolean; uniformOpacity?: number; class?: string;
	} = $props();

	type Rect = { x: number; y: number; a: boolean; opacity: number };
	const rects: Rect[] = $derived(
		Array.from({ length: rows }, (_, row) => {
			const even = row % 2 === 0;
			const opacity = outline
				? uniformOpacity
				: +((0.08 + 0.42 * (0.5 + 0.5 * Math.sin(row * 0.7))) * intensity).toFixed(3);
			return Array.from({ length: PATTERN_COLS }, (_, col) => ({
				x: (even ? 0 : OFFSET) + col * X_PITCH,
				y: row * Y_PITCH,
				a: even,
				opacity
			}));
		}).flat()
	);
</script>

<svg
	class="pointer-events-none absolute inset-0 h-full w-full {klass}"
	viewBox="0 0 1632 {rows * Y_PITCH}"
	preserveAspectRatio="xMinYMin slice"
	aria-hidden="true"
>
	{#each rects as r (r.x + ',' + r.y)}
		<rect
			x={r.x} y={r.y} width={SQUARE} height={SQUARE} rx="6"
			fill={outline ? 'none' : `var(${r.a ? '--pattern-a' : '--pattern-b'})`}
			stroke={outline ? `var(${r.a ? '--pattern-a' : '--pattern-b'})` : 'none'}
			stroke-width={outline ? 2.5 : 0}
			opacity={r.opacity}
		/>
	{/each}
</svg>
