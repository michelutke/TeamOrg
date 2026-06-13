<script lang="ts">
	import type { PageData } from './$types';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();

	const statCards = $derived([
		{
			label: 'Total clubs',
			value: data.stats.totalClubs,
			classes: 'bg-primary-container text-on-primary-container'
		},
		{
			label: 'Total users',
			value: data.stats.totalUsers,
			classes: 'bg-secondary-container text-on-secondary-container'
		},
		{
			label: 'Active events today',
			value: data.stats.activeEventsToday,
			classes: 'bg-tertiary-container text-on-tertiary-container'
		},
		{
			label: 'Recent sign-ups',
			value: data.stats.recentSignups.length,
			classes: 'bg-surface-container-high text-on-surface'
		}
	]);

	function formatDate(iso: string): string {
		return new Date(iso).toLocaleDateString('en-GB', {
			day: '2-digit',
			month: 'short',
			year: 'numeric'
		});
	}
</script>

<svelte:head>
	<title>Dashboard — TeamOrg Admin</title>
</svelte:head>

<div class="flex flex-col gap-6">
	<h1 class="font-display text-[30px] font-extrabold text-on-surface">Dashboard</h1>

	<!-- Stat widgets grid -->
	<div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
		{#each statCards as card}
			<div class="flex flex-col gap-1 rounded-[28px] p-6 {card.classes}">
				<p class="font-display text-[36px] font-extrabold leading-tight">{card.value}</p>
				<p class="text-[13px] font-medium">{card.label}</p>
			</div>
		{/each}
	</div>

	<!-- Recent Sign-ups table -->
	<div class="overflow-hidden rounded-3xl bg-surface-container-low py-2">
		<div class="px-6 pb-1.5 pt-2.5">
			<h2 class="text-[13px] font-bold text-on-surface">Recent sign-ups</h2>
		</div>

		{#if data.stats.recentSignups && data.stats.recentSignups.length > 0}
			<table class="w-full border-collapse">
				<thead>
					<tr>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Name</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Email</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Joined</th>
					</tr>
				</thead>
				<tbody>
					{#each data.stats.recentSignups as user}
						<tr class="border-t border-outline-variant bg-white">
							<td class="px-6 py-3.5 text-[14px] font-medium text-on-surface">{user.displayName}</td>
							<td class="px-6 py-3.5 text-[14px] text-on-surface-variant">{user.email}</td>
							<td class="px-6 py-3.5 text-[14px] text-on-surface-variant">{formatDate(user.createdAt)}</td>
						</tr>
					{/each}
				</tbody>
			</table>
		{:else}
			<div class="border-t border-outline-variant bg-white px-6 py-8 text-center">
				<p class="text-[14px] text-on-surface-variant">No recent sign-ups.</p>
			</div>
		{/if}
	</div>
</div>
