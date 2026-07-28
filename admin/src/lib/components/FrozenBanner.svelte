<script lang="ts">
	import { AlertTriangle } from 'lucide-svelte';
	import type { Dict } from '$lib/i18n';

	interface Props {
		variant: 'frozen' | 'past_due';
		clubId: string;
		m: Dict['billing'];
	}

	let { variant, clubId, m }: Props = $props();

	const styles: Record<string, string> = {
		frozen: 'bg-error-container text-error',
		past_due: 'bg-[#FFF3CD] text-[#7A5B00]'
	};
</script>

<div
	class="flex items-center gap-3 rounded-2xl px-4 py-3 text-[13px] font-medium {styles[variant]}"
>
	<AlertTriangle size={18} class="shrink-0" />
	{#if variant === 'frozen'}
		<span class="flex-1">{m.frozenBanner}</span>
		<a href="/manage/{clubId}/billing" class="shrink-0 whitespace-nowrap underline">
			{m.frozenBannerCta}
		</a>
	{:else}
		<span class="flex-1">{m.pastDueBanner}</span>
	{/if}
</div>
