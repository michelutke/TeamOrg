<script module lang="ts">
	// Smooth closed Bezier blob through N points whose radius is modulated by a
	// cosine -> a Material 3 Expressive "cookie"/"clover" shape. Both shapes use
	// the SAME point count, so their path commands line up and SMIL can morph
	// `d` between them (Compose's Morph between two RoundedPolygons, on the web).
	const N = 24;
	const BASE = 38;

	function points(amp: number, freq: number): [number, number][] {
		return Array.from({ length: N }, (_, i) => {
			const t = (i / N) * Math.PI * 2;
			const r = BASE * (1 + amp * Math.cos(freq * t));
			return [+(Math.cos(t) * r).toFixed(2), +(Math.sin(t) * r).toFixed(2)];
		});
	}

	// Catmull-Rom -> cubic Bezier, closed.
	function smooth(p: [number, number][]): string {
		const n = p.length;
		let d = `M${p[0][0]},${p[0][1]}`;
		for (let i = 0; i < n; i++) {
			const p0 = p[(i - 1 + n) % n];
			const p1 = p[i];
			const p2 = p[(i + 1) % n];
			const p3 = p[(i + 2) % n];
			const c1x = (p1[0] + (p2[0] - p0[0]) / 6).toFixed(2);
			const c1y = (p1[1] + (p2[1] - p0[1]) / 6).toFixed(2);
			const c2x = (p2[0] - (p3[0] - p1[0]) / 6).toFixed(2);
			const c2y = (p2[1] - (p3[1] - p1[1]) / 6).toFixed(2);
			d += `C${c1x},${c1y} ${c2x},${c2y} ${p2[0]},${p2[1]}`;
		}
		return d + 'Z';
	}

	const cookie = smooth(points(0.1, 8)); // 8 soft scallops
	const clover = smooth(points(0.2, 4)); // 4 deep petals
	const morph = `${cookie};${clover};${cookie}`;
</script>

<script lang="ts">
	import { onMount } from 'svelte';

	let { class: klass = '', dur = 6 }: { class?: string; dur?: number } = $props();

	let svg: SVGSVGElement;

	onMount(() => {
		// SMIL ignores prefers-reduced-motion; freeze it ourselves.
		if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
			svg.pauseAnimations();
		}
	});
</script>

<svg
	bind:this={svg}
	viewBox="-50 -50 100 100"
	class={klass}
	fill="currentColor"
	aria-hidden="true"
>
	<g>
		<path d={cookie}>
			<animate
				attributeName="d"
				values={morph}
				dur="{dur}s"
				calcMode="spline"
				keyTimes="0;0.5;1"
				keySplines="0.4 0 0.2 1;0.4 0 0.2 1"
				repeatCount="indefinite"
			/>
		</path>
		<animateTransform
			attributeName="transform"
			type="rotate"
			from="0"
			to="360"
			dur="{dur * 4}s"
			repeatCount="indefinite"
		/>
	</g>
</svg>
