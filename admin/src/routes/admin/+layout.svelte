<script lang="ts">
	import { page } from '$app/stores';
	import { enhance } from '$app/forms';
	import { invalidateAll } from '$app/navigation';
	import { onMount, onDestroy } from 'svelte';
	import { LayoutDashboard, Building2, Users, ScrollText, LogOut, Trophy } from 'lucide-svelte';
	import type { Snippet } from 'svelte';
	import type { LayoutData } from './$types';

	interface Props {
		data: LayoutData;
		children: Snippet;
	}

	let { data, children }: Props = $props();

	const isLoginPage = $derived($page.url.pathname === '/admin/login');
	const isImpersonating = $derived(!!data.impersonation?.active);

	const adminNavItems = [
		{ href: '/admin/dashboard', label: 'Dashboard', icon: LayoutDashboard },
		{ href: '/admin/clubs', label: 'Clubs', icon: Building2 },
		{ href: '/admin/users', label: 'Users', icon: Users },
		{ href: '/admin/audit-log', label: 'Audit Log', icon: ScrollText }
	];

	const impersonationNavItems = $derived(
		data.impersonation?.clubId
			? [{ href: `/admin/clubs/${data.impersonation.clubId}/teams`, label: 'Teams', icon: Trophy }]
			: []
	);

	const navItems = $derived(
		isImpersonating ? impersonationNavItems : adminNavItems
	);

	function isActive(href: string): boolean {
		return $page.url.pathname === href || $page.url.pathname.startsWith(href + '/');
	}

	// Impersonation countdown
	let remainingSeconds = $state(0);
	let interval: ReturnType<typeof setInterval> | undefined;

	$effect(() => {
		if (data.impersonation?.active && data.impersonation.expiresAt) {
			remainingSeconds = Math.max(
				0,
				Math.floor((data.impersonation.expiresAt - Date.now()) / 1000)
			);
		}
	});

	onMount(() => {
		if (data.impersonation?.active) {
			interval = setInterval(() => {
				remainingSeconds = Math.max(0, remainingSeconds - 1);
				if (remainingSeconds <= 0) {
					clearInterval(interval);
					// Auto-expire: reload page (hooks.server.ts will clean up)
					invalidateAll();
				}
			}, 1000);
		}
	});

	onDestroy(() => {
		if (interval) clearInterval(interval);
	});

	function formatCountdown(seconds: number): string {
		const m = Math.floor(seconds / 60);
		const s = seconds % 60;
		return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
	}
</script>

{#if isLoginPage}
	{@render children()}
{:else}
	{#if isImpersonating}
		<div
			class="fixed top-0 left-0 right-0 flex items-center justify-between px-6 z-50"
			style="height: 44px; background-color: #F97316;"
			role="alert"
		>
			<span class="font-semibold" style="font-size: 14px; color: #FFFFFF;">
				Impersonating {data.impersonation?.targetName}
				{#if data.impersonation?.clubName}
					<span style="opacity: 0.8;">@ {data.impersonation.clubName}</span>
				{/if}
				 — {formatCountdown(remainingSeconds)} remaining
			</span>
			<form method="POST" action="/admin/impersonate/end" use:enhance>
				<button
					type="submit"
					style="
						background: transparent;
						border: 1px solid #FFFFFF;
						color: #FFFFFF;
						font-size: 14px;
						font-weight: 600;
						height: 30px;
						padding: 0 12px;
						border-radius: 6px;
						cursor: pointer;
					"
				>End Session</button>
			</form>
		</div>
	{/if}

	<div class="flex min-h-screen" style="background-color: #090912; {isImpersonating ? 'padding-top: 44px;' : ''}">
		<!-- Sidebar -->
		<aside
			class="fixed left-0 h-full flex flex-col"
			style="top: {isImpersonating ? '44px' : '0'}; width: 240px; height: calc(100vh - {isImpersonating ? '44px' : '0px'}); background-color: #13131F; border-right: 1px solid #2A2A40;"
		>
			<!-- Logo -->
			<div class="p-6" style="border-bottom: 1px solid #2A2A40;">
				{#if isImpersonating && data.impersonation?.clubName}
					<span class="font-semibold" style="font-size: 20px; color: #F0F0FF;">{data.impersonation.clubName}</span>
					<span class="block font-semibold" style="font-size: 12px; color: #F97316; margin-top: 2px;">
						Club Manager View
					</span>
				{:else}
					<span class="font-semibold" style="font-size: 20px; color: #F0F0FF;">TeamOrg</span>
					<span class="block font-semibold" style="font-size: 12px; color: #9090B0; margin-top: 2px;">
						Admin Panel
					</span>
				{/if}
			</div>

			<!-- Nav items -->
			<nav class="flex-1 py-4">
				{#each navItems as item}
					{@const active = isActive(item.href)}
					<a
						href={item.href}
						class="flex items-center gap-3 no-underline"
						style="
							padding: 10px 16px;
							font-size: 14px;
							font-weight: {active ? '600' : '400'};
							color: {active ? '#4F8EF7' : '#F0F0FF'};
							background-color: {active ? 'rgba(79,142,247,0.08)' : 'transparent'};
							border-left: 3px solid {active ? '#4F8EF7' : 'transparent'};
							text-decoration: none;
							display: flex;
							align-items: center;
							gap: 12px;
							transition: background-color 0.15s;
						"
						onmouseenter={(e) => { if (!active) (e.currentTarget as HTMLElement).style.backgroundColor = '#1C1C2E'; }}
						onmouseleave={(e) => { if (!active) (e.currentTarget as HTMLElement).style.backgroundColor = 'transparent'; }}
					>
						<item.icon size={18} />
						{item.label}
					</a>
				{/each}
			</nav>

			<!-- Logout / End session -->
			<div class="p-4" style="border-top: 1px solid #2A2A40;">
				{#if data.user}
					<div class="mb-3" style="font-size: 12px; color: #9090B0; padding: 0 4px;">
						{#if isImpersonating}
							Acting as {data.impersonation?.targetName}
						{:else}
							{data.user.displayName}
						{/if}
					</div>
				{/if}
				{#if isImpersonating}
					<form method="POST" action="/admin/impersonate/end" use:enhance>
						<button
							type="submit"
							class="flex items-center gap-3 w-full"
							style="
								background: transparent;
								border: 1px solid #F97316;
								color: #F97316;
								font-size: 14px;
								padding: 8px 12px;
								border-radius: 6px;
								cursor: pointer;
								width: 100%;
								text-align: left;
							"
						>
							<LogOut size={16} />
							End Impersonation
						</button>
					</form>
				{:else}
					<form method="POST" action="/admin/logout">
						<button
							type="submit"
							class="flex items-center gap-3 w-full"
							style="
								background: transparent;
								border: 1px solid #2A2A40;
								color: #9090B0;
								font-size: 14px;
								padding: 8px 12px;
								border-radius: 6px;
								cursor: pointer;
								width: 100%;
								text-align: left;
							"
						>
							<LogOut size={16} />
							Sign Out
						</button>
					</form>
				{/if}
			</div>
		</aside>

		<!-- Main content -->
		<main class="flex-1" style="margin-left: 240px; padding: 32px; min-height: 100vh; overflow-y: auto;">
			{@render children()}
		</main>
	</div>
{/if}
