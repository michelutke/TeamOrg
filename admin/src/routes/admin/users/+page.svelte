<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/stores';
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

	function getRoleBadgeStyle(role: string): string {
		if (role === 'SuperAdmin') return 'color: #4F8EF7; background: rgba(79,142,247,0.12);';
		if (role === 'ClubManager') return 'color: #22C55E; background: rgba(34,197,94,0.12);';
		if (role === 'Coach') return 'color: #FACC15; background: rgba(250,204,21,0.12);';
		return 'color: #9090B0; background: rgba(144,144,176,0.12);';
	}
</script>

<svelte:head>
	<title>Users — TeamOrg Admin</title>
</svelte:head>

<div>
	<h1 class="font-semibold mb-6" style="font-size: 20px; color: #F0F0FF;">Users</h1>

	<!-- Search input -->
	<div class="mb-6">
		<input
			type="text"
			placeholder="Search by name or email..."
			bind:value={searchInput}
			oninput={onSearchInput}
			aria-label="Search Users"
			style="
				width: 100%;
				background-color: #1C1C2E;
				border: 1px solid #2A2A40;
				color: #F0F0FF;
				font-size: 14px;
				height: 40px;
				padding: 0 16px;
				border-radius: 6px;
				outline: none;
				box-sizing: border-box;
			"
			onfocus={(e) => ((e.currentTarget as HTMLInputElement).style.outline = '2px solid #4F8EF7')}
			onblur={(e) => ((e.currentTarget as HTMLInputElement).style.outline = 'none')}
		/>
		<p style="font-size: 12px; color: #9090B0; margin-top: 6px;">
			Search Users — type at least 2 characters
		</p>
	</div>

	<!-- Results table -->
	{#if data.query.length >= 2}
		{#if data.users.users.length === 0}
			<!-- Empty state -->
			<div
				style="
					background-color: #1C1C2E;
					border: 1px solid #2A2A40;
					border-radius: 8px;
					padding: 48px 24px;
					text-align: center;
				"
			>
				<p style="font-size: 14px; color: #9090B0;">
					No users found. Try a different name or email.
				</p>
			</div>
		{:else}
			<div
				style="border: 1px solid #2A2A40; border-radius: 8px; overflow: hidden;"
			>
				<table style="width: 100%; border-collapse: collapse;">
					<thead style="background-color: #13131F;">
						<tr>
							<th
								scope="col"
								style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;"
							>
								Name
							</th>
							<th
								scope="col"
								style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;"
							>
								Email
							</th>
							<th
								scope="col"
								style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;"
							>
								Clubs
							</th>
							<th
								scope="col"
								style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;"
							>
								Roles
							</th>
							<th
								scope="col"
								style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;"
							>
								Joined
							</th>
						</tr>
					</thead>
					<tbody>
						{#each data.users.users as user}
							<tr
								style="
									background-color: #1C1C2E;
									border-top: 1px solid #2A2A40;
									cursor: pointer;
								"
								onclick={() => openUserDetail(user.id)}
								onmouseenter={(e) =>
									((e.currentTarget as HTMLElement).style.backgroundColor =
										'rgba(255,255,255,0.03)')}
								onmouseleave={(e) =>
									((e.currentTarget as HTMLElement).style.backgroundColor = '#1C1C2E')}
							>
								<td style="padding: 12px 16px; font-size: 14px; color: #F0F0FF;">
									{user.displayName}
								</td>
								<td style="padding: 12px 16px; font-size: 14px; color: #F0F0FF;">
									{user.email}
								</td>
								<td style="padding: 12px 16px; font-size: 14px; color: #F0F0FF;">
									{user.clubs?.join(', ') || '—'}
								</td>
								<td style="padding: 12px 16px;">
									<div style="display: flex; flex-wrap: wrap; gap: 4px;">
										{#each (user.roles || []) as role}
											<span
												style="
													font-size: 12px;
													font-weight: 600;
													padding: 2px 8px;
													border-radius: 4px;
													{getRoleBadgeStyle(role)}
												"
											>
												{role}
											</span>
										{/each}
									</div>
								</td>
								<td style="padding: 12px 16px; font-size: 14px; color: #9090B0;">
									{formatDate(user.joinedAt)}
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>

			<!-- Pagination -->
			{#if totalPages > 1}
				<div
					style="display: flex; align-items: center; gap: 12px; margin-top: 16px; justify-content: flex-end;"
				>
					<button
						type="button"
						onclick={() => goToPage(data.page - 1)}
						disabled={data.page <= 1}
						style="
							background: transparent;
							border: 1px solid #2A2A40;
							color: {data.page <= 1 ? '#9090B0' : '#F0F0FF'};
							font-size: 14px;
							padding: 8px 16px;
							border-radius: 6px;
							cursor: {data.page <= 1 ? 'not-allowed' : 'pointer'};
						"
					>
						Previous
					</button>
					<span style="font-size: 14px; color: #9090B0;">
						Page {data.page} of {totalPages}
					</span>
					<button
						type="button"
						onclick={() => goToPage(data.page + 1)}
						disabled={data.page >= totalPages}
						style="
							background: transparent;
							border: 1px solid #2A2A40;
							color: {data.page >= totalPages ? '#9090B0' : '#F0F0FF'};
							font-size: 14px;
							padding: 8px 16px;
							border-radius: 6px;
							cursor: {data.page >= totalPages ? 'not-allowed' : 'pointer'};
						"
					>
						Next
					</button>
				</div>
			{/if}
		{/if}
	{:else}
		<div
			style="
				background-color: #1C1C2E;
				border: 1px solid #2A2A40;
				border-radius: 8px;
				padding: 48px 24px;
				text-align: center;
			"
		>
			<p style="font-size: 14px; color: #9090B0;">
				Enter at least 2 characters to search for users.
			</p>
		</div>
	{/if}
</div>

<!-- User detail drawer -->
{#if drawerOpen}
	<!-- Backdrop -->
	<div
		style="
			position: fixed;
			inset: 0;
			background: rgba(0,0,0,0.5);
			z-index: 40;
		"
		onclick={closeDrawer}
		role="presentation"
	></div>

	<!-- Drawer -->
	<aside
		style="
			position: fixed;
			top: 0;
			right: 0;
			height: 100%;
			width: 400px;
			background-color: #1C1C2E;
			border-left: 1px solid #2A2A40;
			z-index: 50;
			padding: 24px;
			overflow-y: auto;
		"
	>
		<!-- Header -->
		<div style="display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 24px;">
			<div>
				{#if loadingDetail}
					<p style="font-size: 14px; color: #9090B0;">Loading...</p>
				{:else if selectedUser}
					<h2 style="font-size: 20px; font-weight: 600; color: #F0F0FF; margin: 0 0 4px 0;">
						{selectedUser.displayName}
					</h2>
					<p style="font-size: 14px; color: #9090B0; margin: 0;">
						{selectedUser.email}
					</p>
					{#if selectedUser.isSuperAdmin}
						<span
							style="
								display: inline-block;
								margin-top: 8px;
								font-size: 12px;
								font-weight: 600;
								padding: 2px 8px;
								border-radius: 4px;
								color: #4F8EF7;
								background: rgba(79,142,247,0.12);
							"
						>
							SuperAdmin
						</span>
					{/if}
				{/if}
			</div>
			<button
				type="button"
				onclick={closeDrawer}
				aria-label="Close user detail panel"
				style="
					background: transparent;
					border: 1px solid #2A2A40;
					color: #9090B0;
					width: 32px;
					height: 32px;
					border-radius: 6px;
					cursor: pointer;
					font-size: 16px;
					display: flex;
					align-items: center;
					justify-content: center;
					flex-shrink: 0;
				"
			>
				✕
			</button>
		</div>

		{#if selectedUser}
			<!-- Club Memberships -->
			<div style="margin-bottom: 24px;">
				<h3 style="font-size: 12px; font-weight: 600; color: #9090B0; margin: 0 0 12px 0; text-transform: uppercase; letter-spacing: 0.05em;">
					Club Memberships
				</h3>
				{#if selectedUser.clubMemberships.length === 0}
					<p style="font-size: 14px; color: #9090B0;">No club memberships.</p>
				{:else}
					{#each selectedUser.clubMemberships as membership}
						<div
							style="
								display: flex;
								align-items: center;
								justify-content: space-between;
								padding: 10px 12px;
								background-color: #13131F;
								border-radius: 6px;
								margin-bottom: 6px;
							"
						>
							<span style="font-size: 14px; color: #F0F0FF;">{membership.clubName}</span>
							<span
								style="
									font-size: 12px;
									font-weight: 600;
									padding: 2px 8px;
									border-radius: 4px;
									{getRoleBadgeStyle(membership.role)}
								"
							>
								{membership.role}
							</span>
						</div>
					{/each}
				{/if}
			</div>

			<!-- Team Memberships -->
			<div>
				<h3 style="font-size: 12px; font-weight: 600; color: #9090B0; margin: 0 0 12px 0; text-transform: uppercase; letter-spacing: 0.05em;">
					Team Memberships
				</h3>
				{#if selectedUser.teamMemberships.length === 0}
					<p style="font-size: 14px; color: #9090B0;">No team memberships.</p>
				{:else}
					{#each selectedUser.teamMemberships as membership}
						<div
							style="
								display: flex;
								align-items: center;
								justify-content: space-between;
								padding: 10px 12px;
								background-color: #13131F;
								border-radius: 6px;
								margin-bottom: 6px;
							"
						>
							<div>
								<p style="font-size: 14px; color: #F0F0FF; margin: 0;">{membership.teamName}</p>
								<p style="font-size: 12px; color: #9090B0; margin: 2px 0 0 0;">{membership.clubName}</p>
							</div>
							<span
								style="
									font-size: 12px;
									font-weight: 600;
									padding: 2px 8px;
									border-radius: 4px;
									{getRoleBadgeStyle(membership.role)}
								"
							>
								{membership.role}
							</span>
						</div>
					{/each}
				{/if}
			</div>
		{/if}
	</aside>
{/if}
