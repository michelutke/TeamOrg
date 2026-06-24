<script lang="ts">
	import { page } from '$app/stores';
	import { LogOut, Trophy, Building2 } from 'lucide-svelte';
	import type { Snippet } from 'svelte';
	import type { LayoutData } from './$types';

	interface Props {
		data: LayoutData;
		children: Snippet;
	}

	let { data, children }: Props = $props();

	const navItems = $derived([
		{ href: `/manage/${data.clubId}`, label: 'Club', icon: Building2 },
		{ href: `/manage/${data.clubId}/teams`, label: 'Teams', icon: Trophy }
	]);

	function isActive(href: string): boolean {
		if (href === `/manage/${data.clubId}`) {
			return $page.url.pathname === href;
		}
		return $page.url.pathname.startsWith(href);
	}

	function initials(name: string): string {
		return name
			.split(' ')
			.map((p) => p[0])
			.slice(0, 2)
			.join('')
			.toUpperCase();
	}
</script>

<div class="flex min-h-screen bg-white">
	<!-- Sidebar -->
	<aside class="fixed left-0 top-0 flex h-screen w-[260px] flex-col gap-1.5 bg-surface-container-low px-5 py-7">
		<!-- Logo / club name -->
		<div class="flex items-center gap-3 pb-6">
			<div class="flex size-[44px] items-center justify-center rounded-2xl bg-primary-container">
				<span class="text-[15px] font-bold text-on-primary-container">{initials(data.club.name)}</span>
			</div>
			<div class="flex flex-col">
				<span class="font-display text-[17px] font-bold text-on-surface">{data.club.name}</span>
				<span class="text-[11px] font-medium text-on-surface-variant">Club Manager</span>
			</div>
		</div>

		<!-- Nav -->
		<nav class="flex flex-1 flex-col gap-1.5">
			{#each navItems as item}
				{@const active = isActive(item.href)}
				<a
					href={item.href}
					class="flex items-center gap-3 rounded-full px-4 py-3 text-[14px] no-underline transition-colors {active
						? 'bg-secondary-container font-bold text-on-secondary-container'
						: 'font-medium text-on-surface-variant hover:bg-surface-container-high'}"
				>
					<item.icon size={18} />
					{item.label}
				</a>
			{/each}
		</nav>

		<!-- Footer -->
		<div class="flex flex-col gap-1.5">
			<form method="POST" action="/admin/logout">
				<button
					type="submit"
					class="flex w-full cursor-pointer items-center gap-3 rounded-full bg-transparent px-4 py-3 text-left text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
				>
					<LogOut size={16} />
					Log out
				</button>
			</form>
		</div>
	</aside>

	<!-- Main -->
	<main class="min-h-screen flex-1 overflow-y-auto p-10" style="margin-left: 260px;">
		{@render children()}
	</main>
</div>
