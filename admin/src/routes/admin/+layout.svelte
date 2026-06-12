<script lang="ts">
	import { page } from '$app/stores';
	import { enhance } from '$app/forms';
	import { invalidateAll } from '$app/navigation';
	import { onMount, onDestroy } from 'svelte';
	import { LayoutDashboard, Shield, Users, History, LogOut, Trophy, Eye, Timer } from 'lucide-svelte';
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
		{ href: '/admin/clubs', label: 'Clubs', icon: Shield },
		{ href: '/admin/users', label: 'Users', icon: Users },
		{ href: '/admin/audit-log', label: 'Audit Log', icon: History }
	];

	const impersonationNavItems = $derived(
		data.impersonation?.clubId
			? [{ href: `/admin/clubs/${data.impersonation.clubId}/teams`, label: 'Teams', icon: Trophy }]
			: []
	);

	const navItems = $derived(isImpersonating ? impersonationNavItems : adminNavItems);

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
			class="fixed top-0 left-0 right-0 z-50 flex h-[60px] items-center justify-between bg-tertiary px-6"
			role="alert"
		>
			<div class="flex items-center gap-3">
				<Eye size={18} class="text-white" />
				<div class="flex flex-col">
					<span class="text-[14px] font-bold text-white">
						Impersonating {data.impersonation?.targetName}
						{#if data.impersonation?.clubName}
							@ {data.impersonation.clubName}
						{/if}
					</span>
					<span class="text-[11px] text-white/80">
						All actions are audit-logged with impersonation context
					</span>
				</div>
			</div>
			<div class="flex items-center gap-4">
				<span
					class="flex items-center gap-2 rounded-full bg-tertiary-container px-4 py-1.5 text-[13px] font-bold text-on-tertiary-container"
				>
					<Timer size={14} />
					{formatCountdown(remainingSeconds)} remaining
				</span>
				<form method="POST" action="/admin/impersonate/end" use:enhance>
					<button
						type="submit"
						class="cursor-pointer rounded-full border border-white bg-transparent px-5 py-2 text-[13px] font-bold text-white hover:bg-white/10"
					>
						End session
					</button>
				</form>
			</div>
		</div>
	{/if}

	<div class="flex min-h-screen bg-white {isImpersonating ? 'pt-[60px]' : ''}">
		<!-- Sidebar -->
		<aside
			class="fixed left-0 flex w-[260px] flex-col gap-1.5 bg-surface-container-low px-5 py-7"
			style="top: {isImpersonating ? '60px' : '0'}; height: calc(100vh - {isImpersonating
				? '60px'
				: '0px'});"
		>
			<!-- Logo -->
			<div class="flex items-center gap-3 pb-6">
				<div
					class="flex size-[44px] items-center justify-center rounded-2xl bg-primary-container"
				>
					<span class="text-[15px] font-bold text-on-primary-container">
						{#if isImpersonating && data.impersonation?.clubName}
							{data.impersonation.clubName.slice(0, 2).toUpperCase()}
						{:else}
							TO
						{/if}
					</span>
				</div>
				<div class="flex flex-col">
					{#if isImpersonating && data.impersonation?.clubName}
						<span class="font-display text-[17px] font-bold text-on-surface"
							>{data.impersonation.clubName}</span
						>
						<span class="text-[11px] font-medium text-tertiary">Club Manager View</span>
					{:else}
						<span class="font-display text-[17px] font-bold text-on-surface">Teamorg</span>
						<span class="text-[11px] font-medium text-on-surface-variant">Super Admin</span>
					{/if}
				</div>
			</div>

			<!-- Nav items -->
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

			<!-- Logout / End session -->
			<div class="flex flex-col gap-1.5">
				{#if data.user}
					<div class="px-4 text-[11px] font-medium text-on-surface-variant">
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
							class="flex w-full cursor-pointer items-center gap-3 rounded-full border border-tertiary bg-transparent px-4 py-3 text-left text-[14px] font-medium text-tertiary hover:bg-tertiary-container/40"
						>
							<LogOut size={16} />
							End Impersonation
						</button>
					</form>
				{:else}
					<form method="POST" action="/admin/logout">
						<button
							type="submit"
							class="flex w-full cursor-pointer items-center gap-3 rounded-full bg-transparent px-4 py-3 text-left text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
						>
							<LogOut size={16} />
							Log out
						</button>
					</form>
				{/if}
			</div>
		</aside>

		<!-- Main content -->
		<main class="min-h-screen flex-1 overflow-y-auto p-10" style="margin-left: 260px;">
			{@render children()}
		</main>
	</div>
{/if}
