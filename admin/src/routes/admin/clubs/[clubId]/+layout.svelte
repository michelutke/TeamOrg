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
		data.impersonating ? [{ href: `/admin/clubs/${data.clubId}/teams`, label: 'Teams' }] : []
	);

	function isActive(tab: { href: string }): boolean {
		return $page.url.pathname.startsWith(tab.href);
	}
</script>

<!-- Breadcrumb -->
{#if !data.impersonating}
	<nav class="mb-6 text-[13px] text-on-surface-variant">
		<a href="/admin/clubs" class="text-on-surface-variant no-underline hover:text-primary">Clubs</a>
		<span class="mx-2">›</span>
		<span class="text-on-surface">{data.club.name}</span>
	</nav>
{/if}

<!-- Tabs (only shown when impersonating and there are multiple tabs) -->
{#if tabs.length > 1}
	<div class="mb-6 flex gap-2">
		{#each tabs as tab}
			{@const active = isActive(tab)}
			<a
				href={tab.href}
				class="rounded-full px-5 py-2.5 text-[14px] no-underline {active
					? 'bg-secondary-container font-bold text-on-secondary-container'
					: 'font-medium text-on-surface-variant hover:bg-surface-container-high'}"
			>
				{tab.label}
			</a>
		{/each}
	</div>
{/if}

{@render children()}
