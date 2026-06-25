<script lang="ts" module>
	export type RsvpStatus =
		| 'confirmed'
		| 'unsure'
		| 'declined'
		| 'declined-auto'
		| 'no-response'
		| null;
</script>

<script lang="ts">
	import type { Dict } from '$lib/i18n';

	interface Props {
		status: RsvpStatus;
		m: Dict['rsvp'];
		size?: 'sm' | 'md';
	}

	let { status, m, size = 'md' }: Props = $props();

	const styles: Record<string, string> = {
		confirmed: 'bg-success-container text-success',
		unsure: 'bg-[#FFF3CD] text-[#7A5B00]',
		declined: 'bg-error-container text-error',
		'declined-auto': 'bg-error-container text-error',
		'no-response': 'bg-surface-container-high text-on-surface-variant'
	};

	const labels = $derived<Record<string, string>>({
		confirmed: m.confirmed,
		unsure: m.unsure,
		declined: m.declined,
		'declined-auto': m.declined,
		'no-response': m.noResponse
	});

	const key = $derived(status ?? 'no-response');
</script>

<span
	class="inline-flex items-center rounded-full font-semibold {styles[key] ??
		styles['no-response']} {size === 'sm' ? 'px-2 py-0.5 text-[11px]' : 'px-3 py-1 text-[12px]'}"
>
	{labels[key] ?? m.noResponse}
</span>
