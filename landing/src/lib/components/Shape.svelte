<script module lang="ts">
	export type ShapeName = 'clover' | 'flower' | 'sunny' | 'squircle';
</script>

<script lang="ts">
	// Material 3 Expressive-style shapes for icon backgrounds.
	// Composed from overlapping primitives (same fill = seamless union) so they stay
	// crisp at any size. Fill follows `currentColor`, so the parent sets the colour
	// via a text-* class.
	let { name, class: klass = '' }: { name: ShapeName; class?: string } = $props();

	// Evenly-spaced lobes around the centre (viewBox is centered at 0,0).
	function ring(count: number, radius: number, lobe: number) {
		return Array.from({ length: count }, (_, i) => {
			const a = (i / count) * Math.PI * 2 - Math.PI / 2;
			return {
				cx: +(Math.cos(a) * radius).toFixed(2),
				cy: +(Math.sin(a) * radius).toFixed(2),
				r: lobe
			};
		});
	}
	const flower = ring(7, 25, 20);
	const sunny = ring(11, 31, 10);
</script>

<svg viewBox="-50 -50 100 100" class={klass} fill="currentColor" aria-hidden="true">
	{#if name === 'clover'}
		<circle cx="0" cy="-20" r="28" />
		<circle cx="0" cy="20" r="28" />
		<circle cx="-20" cy="0" r="28" />
		<circle cx="20" cy="0" r="28" />
	{:else if name === 'flower'}
		<circle cx="0" cy="0" r="30" />
		{#each flower as p (p.cx + ',' + p.cy)}
			<circle cx={p.cx} cy={p.cy} r={p.r} />
		{/each}
	{:else if name === 'sunny'}
		<circle cx="0" cy="0" r="34" />
		{#each sunny as p (p.cx + ',' + p.cy)}
			<circle cx={p.cx} cy={p.cy} r={p.r} />
		{/each}
	{:else}
		<rect x="-44" y="-44" width="88" height="88" rx="30" />
	{/if}
</svg>
