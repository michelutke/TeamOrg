<script lang="ts">
	import { page } from '$app/stores';
	import type { Snippet } from 'svelte';
	import { House, CalendarDays, Users, Inbox, User, LogOut } from 'lucide-svelte';
	import type { LayoutData } from './$types';

	interface Props {
		data: LayoutData;
		children: Snippet;
	}

	let { data, children }: Props = $props();

	const nav = [
		{ href: '/app', label: 'Start', icon: House },
		{ href: '/app/events', label: 'Termine', icon: CalendarDays },
		{ href: '/app/teams', label: 'Teams', icon: Users },
		{ href: '/app/inbox', label: 'Inbox', icon: Inbox },
		{ href: '/app/profile', label: 'Profil', icon: User }
	];

	const pathname = $derived($page.url.pathname);
	function isActive(href: string): boolean {
		return href === '/app' ? pathname === '/app' : pathname.startsWith(href);
	}
</script>

<div class="flex min-h-screen bg-surface-container-low text-on-surface">
	<!-- Sidebar -->
	<aside
		class="sticky top-0 flex h-screen w-[240px] flex-col gap-2 border-r border-outline-variant bg-surface px-4 py-6"
	>
		<a href="/app" class="mb-4 flex items-center gap-3 px-2">
			<span
				class="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary-container text-[15px] font-bold text-on-primary-container"
			>
				TO
			</span>
			<span class="font-display text-[18px] font-extrabold">TeamOrg</span>
		</a>

		<nav class="flex flex-1 flex-col gap-1">
			{#each nav as item (item.href)}
				<a
					href={item.href}
					class="flex items-center gap-3 rounded-full px-4 py-3 text-[14px] font-medium transition-colors hover:bg-surface-container-high {isActive(
						item.href
					)
						? 'bg-secondary-container text-on-secondary-container'
						: 'text-on-surface-variant'}"
				>
					<item.icon size={20} />
					{item.label}
				</a>
			{/each}
		</nav>

		<div class="flex flex-col gap-2 border-t border-outline-variant pt-4">
			<div class="px-4">
				<p class="truncate text-[13px] font-semibold text-on-surface">{data.user.displayName}</p>
				<p class="truncate text-[11px] text-on-surface-variant">{data.user.email}</p>
			</div>
			{#if data.user.isSuperAdmin}
				<a
					href="/admin/dashboard"
					class="rounded-full px-4 py-2 text-[13px] font-medium text-on-surface-variant hover:bg-surface-container-high"
				>
					Admin-Bereich
				</a>
			{/if}
			<form method="POST" action="/logout">
				<button
					type="submit"
					class="flex w-full items-center gap-3 rounded-full border-none bg-transparent px-4 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
				>
					<LogOut size={20} />
					Abmelden
				</button>
			</form>
		</div>
	</aside>

	<!-- Content -->
	<main class="flex-1 px-8 py-8">
		{@render children()}
	</main>
</div>
