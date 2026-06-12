<script lang="ts">
	import { page } from '$app/stores';
	import type { Snippet } from 'svelte';
	import type { LayoutData } from './$types';

	interface Props {
		data: LayoutData;
		children: Snippet;
	}

	let { data, children }: Props = $props();

	const tabs = $derived(
		data.impersonating
			? [
					{ href: `/admin/clubs/${data.clubId}/teams`, label: 'Teams' }
				]
			: []
	);

	function isActive(tab: { href: string }): boolean {
		return $page.url.pathname.startsWith(tab.href);
	}
</script>

<!-- Breadcrumb -->
<nav class="mb-4" style="font-size: 14px; color: #9090B0;">
	{#if data.impersonating}
		<span style="color: #F0F0FF;">{data.club.name}</span>
	{:else}
		<a href="/admin/clubs" style="color: #9090B0; text-decoration: none;">Clubs</a>
		<span class="mx-2">›</span>
		<span style="color: #F0F0FF;">{data.club.name}</span>
	{/if}
</nav>

<h1 class="font-semibold mb-4" style="font-size: 20px; color: #F0F0FF;">{data.club.name}</h1>

<!-- Tabs (only shown when impersonating and there are multiple tabs) -->
{#if tabs.length > 1}
	<div class="flex gap-0 mb-6" style="border-bottom: 1px solid #2A2A40;">
		{#each tabs as tab}
			{@const active = isActive(tab)}
			<a
				href={tab.href}
				style="
					padding: 10px 20px;
					font-size: 14px;
					font-weight: {active ? '600' : '400'};
					color: {active ? '#4F8EF7' : '#9090B0'};
					border-bottom: 2px solid {active ? '#4F8EF7' : 'transparent'};
					text-decoration: none;
					margin-bottom: -1px;
				"
			>
				{tab.label}
			</a>
		{/each}
	</div>
{/if}

{@render children()}
