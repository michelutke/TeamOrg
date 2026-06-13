<script lang="ts">
	import { Plus } from 'lucide-svelte';
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

	function statusChipClasses(status: string): string {
		if (status === 'active') return 'bg-success-container text-success';
		if (status === 'deactivated') return 'bg-error-container text-error';
		return 'bg-error-container text-error';
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

	const inputClasses =
		'w-full rounded-2xl border-none bg-surface-container-high px-[18px] py-3 text-[14px] text-on-surface outline-none placeholder:text-on-surface-variant focus:ring-2 focus:ring-primary';
	const labelClasses = 'mb-1 block text-[12px] font-medium text-on-surface-variant';
</script>

<svelte:head>
	<title>Clubs — TeamOrg Admin</title>
</svelte:head>

<div class="flex flex-col gap-5">
	<!-- Page header -->
	<div class="flex items-center justify-between">
		<h1 class="font-display text-[30px] font-extrabold text-on-surface">Clubs</h1>
		<button
			type="button"
			onclick={() => (showCreateForm = !showCreateForm)}
			class="flex cursor-pointer items-center gap-2 rounded-full border-none bg-primary py-[13px] pl-[22px] pr-6 text-[14px] font-bold text-on-primary hover:opacity-90"
		>
			<Plus size={16} />
			New club
		</button>
	</div>

	<!-- Create club form -->
	{#if showCreateForm}
		<div class="rounded-3xl bg-surface-container-low p-6">
			<h2 class="mb-4 text-[17px] font-bold text-on-surface">New club</h2>
			{#if form?.error}
				<p class="mb-4 text-[12px] font-medium text-error">{form.error}</p>
			{/if}
			<form method="POST" action="?/create">
				<div class="grid grid-cols-2 gap-4">
					<div>
						<label for="name" class={labelClasses}>Club Name *</label>
						<input id="name" name="name" type="text" required placeholder="FC Zürich" class={inputClasses} />
					</div>
					<div>
						<label for="sportType" class={labelClasses}>Sport Type</label>
						<input id="sportType" name="sportType" type="text" placeholder="volleyball" value="volleyball" class={inputClasses} />
					</div>
					<div>
						<label for="location" class={labelClasses}>Location</label>
						<input id="location" name="location" type="text" placeholder="Zürich, Switzerland" class={inputClasses} />
					</div>
					<div>
						<label for="managerEmail" class={labelClasses}>ClubManager Email (optional)</label>
						<input id="managerEmail" name="managerEmail" type="email" placeholder="manager@example.com" class={inputClasses} />
					</div>
				</div>
				<div class="mt-4 flex gap-3">
					<button
						type="submit"
						class="cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90"
					>Create</button>
					<button
						type="button"
						onclick={() => (showCreateForm = false)}
						class="cursor-pointer rounded-full border border-outline-variant bg-transparent px-6 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
					>Cancel</button>
				</div>
			</form>
		</div>
	{/if}

	<!-- Clubs table -->
	{#if !data.clubs || data.clubs.clubs.length === 0}
		<div class="rounded-3xl bg-surface-container-low px-6 py-16 text-center">
			<p class="mb-2 font-display text-[20px] font-extrabold text-on-surface">No clubs yet</p>
			<p class="text-[14px] text-on-surface-variant">Create the first club to get started.</p>
		</div>
	{:else}
		<div class="overflow-hidden rounded-3xl bg-surface-container-low py-1">
			<table class="w-full border-collapse">
				<thead>
					<tr>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Name</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Sport</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Location</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Status</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Teams</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Members</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Created</th>
						<th scope="col" class="px-6 py-3.5"></th>
					</tr>
				</thead>
				<tbody>
					{#each data.clubs.clubs as club}
						<tr
							class="cursor-pointer border-t border-outline-variant bg-white hover:bg-surface"
							onclick={() => {
								window.location.href = `/admin/clubs/${club.id}`;
							}}
						>
							<td class="px-6 py-[13px] text-[14px] font-medium text-on-surface">{club.name}</td>
							<td class="px-6 py-[13px] text-[14px] text-on-surface-variant">{club.sportType}</td>
							<td class="px-6 py-[13px] text-[14px] text-on-surface-variant">{club.location || '—'}</td>
							<td class="px-6 py-[13px]">
								<span class="rounded-full px-3 py-1 text-[11px] font-bold {statusChipClasses(club.status)}">
									{statusLabel(club.status)}
								</span>
							</td>
							<td class="px-6 py-[13px] text-[14px] text-on-surface-variant">{club.teamCount}</td>
							<td class="px-6 py-[13px] text-[14px] text-on-surface-variant">{club.memberCount}</td>
							<td class="px-6 py-[13px] text-[14px] text-on-surface-variant">{formatDate(club.createdAt)}</td>
							<td class="px-6 py-[13px] text-[13px] font-bold text-primary">View ›</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>

		<!-- Pagination -->
		<div class="flex items-center gap-2">
			<p class="text-[13px] text-on-surface-variant">{data.clubs.totalCount} clubs</p>
			<div class="flex-1"></div>
			{#if totalPages > 1}
				<a
					href="?page={Math.max(1, data.page - 1)}"
					class="flex size-9 items-center justify-center rounded-full text-[13px] font-medium no-underline {data.page <= 1
						? 'pointer-events-none text-outline-variant'
						: 'text-on-surface-variant hover:bg-surface-container-high'}"
					aria-label="Previous page"
				>‹</a>
				{#each Array(totalPages) as _, i}
					<a
						href="?page={i + 1}"
						class="flex size-9 items-center justify-center rounded-full text-[13px] font-medium no-underline {data.page === i + 1
							? 'bg-primary text-on-primary'
							: 'text-on-surface-variant hover:bg-surface-container-high'}"
					>{i + 1}</a>
				{/each}
				<a
					href="?page={Math.min(totalPages, data.page + 1)}"
					class="flex size-9 items-center justify-center rounded-full text-[13px] font-medium no-underline {data.page >= totalPages
						? 'pointer-events-none text-outline-variant'
						: 'text-on-surface-variant hover:bg-surface-container-high'}"
					aria-label="Next page"
				>›</a>
			{/if}
		</div>
	{/if}
</div>
