<script lang="ts">
	import { Users, Inbox, ArrowRight } from 'lucide-svelte';
	import type { PageData } from './$types';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();

	const roleLabel = $derived(data.m.roles);
</script>

<svelte:head>
	<title>{data.m.nav.start} — TeamOrg</title>
</svelte:head>

<header class="mb-8">
	<h1 class="font-display text-[28px] font-extrabold text-on-surface">
		{data.m.home.greeting} {data.user.displayName.split(' ')[0]}
	</h1>
	<p class="text-[14px] text-on-surface-variant">{data.m.home.teamsSub}</p>
</header>

{#if data.teams.length === 0}
	<div
		class="mx-auto mt-16 flex max-w-[440px] flex-col items-center gap-4 rounded-[28px] bg-surface px-8 py-12 text-center"
	>
		<span class="flex size-16 items-center justify-center rounded-full bg-primary-container">
			<Inbox size={28} class="text-on-primary-container" />
		</span>
		<h2 class="font-display text-[20px] font-bold text-on-surface">{data.m.home.emptyTitle}</h2>
		<p class="text-[14px] text-on-surface-variant">{data.m.home.emptyBody}</p>
	</div>
{:else}
	<section class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
		{#each data.teams as team (team.id)}
			<a
				href="/app/teams/{team.id}"
				class="group flex flex-col gap-3 rounded-[24px] bg-surface p-5 transition-shadow hover:shadow-[0px_4px_16px_0px_rgba(0,0,0,0.08)]"
			>
				<div class="flex items-center justify-between">
					<span class="flex size-10 items-center justify-center rounded-full bg-secondary-container">
						<Users size={20} class="text-on-secondary-container" />
					</span>
					<span
						class="rounded-full bg-primary-container px-3 py-1 text-[11px] font-semibold text-on-primary-container"
					>
						{roleLabel[team.role]}
					</span>
				</div>
				<h3 class="font-display text-[17px] font-bold text-on-surface">{team.name}</h3>
				<span
					class="mt-1 flex items-center gap-1 text-[13px] font-medium text-primary opacity-0 transition-opacity group-hover:opacity-100"
				>
					{data.m.home.open} <ArrowRight size={14} />
				</span>
			</a>
		{/each}
	</section>
{/if}
