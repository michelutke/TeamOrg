<script lang="ts">
	// Exact P2 "T-Tessellation" lattice from the brand board:
	// band A up(6+144k, 6+144r) / down(142+144k, 70+144r),
	// band B up(78+144k, 78+144r) / down(70+144k, 142+144r),
	// opacity cycle [0.55,0.3,0.4,0.5,0.35,0.45].
	const OPS = [0.55, 0.3, 0.4, 0.5, 0.35, 0.45];
	const WIDTH = 1728; // 12 cols × 144, clipped by parent

	let { rows = 6, opacityScale = 0.35, class: klass = '' }: {
		rows?: number; opacityScale?: number; class?: string;
	} = $props();

	type T = { x: number; y: number; rot: 0 | 180; opacity: number };
	const tiles: T[] = $derived.by(() => {
		const out: T[] = [];
		let i = 0;
		const cols = Math.ceil(WIDTH / 144);
		for (let r = 0; r < rows; r++) {
			const y = 6 + 144 * r;
			for (let k = 0; k < cols; k++) {
				out.push({ x: 6 + 144 * k, y, rot: 0, opacity: OPS[i++ % 6] * opacityScale });
				out.push({ x: 142 + 144 * k, y: y + 64, rot: 180, opacity: OPS[i++ % 6] * opacityScale });
			}
			for (let k = 0; k < cols; k++) {
				out.push({ x: 70 + 144 * k, y: y + 136, rot: 180, opacity: OPS[i++ % 6] * opacityScale });
				out.push({ x: 78 + 144 * k, y: y + 72, rot: 0, opacity: OPS[i++ % 6] * opacityScale });
			}
		}
		return out;
	});
</script>

<svg
	class="pointer-events-none absolute inset-0 h-full w-full {klass}"
	viewBox="0 0 {WIDTH} {rows * 144 + 12}"
	preserveAspectRatio="xMinYMin slice"
	aria-hidden="true"
	style="color: var(--pattern-a)"
>
	{#each tiles as t (t.x + ',' + t.y + ',' + t.rot)}
		<g transform="translate({t.x},{t.y}) rotate({t.rot},32,32)" opacity={t.opacity}>
			<rect x="0" y="0" width="18" height="18" rx="5.5" fill="none" stroke="currentColor" stroke-width="2.5" />
			<rect x="23" y="0" width="18" height="18" rx="5.5" fill="none" stroke="currentColor" stroke-width="2.5" />
			<rect x="46" y="0" width="18" height="18" rx="5.5" fill="none" stroke="currentColor" stroke-width="2.5" />
			<rect x="23" y="23" width="18" height="41" rx="5.5" fill="none" stroke="currentColor" stroke-width="2.5" />
		</g>
	{/each}
</svg>
