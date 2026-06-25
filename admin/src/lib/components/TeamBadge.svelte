<script lang="ts">
	import type { TeamAppearance } from '$lib/server/teams';

	interface Props {
		name: string;
		appearance?: TeamAppearance | null;
		size?: number;
	}

	let { name, appearance = null, size = 40 }: Props = $props();

	// Fallback palette (M3 container tones) keyed deterministically off the name so a
	// team without an explicit appearance still gets a stable, non-random color.
	const palette = ['#EADDFF', '#FFD8E4', '#E8DEF8', '#C8E6C9', '#FFE0B2', '#B3E5FC'];
	function hash(s: string): number {
		let h = 0;
		for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
		return Math.abs(h);
	}

	// Shape → border-radius preset (approximation of the M3 Expressive shape set).
	const radii: Record<string, string> = {
		cookie: '42% 58% 53% 47% / 47% 42% 58% 53%',
		clover: '60% 40% 55% 45% / 45% 55% 45% 55%',
		sunny: '38%',
		flower: '50%'
	};

	const color = $derived(appearance?.color ?? palette[hash(name) % palette.length]);
	const borderRadius = $derived(appearance ? (radii[appearance.shape] ?? '38%') : '38%');
	const initials = $derived(
		name
			.split(/\s+/)
			.slice(0, 2)
			.map((w) => w[0]?.toUpperCase() ?? '')
			.join('')
	);
</script>

<span
	class="flex shrink-0 items-center justify-center font-bold text-on-surface"
	style="width:{size}px;height:{size}px;background:{color};border-radius:{borderRadius};font-size:{Math.round(
		size * 0.38
	)}px"
>
	{initials}
</span>
