<script lang="ts">
	import { Building2, Users, Calendar, UserPlus } from 'lucide-svelte';
	import type { PageData } from './$types';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();

	const statCards = $derived([
		{
			label: 'Total Clubs',
			value: data.stats.totalClubs,
			icon: Building2
		},
		{
			label: 'Total Users',
			value: data.stats.totalUsers,
			icon: Users
		},
		{
			label: 'Active Events Today',
			value: data.stats.activeEventsToday,
			icon: Calendar
		},
		{
			label: 'Recent Sign-ups',
			value: data.stats.recentSignups.length,
			icon: UserPlus
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

<h1 class="font-semibold mb-6" style="font-size: 20px; color: #F0F0FF;">Dashboard</h1>

<!-- Stat widgets grid -->
<div
	class="grid gap-6 mb-8"
	style="grid-template-columns: repeat(4, 1fr);"
>
	{#each statCards as card}
		<div
			class="flex flex-col gap-3"
			style="background-color: #1C1C2E; padding: 24px; border-radius: 8px; border: 1px solid #2A2A40;"
		>
			<card.icon size={20} color="#4F8EF7" />
			<div>
				<p class="font-semibold" style="font-size: 28px; color: #F0F0FF; line-height: 1.15;">
					{card.value}
				</p>
				<p class="font-semibold" style="font-size: 12px; color: #9090B0; margin-top: 4px;">
					{card.label}
				</p>
			</div>
		</div>
	{/each}
</div>

<!-- Recent Sign-ups table -->
<div style="background-color: #1C1C2E; border-radius: 8px; border: 1px solid #2A2A40; overflow: hidden;">
	<div class="px-6 py-4" style="border-bottom: 1px solid #2A2A40;">
		<h2 class="font-semibold" style="font-size: 16px; color: #F0F0FF;">Recent Sign-ups</h2>
	</div>

	{#if data.stats.recentSignups && data.stats.recentSignups.length > 0}
		<table class="w-full" style="border-collapse: collapse;">
			<thead>
				<tr style="background-color: #13131F;">
					<th
						scope="col"
						class="text-left font-semibold"
						style="font-size: 12px; color: #9090B0; padding: 10px 16px;"
					>Name</th>
					<th
						scope="col"
						class="text-left font-semibold"
						style="font-size: 12px; color: #9090B0; padding: 10px 16px;"
					>Email</th>
					<th
						scope="col"
						class="text-left font-semibold"
						style="font-size: 12px; color: #9090B0; padding: 10px 16px;"
					>Joined</th>
				</tr>
			</thead>
			<tbody>
				{#each data.stats.recentSignups as user, i}
					<tr
						style="
							background-color: #1C1C2E;
							border-top: 1px solid #2A2A40;
						"
					>
						<td style="font-size: 14px; color: #F0F0FF; padding: 12px 16px;">{user.displayName}</td>
						<td style="font-size: 14px; color: #9090B0; padding: 12px 16px;">{user.email}</td>
						<td style="font-size: 14px; color: #9090B0; padding: 12px 16px;">{formatDate(user.createdAt)}</td>
					</tr>
				{/each}
			</tbody>
		</table>
	{:else}
		<div class="px-6 py-8 text-center">
			<p style="font-size: 14px; color: #9090B0;">No recent sign-ups.</p>
		</div>
	{/if}
</div>

<style>
	@media (max-width: 1023px) {
		div[style*="repeat(4, 1fr)"] {
			grid-template-columns: repeat(2, 1fr) !important;
		}
	}

	@media (max-width: 767px) {
		div[style*="repeat(4, 1fr)"] {
			grid-template-columns: 1fr !important;
		}
	}
</style>
