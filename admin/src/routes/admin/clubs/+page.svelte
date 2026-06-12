<script lang="ts">
	import type { PageData, ActionData } from './$types';

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();

	let showCreateForm = $state(false);
	let totalPages = $derived(
		data.clubs ? Math.ceil(data.clubs.totalCount / data.clubs.pageSize) : 1
	);

	function statusColor(status: string): string {
		if (status === 'active') return '#22C55E';
		if (status === 'deactivated') return '#FACC15';
		return '#EF4444';
	}

	function statusBg(status: string): string {
		if (status === 'active') return 'rgba(34,197,94,0.12)';
		if (status === 'deactivated') return 'rgba(250,204,21,0.12)';
		return 'rgba(239,68,68,0.12)';
	}

	function statusLabel(status: string): string {
		if (status === 'active') return 'Active';
		if (status === 'deactivated') return 'Deactivated';
		return 'Deleted';
	}

	function formatDate(iso: string): string {
		return new Date(iso).toLocaleDateString('en-GB', {
			day: '2-digit',
			month: 'short',
			year: 'numeric'
		});
	}
</script>

<svelte:head>
	<title>Clubs — TeamOrg Admin</title>
</svelte:head>

<!-- Page header -->
<div class="flex items-center justify-between mb-6">
	<h1 class="font-semibold" style="font-size: 20px; color: #F0F0FF;">Clubs</h1>
	<button
		type="button"
		onclick={() => (showCreateForm = !showCreateForm)}
		style="
			background-color: #4F8EF7;
			color: #FFFFFF;
			font-size: 14px;
			font-weight: 600;
			height: 40px;
			padding: 0 16px;
			border-radius: 6px;
			border: none;
			cursor: pointer;
		"
	>
		Create Club
	</button>
</div>

<!-- Create club form -->
{#if showCreateForm}
	<div
		class="mb-6"
		style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 24px;"
	>
		<h2 class="font-semibold mb-4" style="font-size: 16px; color: #F0F0FF;">New Club</h2>
		{#if form?.error}
			<p class="mb-4" style="font-size: 12px; color: #EF4444;">{form.error}</p>
		{/if}
		<form method="POST" action="?/create">
			<div class="grid gap-4" style="grid-template-columns: 1fr 1fr;">
				<div>
					<label
						for="name"
						class="block font-semibold mb-1"
						style="font-size: 12px; color: #F0F0FF;"
					>Club Name *</label>
					<input
						id="name"
						name="name"
						type="text"
						required
						placeholder="FC Zürich"
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
						"
					/>
				</div>
				<div>
					<label
						for="sportType"
						class="block font-semibold mb-1"
						style="font-size: 12px; color: #F0F0FF;"
					>Sport Type</label>
					<input
						id="sportType"
						name="sportType"
						type="text"
						placeholder="volleyball"
						value="volleyball"
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
						"
					/>
				</div>
				<div>
					<label
						for="location"
						class="block font-semibold mb-1"
						style="font-size: 12px; color: #F0F0FF;"
					>Location</label>
					<input
						id="location"
						name="location"
						type="text"
						placeholder="Zürich, Switzerland"
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
						"
					/>
				</div>
				<div>
					<label
						for="managerEmail"
						class="block font-semibold mb-1"
						style="font-size: 12px; color: #F0F0FF;"
					>ClubManager Email (optional)</label>
					<input
						id="managerEmail"
						name="managerEmail"
						type="email"
						placeholder="manager@example.com"
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
						"
					/>
				</div>
			</div>
			<div class="flex gap-3 mt-4">
				<button
					type="submit"
					style="
						background-color: #4F8EF7;
						color: #FFFFFF;
						font-size: 14px;
						font-weight: 600;
						height: 40px;
						padding: 0 16px;
						border-radius: 6px;
						border: none;
						cursor: pointer;
					"
				>Create</button>
				<button
					type="button"
					onclick={() => (showCreateForm = false)}
					style="
						background: transparent;
						border: 1px solid #2A2A40;
						color: #F0F0FF;
						font-size: 14px;
						height: 40px;
						padding: 0 16px;
						border-radius: 6px;
						cursor: pointer;
					"
				>Cancel</button>
			</div>
		</form>
	</div>
{/if}

<!-- Clubs table -->
{#if !data.clubs || data.clubs.clubs.length === 0}
	<div
		style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 64px 24px;"
		class="text-center"
	>
		<p class="font-semibold mb-2" style="font-size: 20px; color: #F0F0FF;">No clubs yet</p>
		<p style="font-size: 14px; color: #9090B0;">Create the first club to get started.</p>
	</div>
{:else}
	<div style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; overflow: hidden;">
		<table class="w-full" style="border-collapse: collapse;">
			<thead>
				<tr style="background-color: #13131F;">
					<th scope="col" class="text-left font-semibold" style="font-size: 12px; color: #9090B0; padding: 10px 16px;">Name</th>
					<th scope="col" class="text-left font-semibold" style="font-size: 12px; color: #9090B0; padding: 10px 16px;">Sport Type</th>
					<th scope="col" class="text-left font-semibold" style="font-size: 12px; color: #9090B0; padding: 10px 16px;">Location</th>
					<th scope="col" class="text-left font-semibold" style="font-size: 12px; color: #9090B0; padding: 10px 16px;">Status</th>
					<th scope="col" class="text-left font-semibold" style="font-size: 12px; color: #9090B0; padding: 10px 16px;">Teams</th>
					<th scope="col" class="text-left font-semibold" style="font-size: 12px; color: #9090B0; padding: 10px 16px;">Members</th>
					<th scope="col" class="text-left font-semibold" style="font-size: 12px; color: #9090B0; padding: 10px 16px;">Created</th>
				</tr>
			</thead>
			<tbody>
				{#each data.clubs.clubs as club}
					<tr
						style="background-color: #1C1C2E; border-top: 1px solid #2A2A40; cursor: pointer;"
						onclick={() => { window.location.href = `/admin/clubs/${club.id}`; }}
						onmouseenter={(e) => { (e.currentTarget as HTMLElement).style.backgroundColor = 'rgba(255,255,255,0.03)'; }}
						onmouseleave={(e) => { (e.currentTarget as HTMLElement).style.backgroundColor = '#1C1C2E'; }}
					>
						<td style="font-size: 14px; color: #F0F0FF; padding: 12px 16px; font-weight: 500;">{club.name}</td>
						<td style="font-size: 14px; color: #9090B0; padding: 12px 16px;">{club.sportType}</td>
						<td style="font-size: 14px; color: #9090B0; padding: 12px 16px;">{club.location || '—'}</td>
						<td style="padding: 12px 16px;">
							<span
								class="font-semibold"
								style="
									font-size: 12px;
									color: {statusColor(club.status)};
									background-color: {statusBg(club.status)};
									padding: 2px 8px;
									border-radius: 4px;
								"
							>{statusLabel(club.status)}</span>
						</td>
						<td style="font-size: 14px; color: #9090B0; padding: 12px 16px;">{club.teamCount}</td>
						<td style="font-size: 14px; color: #9090B0; padding: 12px 16px;">{club.memberCount}</td>
						<td style="font-size: 14px; color: #9090B0; padding: 12px 16px;">{formatDate(club.createdAt)}</td>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>

	<!-- Pagination -->
	{#if totalPages > 1}
		<div class="flex items-center gap-3 mt-4" style="justify-content: flex-end;">
			<a
				href="?page={Math.max(1, data.page - 1)}"
				style="
					border: 1px solid {data.page <= 1 ? '#2A2A40' : '#4F8EF7'};
					color: {data.page <= 1 ? '#9090B0' : '#4F8EF7'};
					font-size: 14px;
					height: 40px;
					padding: 0 16px;
					border-radius: 6px;
					display: flex;
					align-items: center;
					text-decoration: none;
					pointer-events: {data.page <= 1 ? 'none' : 'auto'};
				"
			>Previous</a>
			<span style="font-size: 14px; color: #9090B0;">Page {data.page} of {totalPages}</span>
			<a
				href="?page={Math.min(totalPages, data.page + 1)}"
				style="
					border: 1px solid {data.page >= totalPages ? '#2A2A40' : '#4F8EF7'};
					color: {data.page >= totalPages ? '#9090B0' : '#4F8EF7'};
					font-size: 14px;
					height: 40px;
					padding: 0 16px;
					border-radius: 6px;
					display: flex;
					align-items: center;
					text-decoration: none;
					pointer-events: {data.page >= totalPages ? 'none' : 'auto'};
				"
			>Next</a>
		</div>
	{/if}
{/if}
