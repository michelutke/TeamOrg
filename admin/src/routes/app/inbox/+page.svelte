<script lang="ts">
	import { enhance } from '$app/forms';
	import { CheckCheck } from 'lucide-svelte';
	import type { PageData } from './$types';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();

	const hasUnread = $derived(data.notifications.some((n) => !n.isRead));

	function fmt(iso: string): string {
		return new Date(iso).toLocaleString(data.lang, {
			day: 'numeric',
			month: 'short',
			hour: '2-digit',
			minute: '2-digit'
		});
	}
	function href(n: { entityType: string | null; entityId: string | null }): string | null {
		return n.entityType === 'event' && n.entityId ? `/app/events/${n.entityId}` : null;
	}
</script>

<svelte:head>
	<title>{data.m.inbox.title} — TeamOrg</title>
</svelte:head>

<header class="mb-6 flex items-center justify-between">
	<h1 class="font-display text-[28px] font-extrabold text-on-surface">{data.m.inbox.title}</h1>
	{#if hasUnread}
		<form method="POST" action="?/readAll" use:enhance>
			<button
				type="submit"
				class="flex items-center gap-2 rounded-full bg-surface px-4 py-2 text-[13px] font-medium text-on-surface-variant hover:bg-surface-container-high"
			>
				<CheckCheck size={16} /> {data.m.inbox.markAllRead}
			</button>
		</form>
	{/if}
</header>

{#if data.notifications.length === 0}
	<p class="text-[14px] text-on-surface-variant">{data.m.inbox.empty}</p>
{:else}
	<div class="flex flex-col gap-2">
		{#each data.notifications as n (n.id)}
			{@const link = href(n)}
			<svelte:element
				this={link ? 'a' : 'div'}
				href={link}
				class="flex items-start gap-3 rounded-2xl bg-surface px-4 py-3 {link ? 'hover:shadow-[0px_4px_16px_0px_rgba(0,0,0,0.06)]' : ''}"
			>
				<span
					class="mt-1.5 size-2 shrink-0 rounded-full {n.isRead ? 'bg-transparent' : 'bg-primary'}"
				></span>
				<div class="min-w-0 flex-1">
					<p class="text-[14px] font-semibold text-on-surface">{n.title}</p>
					<p class="text-[13px] text-on-surface-variant">{n.body}</p>
					<p class="mt-1 text-[11px] text-on-surface-variant">{fmt(n.createdAt)}</p>
				</div>
				{#if !n.isRead}
					<form method="POST" action="?/read" use:enhance>
						<input type="hidden" name="id" value={n.id} />
						<button
							type="submit"
							class="rounded-full bg-surface-container-high px-3 py-1 text-[11px] font-medium text-on-surface-variant hover:bg-surface-container-low"
						>
							✓
						</button>
					</form>
				{/if}
			</svelte:element>
		{/each}
	</div>
{/if}
