<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/stores';
	import { Search } from 'lucide-svelte';
	import type { PageData } from './$types';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();

	interface UserDetail {
		id: string;
		email: string;
		displayName: string;
		avatarUrl: string | null;
		isSuperAdmin: boolean;
		clubMemberships: Array<{
			clubId: string;
			clubName: string;
			role: string;
		}>;
		teamMemberships: Array<{
			teamId: string;
			teamName: string;
			clubName: string;
			role: string;
		}>;
	}

	let searchInput = $state(data.query || '');
	let selectedUser = $state<UserDetail | null>(null);
	let drawerOpen = $state(false);
	let loadingDetail = $state(false);
	let debounceTimer: ReturnType<typeof setTimeout> | null = null;

	const totalPages = $derived(
		data.users.totalCount > 0 ? Math.ceil(data.users.totalCount / data.users.pageSize) : 1
	);

	function onSearchInput() {
		if (debounceTimer) clearTimeout(debounceTimer);
		debounceTimer = setTimeout(() => {
			if (searchInput.length >= 2 || searchInput.length === 0) {
				const params = new URLSearchParams($page.url.searchParams);
				if (searchInput.length >= 2) {
					params.set('q', searchInput);
				} else {
					params.delete('q');
				}
				params.set('page', '1');
				goto(`?${params.toString()}`, { replaceState: true });
			}
		}, 300);
	}

	function goToPage(p: number) {
		const params = new URLSearchParams($page.url.searchParams);
		params.set('page', String(p));
		goto(`?${params.toString()}`);
	}

	async function openUserDetail(userId: string) {
		loadingDetail = true;
		drawerOpen = true;
		selectedUser = null;
		try {
			const res = await fetch(`/admin/users/${userId}`);
			if (res.ok) {
				selectedUser = await res.json();
			}
		} finally {
			loadingDetail = false;
		}
	}

	function closeDrawer() {
		drawerOpen = false;
		selectedUser = null;
	}

	function formatDate(dateStr: string): string {
		if (!dateStr) return '—';
		try {
			return new Date(dateStr).toLocaleDateString('en-CH', {
				year: 'numeric',
				month: 'short',
				day: 'numeric'
			});
		} catch {
			return dateStr;
		}
	}

	function roleChipClasses(role: string): string {
		if (role === 'SuperAdmin') return 'bg-primary-container text-on-primary-container';
		if (role === 'ClubManager') return 'bg-success-container text-success';
		if (role === 'Coach') return 'bg-tertiary-container text-on-tertiary-container';
		return 'bg-surface-container-high text-on-surface-variant';
	}
</script>

<svelte:head>
	<title>Users — TeamOrg Admin</title>
</svelte:head>

<div class="flex flex-col gap-5">
	<h1 class="font-display text-[30px] font-extrabold text-on-surface">Users</h1>

	<!-- Search input -->
	<div>
		<div class="flex items-center gap-2.5 rounded-full bg-surface-container-high px-5 py-3">
			<Search size={15} class="shrink-0 text-on-surface-variant" />
			<input
				type="text"
				placeholder="Search by name or email…"
				bind:value={searchInput}
				oninput={onSearchInput}
				aria-label="Search Users"
				class="w-full border-none bg-transparent text-[14px] text-on-surface outline-none placeholder:text-on-surface-variant"
			/>
		</div>
		<p class="mt-2 text-[12px] text-on-surface-variant">
			{#if data.query.length >= 2}
				{data.users.totalCount} results for “{data.query}” · type at least 2 characters to search
			{:else}
				Type at least 2 characters to search
			{/if}
		</p>
	</div>

	<!-- Results table -->
	{#if data.query.length >= 2}
		{#if data.users.users.length === 0}
			<!-- Empty state -->
			<div class="rounded-3xl bg-surface-container-low px-6 py-12 text-center">
				<p class="text-[14px] text-on-surface-variant">
					No users found. Try a different name or email.
				</p>
			</div>
		{:else}
			<div class="overflow-hidden rounded-3xl bg-surface-container-low py-1">
				<table class="w-full border-collapse">
					<thead>
						<tr>
							<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Name</th>
							<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Email</th>
							<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Clubs</th>
							<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Roles</th>
							<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Joined</th>
						</tr>
					</thead>
					<tbody>
						{#each data.users.users as user}
							<tr
								class="cursor-pointer border-t border-outline-variant bg-white hover:bg-surface"
								onclick={() => openUserDetail(user.id)}
							>
								<td class="px-6 py-[13px] text-[14px] font-medium text-on-surface">{user.displayName}</td>
								<td class="px-6 py-[13px] text-[14px] text-on-surface-variant">{user.email}</td>
								<td class="px-6 py-[13px] text-[14px] text-on-surface-variant">{user.clubs?.join(', ') || '—'}</td>
								<td class="px-6 py-[13px]">
									<div class="flex flex-wrap gap-1">
										{#each user.roles || [] as role}
											<span class="rounded-full px-3 py-1 text-[11px] font-bold {roleChipClasses(role)}">
												{role}
											</span>
										{/each}
									</div>
								</td>
								<td class="px-6 py-[13px] text-[14px] text-on-surface-variant">{formatDate(user.joinedAt)}</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>

			<!-- Pagination -->
			{#if totalPages > 1}
				<div class="flex items-center gap-2">
					<p class="text-[13px] text-on-surface-variant">{data.users.totalCount} users</p>
					<div class="flex-1"></div>
					<button
						type="button"
						onclick={() => goToPage(data.page - 1)}
						disabled={data.page <= 1}
						class="flex size-9 items-center justify-center rounded-full border-none bg-transparent text-[13px] font-medium {data.page <= 1
							? 'cursor-not-allowed text-outline-variant'
							: 'cursor-pointer text-on-surface-variant hover:bg-surface-container-high'}"
						aria-label="Previous page"
					>‹</button>
					{#each Array(totalPages) as _, i}
						<button
							type="button"
							onclick={() => goToPage(i + 1)}
							class="flex size-9 cursor-pointer items-center justify-center rounded-full border-none text-[13px] font-medium {data.page === i + 1
								? 'bg-primary text-on-primary'
								: 'bg-transparent text-on-surface-variant hover:bg-surface-container-high'}"
						>{i + 1}</button>
					{/each}
					<button
						type="button"
						onclick={() => goToPage(data.page + 1)}
						disabled={data.page >= totalPages}
						class="flex size-9 items-center justify-center rounded-full border-none bg-transparent text-[13px] font-medium {data.page >= totalPages
							? 'cursor-not-allowed text-outline-variant'
							: 'cursor-pointer text-on-surface-variant hover:bg-surface-container-high'}"
						aria-label="Next page"
					>›</button>
				</div>
			{/if}
		{/if}
	{:else}
		<div class="rounded-3xl bg-surface-container-low px-6 py-12 text-center">
			<p class="text-[14px] text-on-surface-variant">
				Enter at least 2 characters to search for users.
			</p>
		</div>
	{/if}
</div>

<!-- User detail drawer -->
{#if drawerOpen}
	<!-- Backdrop -->
	<div class="fixed inset-0 z-40 bg-black/40" onclick={closeDrawer} role="presentation"></div>

	<!-- Drawer -->
	<aside
		class="fixed right-0 top-0 z-50 h-full w-[400px] overflow-y-auto rounded-l-[28px] bg-white p-6 shadow-[0px_8px_32px_0px_rgba(0,0,0,0.12)]"
	>
		<!-- Header -->
		<div class="mb-6 flex items-start justify-between">
			<div>
				{#if loadingDetail}
					<p class="text-[14px] text-on-surface-variant">Loading...</p>
				{:else if selectedUser}
					<h2 class="mb-1 font-display text-[22px] font-extrabold text-on-surface">
						{selectedUser.displayName}
					</h2>
					<p class="text-[14px] text-on-surface-variant">{selectedUser.email}</p>
					{#if selectedUser.isSuperAdmin}
						<span class="mt-2 inline-block rounded-full bg-primary-container px-3 py-1 text-[11px] font-bold text-on-primary-container">
							SuperAdmin
						</span>
					{/if}
				{/if}
			</div>
			<button
				type="button"
				onclick={closeDrawer}
				aria-label="Close user detail panel"
				class="flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-full border-none bg-surface-container-high text-[14px] text-on-surface-variant hover:bg-surface-container-low"
			>
				✕
			</button>
		</div>

		{#if selectedUser}
			<!-- Club Memberships -->
			<div class="mb-6">
				<h3 class="mb-3 text-[11px] font-bold uppercase tracking-wider text-on-surface-variant">
					Club Memberships
				</h3>
				{#if selectedUser.clubMemberships.length === 0}
					<p class="text-[14px] text-on-surface-variant">No club memberships.</p>
				{:else}
					{#each selectedUser.clubMemberships as membership}
						<div class="mb-1.5 flex items-center justify-between rounded-2xl bg-surface-container-low px-4 py-2.5">
							<span class="text-[14px] text-on-surface">{membership.clubName}</span>
							<span class="rounded-full px-3 py-1 text-[11px] font-bold {roleChipClasses(membership.role)}">
								{membership.role}
							</span>
						</div>
					{/each}
				{/if}
			</div>

			<!-- Team Memberships -->
			<div>
				<h3 class="mb-3 text-[11px] font-bold uppercase tracking-wider text-on-surface-variant">
					Team Memberships
				</h3>
				{#if selectedUser.teamMemberships.length === 0}
					<p class="text-[14px] text-on-surface-variant">No team memberships.</p>
				{:else}
					{#each selectedUser.teamMemberships as membership}
						<div class="mb-1.5 flex items-center justify-between rounded-2xl bg-surface-container-low px-4 py-2.5">
							<div>
								<p class="text-[14px] text-on-surface">{membership.teamName}</p>
								<p class="mt-0.5 text-[12px] text-on-surface-variant">{membership.clubName}</p>
							</div>
							<span class="rounded-full px-3 py-1 text-[11px] font-bold {roleChipClasses(membership.role)}">
								{membership.role}
							</span>
						</div>
					{/each}
				{/if}
			</div>
		{/if}
	</aside>
{/if}
