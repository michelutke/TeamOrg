<script lang="ts">
	import { page } from '$app/stores';
	import type { Snippet } from 'svelte';
	import { House, CalendarDays, Users, Inbox, User, LogOut, Shield } from 'lucide-svelte';
	import type { LayoutData } from './$types';

	interface Props {
		data: LayoutData;
		children: Snippet;
	}

	let { data, children }: Props = $props();

	const nav = $derived([
		{ href: '/app', label: data.m.nav.start, icon: House },
		{ href: '/app/events', label: data.m.nav.termine, icon: CalendarDays },
		{ href: '/app/teams', label: data.m.nav.teams, icon: Users },
		{ href: '/app/inbox', label: data.m.nav.inbox, icon: Inbox },
		{ href: '/app/profile', label: data.m.nav.profil, icon: User }
	]);

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
			<!-- Language toggle -->
			<div class="flex items-center gap-1 px-2 text-[12px] font-medium">
				<a
					href="?lang=de"
					data-sveltekit-reload
					class="rounded-full px-3 py-1 transition-colors {data.lang === 'de'
						? 'bg-secondary-container text-on-secondary-container'
						: 'text-on-surface-variant hover:bg-surface-container-high'}">DE</a
				>
				<a
					href="?lang=en"
					data-sveltekit-reload
					class="rounded-full px-3 py-1 transition-colors {data.lang === 'en'
						? 'bg-secondary-container text-on-secondary-container'
						: 'text-on-surface-variant hover:bg-surface-container-high'}">EN</a
				>
			</div>
			<div class="px-4">
				<p class="truncate text-[13px] font-semibold text-on-surface">{data.user.displayName}</p>
				<p class="truncate text-[11px] text-on-surface-variant">{data.user.email}</p>
			</div>
			{#if data.user.managedClubIds.length > 0}
				<a
					href="/manage"
					class="flex items-center gap-3 rounded-full px-4 py-2 text-[13px] font-medium text-on-surface-variant hover:bg-surface-container-high"
				>
					<Shield size={18} />
					{data.m.nav.manageArea}
				</a>
			{/if}
			{#if data.user.isSuperAdmin}
				<a
					href="/admin/dashboard"
					class="rounded-full px-4 py-2 text-[13px] font-medium text-on-surface-variant hover:bg-surface-container-high"
				>
					{data.m.nav.adminArea}
				</a>
			{/if}
			<form method="POST" action="/logout">
				<button
					type="submit"
					class="flex w-full items-center gap-3 rounded-full border-none bg-transparent px-4 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
				>
					<LogOut size={20} />
					{data.m.nav.logout}
				</button>
			</form>
		</div>
	</aside>

	<!-- Content -->
	<main class="flex-1 px-8 py-8">
		{@render children()}
	</main>
</div>
