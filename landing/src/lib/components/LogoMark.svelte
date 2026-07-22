<script lang="ts">
	// The "T-mark" — three blocks forming a crossbar, one stem below the middle block.
	let {
		size = 28,
		blocks = '#0E6577',
		stem = '#181C1F'
	}: { size?: number; blocks?: string; stem?: string } = $props();
</script>

<svg
	width={size}
	height={size}
	viewBox="0 0 560 560"
	fill="none"
	aria-hidden="true"
	xmlns="http://www.w3.org/2000/svg"
>
	<rect x="0" y="0" width="160" height="160" rx="48" fill={blocks} />
	<rect x="200" y="0" width="160" height="160" rx="48" fill={blocks} />
	<rect x="400" y="0" width="160" height="160" rx="48" fill={blocks} />
	<rect x="200" y="200" width="160" height="360" rx="48" fill={stem} />
</svg>
