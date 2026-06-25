<script lang="ts">
	import { ArrowLeft } from 'lucide-svelte';
	import TeamBadge from '$lib/components/TeamBadge.svelte';
	import MemberRow from '$lib/components/MemberRow.svelte';
	import type { PageData } from './$types';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();

	const roleLabel = (role: string) =>
		data.m.roles[role as keyof typeof data.m.roles] ?? role;

	const coaches = $derived(data.members.filter((m) => m.role === 'coach'));
	const players = $derived(data.members.filter((m) => m.role !== 'coach'));
</script>

<svelte:head>
	<title>{data.team.name} — TeamOrg</title>
</svelte:head>

<a
	href="/app/teams"
	class="mb-4 inline-flex items-center gap-1 text-[13px] font-medium text-on-surface-variant hover:text-on-surface"
>
	<ArrowLeft size={16} /> {data.m.common.back}
</a>

<header class="mb-8 flex items-center gap-4">
	<TeamBadge name={data.team.name} appearance={data.team.appearance} size={56} />
	<div>
		<h1 class="font-display text-[28px] font-extrabold text-on-surface">{data.team.name}</h1>
		<p class="text-[13px] text-on-surface-variant">
			{data.team.memberCount} {data.m.teams.members}
		</p>
	</div>
</header>

{#if data.members.length === 0}
	<p class="text-[14px] text-on-surface-variant">{data.m.roster.empty}</p>
{:else}
	{#if coaches.length > 0}
		<section class="mb-6">
			<h2 class="mb-2 text-[12px] font-semibold uppercase tracking-wide text-on-surface-variant">
				{data.m.roster.coaches}
			</h2>
			<div class="flex flex-col gap-2">
				{#each coaches as member (member.userId)}
					<a href="/app/teams/{data.team.id}/members/{member.userId}" class="block">
						<MemberRow {member} {roleLabel} />
					</a>
				{/each}
			</div>
		</section>
	{/if}

	{#if players.length > 0}
		<section>
			<h2 class="mb-2 text-[12px] font-semibold uppercase tracking-wide text-on-surface-variant">
				{data.m.roster.players}
			</h2>
			<div class="flex flex-col gap-2">
				{#each players as member (member.userId)}
					<a href="/app/teams/{data.team.id}/members/{member.userId}" class="block">
						<MemberRow {member} {roleLabel} />
					</a>
				{/each}
			</div>
		</section>
	{/if}
{/if}
