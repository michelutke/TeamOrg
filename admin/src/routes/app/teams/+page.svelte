<script lang="ts">
	import { ArrowRight } from 'lucide-svelte';
	import TeamBadge from '$lib/components/TeamBadge.svelte';
	import type { PageData } from './$types';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();
</script>

<svelte:head>
	<title>{data.m.teams.title} — TeamOrg</title>
</svelte:head>

<header class="mb-8">
	<h1 class="font-display text-[28px] font-extrabold text-on-surface">{data.m.teams.title}</h1>
</header>

{#if data.teams.length === 0}
	<p class="text-[14px] text-on-surface-variant">{data.m.home.emptyBody}</p>
{:else}
	<section class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
		{#each data.teams as team (team.id)}
			<a
				href="/app/teams/{team.id}"
				class="group flex items-center gap-4 rounded-[24px] bg-surface p-5 transition-shadow hover:shadow-[0px_4px_16px_0px_rgba(0,0,0,0.08)]"
			>
				<TeamBadge name={team.name} appearance={team.appearance} size={48} />
				<div class="min-w-0 flex-1">
					<h3 class="truncate font-display text-[17px] font-bold text-on-surface">{team.name}</h3>
					<p class="text-[12px] text-on-surface-variant">
						{data.m.roles[team.role]} · {team.memberCount}
						{data.m.teams.members}
					</p>
				</div>
				<ArrowRight
					size={18}
					class="text-on-surface-variant opacity-0 transition-opacity group-hover:opacity-100"
				/>
			</a>
		{/each}
	</section>
{/if}
