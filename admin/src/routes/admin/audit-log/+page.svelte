<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/stores';
	import type { PageData } from './$types';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();

	const ACTION_OPTIONS = [
		{ value: '', label: 'All Actions' },
		{ value: 'club.create', label: 'club.create' },
		{ value: 'club.update', label: 'club.update' },
		{ value: 'club.deactivate', label: 'club.deactivate' },
		{ value: 'club.reactivate', label: 'club.reactivate' },
		{ value: 'club.delete', label: 'club.delete' },
		{ value: 'club.manager.add', label: 'club.manager.add' },
		{ value: 'club.manager.remove', label: 'club.manager.remove' },
		{ value: 'impersonation.start', label: 'impersonation.start' },
		{ value: 'impersonation.end', label: 'impersonation.end' }
	];

	let filterAction = $state(data.filters.action || '');
	let filterActor = $state(data.filters.actor || '');
	let filterStartDate = $state(data.filters.startDate || '');
	let filterEndDate = $state(data.filters.endDate || '');

	const totalPages = $derived(
		data.log.totalCount > 0 ? Math.ceil(data.log.totalCount / data.log.pageSize) : 1
	);

	const pageNumbers = $derived.by(() => {
		const start = Math.max(1, Math.min(data.page - 2, totalPages - 4));
		const end = Math.min(totalPages, start + 4);
		const pages: number[] = [];
		for (let p = start; p <= end; p++) pages.push(p);
		return pages;
	});

	function applyFilters() {
		const params = new URLSearchParams($page.url.searchParams);
		params.set('page', '1');
		if (filterAction) params.set('action', filterAction);
		else params.delete('action');
		if (filterActor) params.set('actor', filterActor);
		else params.delete('actor');
		if (filterStartDate) params.set('startDate', filterStartDate);
		else params.delete('startDate');
		if (filterEndDate) params.set('endDate', filterEndDate);
		else params.delete('endDate');
		goto(`?${params.toString()}`);
	}

	function clearFilters() {
		filterAction = '';
		filterActor = '';
		filterStartDate = '';
		filterEndDate = '';
		goto('/admin/audit-log');
	}

	function goToPage(p: number) {
		const params = new URLSearchParams($page.url.searchParams);
		params.set('page', String(p));
		goto(`?${params.toString()}`);
	}

	function formatTimestamp(ts: string): string {
		if (!ts) return '—';
		try {
			return new Date(ts).toLocaleString('en-CH', {
				year: 'numeric',
				month: 'short',
				day: 'numeric',
				hour: '2-digit',
				minute: '2-digit',
				second: '2-digit'
			});
		} catch {
			return ts;
		}
	}

	function formatDetails(details: Record<string, unknown> | null): string {
		if (!details) return '—';
		try {
			const str = JSON.stringify(details);
			return str.length > 100 ? str.slice(0, 100) + '...' : str;
		} catch {
			return '—';
		}
	}

	function formatTarget(targetType: string | null, targetId: string | null): string {
		if (!targetType && !targetId) return '—';
		if (targetType && targetId) return `${targetType} ${targetId}`;
		return targetType || targetId || '—';
	}

	function actionChipClasses(action: string): string {
		if (action.startsWith('impersonation')) return 'bg-tertiary-container text-on-tertiary-container';
		if (action.includes('manager')) return 'bg-primary-container text-on-primary-container';
		if (action.includes('delete') || action.includes('deactivate') || action.includes('archive'))
			return 'bg-error-container text-error';
		return 'bg-secondary-container text-on-secondary-container';
	}

	const chipClasses =
		'flex items-center gap-1.5 rounded-full bg-surface-container-high px-4 py-2 text-[13px] text-on-surface-variant';
</script>

<svelte:head>
	<title>Audit Log — TeamOrg Admin</title>
</svelte:head>

<div class="flex flex-col gap-5">
	<div class="flex items-baseline justify-between">
		<h1 class="font-display text-[30px] font-extrabold text-on-surface">Audit log</h1>
		<p class="text-[12px] text-on-surface-variant">Immutable · retained 2 years</p>
	</div>

	<!-- Filter chip bar -->
	<div class="flex flex-wrap items-center gap-3">
		<label class={chipClasses}>
			Action:
			<select
				bind:value={filterAction}
				onchange={applyFilters}
				class="cursor-pointer border-none bg-transparent text-[13px] font-bold text-on-surface outline-none"
			>
				{#each ACTION_OPTIONS as opt}
					<option value={opt.value}>{opt.label}</option>
				{/each}
			</select>
		</label>

		<label class={chipClasses}>
			Actor:
			<input
				type="text"
				placeholder="All admins"
				bind:value={filterActor}
				onchange={applyFilters}
				class="w-[140px] border-none bg-transparent text-[13px] font-bold text-on-surface outline-none placeholder:font-medium placeholder:text-on-surface-variant"
			/>
		</label>

		<label class={chipClasses}>
			From:
			<input
				type="date"
				bind:value={filterStartDate}
				onchange={applyFilters}
				class="cursor-pointer border-none bg-transparent text-[13px] font-bold text-on-surface outline-none"
			/>
		</label>

		<label class={chipClasses}>
			To:
			<input
				type="date"
				bind:value={filterEndDate}
				onchange={applyFilters}
				class="cursor-pointer border-none bg-transparent text-[13px] font-bold text-on-surface outline-none"
			/>
		</label>

		<button
			type="button"
			onclick={clearFilters}
			class="cursor-pointer rounded-full border-none bg-transparent px-4 py-2 text-[13px] font-bold text-primary hover:bg-primary-container/50"
		>
			Clear filters
		</button>
	</div>

	<!-- Data table -->
	{#if data.log.entries.length === 0}
		<div class="rounded-3xl bg-surface-container-low px-6 py-12 text-center">
			<p class="text-[14px] text-on-surface-variant">
				No audit events match your filters. Adjust the date range or action type.
			</p>
		</div>
	{:else}
		<div class="overflow-hidden rounded-3xl bg-surface-container-low py-1">
			<table class="w-full border-collapse">
				<thead>
					<tr>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Timestamp</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Actor</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Action</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Target</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Details</th>
					</tr>
				</thead>
				<tbody>
					{#each data.log.entries as entry}
						<tr class="border-t border-outline-variant bg-white hover:bg-surface">
							<td class="whitespace-nowrap px-6 py-[13px] text-[14px] text-on-surface-variant">
								{formatTimestamp(entry.timestamp)}
							</td>
							<td class="px-6 py-[13px] text-[14px] text-on-surface-variant">
								<div class="flex flex-wrap items-center gap-2">
									{entry.actorEmail}
									{#if entry.impersonationContext}
										<span class="rounded-full bg-tertiary-container px-3 py-1 text-[11px] font-bold text-on-tertiary-container">
											Impersonated
										</span>
									{/if}
								</div>
							</td>
							<td class="px-6 py-[13px]">
								<span class="rounded-full px-3 py-1 text-[11px] font-bold {actionChipClasses(entry.action)}">
									{entry.action}
								</span>
							</td>
							<td class="px-6 py-[13px] text-[14px] font-medium text-on-surface">
								{formatTarget(entry.targetType, entry.targetId)}
							</td>
							<td class="max-w-[300px] break-all px-6 py-[13px] text-[14px] text-on-surface-variant">
								{formatDetails(entry.details)}
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>

		<!-- Pagination -->
		<div class="flex items-center gap-2">
			<p class="text-[13px] text-on-surface-variant">
				{data.log.totalCount} entries · {data.log.pageSize} per page
			</p>
			<div class="flex-1"></div>
			{#if totalPages > 1}
				<button
					type="button"
					onclick={() => goToPage(data.page - 1)}
					disabled={data.page <= 1}
					class="flex size-9 items-center justify-center rounded-full border-none bg-transparent text-[13px] font-medium {data.page <= 1
						? 'cursor-not-allowed text-outline-variant'
						: 'cursor-pointer text-on-surface-variant hover:bg-surface-container-high'}"
					aria-label="Previous page"
				>‹</button>
				{#each pageNumbers as p}
					<button
						type="button"
						onclick={() => goToPage(p)}
						class="flex size-9 cursor-pointer items-center justify-center rounded-full border-none text-[13px] font-medium {data.page === p
							? 'bg-primary text-on-primary'
							: 'bg-transparent text-on-surface-variant hover:bg-surface-container-high'}"
					>{p}</button>
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
			{/if}
		</div>
	{/if}
</div>
