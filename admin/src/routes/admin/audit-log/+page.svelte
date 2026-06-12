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

	const inputStyle = `
		background-color: #1C1C2E;
		border: 1px solid #2A2A40;
		color: #F0F0FF;
		font-size: 14px;
		height: 40px;
		padding: 0 16px;
		border-radius: 6px;
		outline: none;
	`;
</script>

<svelte:head>
	<title>Audit Log — TeamOrg Admin</title>
</svelte:head>

<div>
	<h1 class="font-semibold mb-6" style="font-size: 20px; color: #F0F0FF;">Audit Log</h1>

	<!-- Filter bar -->
	<div
		style="
			background-color: #1C1C2E;
			border: 1px solid #2A2A40;
			border-radius: 8px;
			padding: 16px;
			margin-bottom: 24px;
		"
	>
		<div
			style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-bottom: 12px;"
		>
			<!-- Action type dropdown -->
			<div>
				<label
					for="filter-action"
					style="display: block; font-size: 12px; font-weight: 600; color: #9090B0; margin-bottom: 4px;"
				>
					Action Type
				</label>
				<select
					id="filter-action"
					bind:value={filterAction}
					style="{inputStyle} width: 100%; cursor: pointer;"
				>
					{#each ACTION_OPTIONS as opt}
						<option value={opt.value}>{opt.label}</option>
					{/each}
				</select>
			</div>

			<!-- Actor email -->
			<div>
				<label
					for="filter-actor"
					style="display: block; font-size: 12px; font-weight: 600; color: #9090B0; margin-bottom: 4px;"
				>
					Actor Email
				</label>
				<input
					id="filter-actor"
					type="text"
					placeholder="Filter by actor email..."
					bind:value={filterActor}
					style="{inputStyle} width: 100%; box-sizing: border-box;"
					onfocus={(e) =>
						((e.currentTarget as HTMLInputElement).style.outline = '2px solid #4F8EF7')}
					onblur={(e) => ((e.currentTarget as HTMLInputElement).style.outline = 'none')}
				/>
			</div>

			<!-- Start date -->
			<div>
				<label
					for="filter-start"
					style="display: block; font-size: 12px; font-weight: 600; color: #9090B0; margin-bottom: 4px;"
				>
					Start Date
				</label>
				<input
					id="filter-start"
					type="date"
					bind:value={filterStartDate}
					style="{inputStyle} width: 100%; box-sizing: border-box;"
					onfocus={(e) =>
						((e.currentTarget as HTMLInputElement).style.outline = '2px solid #4F8EF7')}
					onblur={(e) => ((e.currentTarget as HTMLInputElement).style.outline = 'none')}
				/>
			</div>

			<!-- End date -->
			<div>
				<label
					for="filter-end"
					style="display: block; font-size: 12px; font-weight: 600; color: #9090B0; margin-bottom: 4px;"
				>
					End Date
				</label>
				<input
					id="filter-end"
					type="date"
					bind:value={filterEndDate}
					style="{inputStyle} width: 100%; box-sizing: border-box;"
					onfocus={(e) =>
						((e.currentTarget as HTMLInputElement).style.outline = '2px solid #4F8EF7')}
					onblur={(e) => ((e.currentTarget as HTMLInputElement).style.outline = 'none')}
				/>
			</div>
		</div>

		<!-- Filter actions -->
		<div style="display: flex; gap: 8px;">
			<button
				type="button"
				onclick={applyFilters}
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
				Apply Filters
			</button>
			<button
				type="button"
				onclick={clearFilters}
				style="
					background: transparent;
					color: #F0F0FF;
					font-size: 14px;
					height: 40px;
					padding: 0 16px;
					border-radius: 6px;
					border: 1px solid #2A2A40;
					cursor: pointer;
				"
			>
				Clear Filters
			</button>
		</div>
	</div>

	<!-- Data table -->
	{#if data.log.entries.length === 0}
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
				No audit events match your filters. Adjust the date range or action type.
			</p>
		</div>
	{:else}
		<div style="border: 1px solid #2A2A40; border-radius: 8px; overflow: hidden;">
			<table style="width: 100%; border-collapse: collapse;">
				<thead style="background-color: #13131F;">
					<tr>
						<th
							scope="col"
							style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;"
						>
							Timestamp
						</th>
						<th
							scope="col"
							style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;"
						>
							Actor
						</th>
						<th
							scope="col"
							style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;"
						>
							Action
						</th>
						<th
							scope="col"
							style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;"
						>
							Target
						</th>
						<th
							scope="col"
							style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;"
						>
							Details
						</th>
					</tr>
				</thead>
				<tbody>
					{#each data.log.entries as entry}
						<tr
							style="
								background-color: #1C1C2E;
								border-top: 1px solid #2A2A40;
							"
							onmouseenter={(e) =>
								((e.currentTarget as HTMLElement).style.backgroundColor =
									'rgba(255,255,255,0.03)')}
							onmouseleave={(e) =>
								((e.currentTarget as HTMLElement).style.backgroundColor = '#1C1C2E')}
						>
							<td style="padding: 12px 16px; font-size: 14px; color: #9090B0; white-space: nowrap;">
								{formatTimestamp(entry.timestamp)}
							</td>
							<td style="padding: 12px 16px; font-size: 14px; color: #F0F0FF;">
								<div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
									{entry.actorEmail}
									{#if entry.impersonationContext}
										<span
											style="
												font-size: 12px;
												font-weight: 600;
												padding: 2px 8px;
												border-radius: 4px;
												color: #F97316;
												background: rgba(249,115,22,0.12);
											"
										>
											Impersonated
										</span>
									{/if}
								</div>
							</td>
							<td style="padding: 12px 16px; font-size: 14px; color: #F0F0FF;">
								<span
									style="
										font-size: 12px;
										font-weight: 600;
										padding: 2px 8px;
										border-radius: 4px;
										color: #9090B0;
										background: rgba(144,144,176,0.12);
									"
								>
									{entry.action}
								</span>
							</td>
							<td style="padding: 12px 16px; font-size: 14px; color: #9090B0;">
								{formatTarget(entry.targetType, entry.targetId)}
							</td>
							<td
								style="padding: 12px 16px; font-size: 14px; color: #9090B0; max-width: 300px; word-break: break-all;"
							>
								{formatDetails(entry.details)}
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
</div>
