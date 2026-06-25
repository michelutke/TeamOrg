<script lang="ts">
	import { CalendarDays, MapPin } from 'lucide-svelte';
	import type { PageData } from './$types';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();

	const typeLabel = (t: string) =>
		data.m.eventTypes[t as keyof typeof data.m.eventTypes] ?? t;

	function fmtDate(iso: string): string {
		return new Date(iso).toLocaleDateString(data.lang, {
			weekday: 'short',
			day: 'numeric',
			month: 'short'
		});
	}
	function fmtTime(iso: string): string {
		return new Date(iso).toLocaleTimeString(data.lang, { hour: '2-digit', minute: '2-digit' });
	}
</script>

<svelte:head>
	<title>{data.m.events.title} — TeamOrg</title>
</svelte:head>

<header class="mb-6">
	<h1 class="font-display text-[28px] font-extrabold text-on-surface">{data.m.events.title}</h1>
</header>

{#if data.teams.length > 1}
	<div class="mb-6 flex flex-wrap gap-2">
		<a
			href="/app/events"
			class="rounded-full px-4 py-1.5 text-[13px] font-medium transition-colors {!data.teamFilter
				? 'bg-secondary-container text-on-secondary-container'
				: 'bg-surface text-on-surface-variant hover:bg-surface-container-high'}"
		>
			{data.m.events.allTeams}
		</a>
		{#each data.teams as team (team.id)}
			<a
				href="/app/events?team={team.id}"
				class="rounded-full px-4 py-1.5 text-[13px] font-medium transition-colors {data.teamFilter ===
				team.id
					? 'bg-secondary-container text-on-secondary-container'
					: 'bg-surface text-on-surface-variant hover:bg-surface-container-high'}"
			>
				{team.name}
			</a>
		{/each}
	</div>
{/if}

{#if data.events.length === 0}
	<p class="text-[14px] text-on-surface-variant">{data.m.events.none}</p>
{:else}
	<div class="flex flex-col gap-2">
		{#each data.events as { event, matchedTeams } (event.id)}
			<a
				href="/app/events/{event.id}"
				class="flex items-center gap-4 rounded-2xl bg-surface px-4 py-3 transition-shadow hover:shadow-[0px_4px_16px_0px_rgba(0,0,0,0.06)]"
			>
				<div
					class="flex size-12 shrink-0 flex-col items-center justify-center rounded-2xl bg-primary-container text-on-primary-container"
				>
					<span class="text-[10px] font-medium uppercase">{fmtDate(event.startAt).split(' ')[0]}</span>
					<span class="text-[16px] font-bold leading-none">{new Date(event.startAt).getDate()}</span>
				</div>
				<div class="min-w-0 flex-1">
					<div class="flex items-center gap-2">
						<h3 class="truncate text-[15px] font-semibold text-on-surface">{event.title}</h3>
						{#if event.status === 'cancelled'}
							<span class="rounded-full bg-error-container px-2 py-0.5 text-[10px] font-semibold text-error">
								{typeLabel(event.type)}
							</span>
						{/if}
					</div>
					<p class="truncate text-[12px] text-on-surface-variant">
						{fmtTime(event.startAt)} · {typeLabel(event.type)}
						{#if matchedTeams.length}· {matchedTeams.map((t) => t.name).join(', ')}{/if}
					</p>
				</div>
				{#if event.location}
					<span class="hidden items-center gap-1 text-[12px] text-on-surface-variant sm:flex">
						<MapPin size={14} />{event.location}
					</span>
				{:else}
					<CalendarDays size={16} class="text-on-surface-variant" />
				{/if}
			</a>
		{/each}
	</div>
{/if}
