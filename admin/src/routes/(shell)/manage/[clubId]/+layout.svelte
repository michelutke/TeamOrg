<script lang="ts">
	import type { Snippet } from 'svelte';
	import FrozenBanner from '$lib/components/FrozenBanner.svelte';
	import type { LayoutData } from './$types';

	interface Props {
		data: LayoutData;
		children: Snippet;
	}

	let { data, children }: Props = $props();
</script>

<div class="flex flex-col gap-4">
	{#if data.club.billingStatus === 'frozen'}
		<FrozenBanner variant="frozen" clubId={data.clubId} m={data.m.billing} />
	{:else if data.club.billingStatus === 'past_due'}
		<FrozenBanner variant="past_due" clubId={data.clubId} m={data.m.billing} />
	{/if}
	{@render children()}
</div>
