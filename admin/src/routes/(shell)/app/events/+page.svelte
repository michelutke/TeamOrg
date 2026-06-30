<script lang="ts">
	import { CalendarDays, MapPin, Plus } from 'lucide-svelte';
	import type { PageData } from './$types';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();

	type Att = { confirmed: number; maybe: number; declined: number; mine: string | null };
	let att = $state<Record<string, Att>>(data.attendance);
	// Resync when the page data changes (e.g. team filter navigation).
	$effect(() => {
		att = data.attendance;
	});

	function applyOptimistic(c: Att, status: string): Att {
		const n: Att = { confirmed: c.confirmed, maybe: c.maybe, declined: c.declined, mine: status };
		const bump = (s: string | null, d: number) => {
			if (s === 'confirmed') n.confirmed = Math.max(0, n.confirmed + d);
			else if (s === 'unsure') n.maybe = Math.max(0, n.maybe + d);
			else if (s === 'declined' || s === 'declined-auto') n.declined = Math.max(0, n.declined + d);
		};
		if (c.mine !== status) {
			bump(c.mine, -1);
			bump(status, +1);
		}
		return n;
	}

	async function respond(eventId: string, status: string, e: Event) {
		e.preventDefault();
		e.stopPropagation();
		let reason: string | null = null;
		if (status === 'unsure') {
			reason = prompt('Kurzer Grund für „Unsicher"');
			if (reason === null) return; // cancelled
			if (!reason.trim()) reason = '—';
		}
		const prev: Att = att[eventId] ?? { confirmed: 0, maybe: 0, declined: 0, mine: null };
		att = { ...att, [eventId]: applyOptimistic(prev, status) };
		try {
			const res = await fetch('/app/events/rsvp', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ eventId, status, reason })
			});
			if (!res.ok) att = { ...att, [eventId]: prev };
		} catch {
			att = { ...att, [eventId]: prev };
		}
	}

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

<header class="mb-6 flex items-center justify-between">
	<h1 class="font-display text-[28px] font-extrabold text-on-surface">{data.m.events.title}</h1>
	{#if data.canCreate}
		<a
			href="/app/events/new"
			class="flex items-center gap-2 rounded-full bg-primary px-5 py-3 text-[14px] font-bold text-on-primary hover:opacity-90"
		>
			<Plus size={18} /> {data.m.eventForm.newTitle}
		</a>
	{/if}
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
			<div class="rounded-2xl bg-surface px-4 py-3 transition-shadow hover:shadow-[0px_4px_16px_0px_rgba(0,0,0,0.06)]">
			<a
				href="/app/events/{event.id}"
				class="flex items-center gap-4"
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
						{#if event.externalStatus === 'postponed'}
							<span class="rounded-full bg-[#FFF3CD] px-2 py-0.5 text-[10px] font-semibold text-[#7A5B00]">
								{data.m.swissvolley.postponed}
							</span>
						{/if}
						{#if event.externalSource === 'swissvolley'}
							<span
								class="rounded-full bg-surface-container-high px-2 py-0.5 text-[10px] font-semibold text-on-surface-variant"
							>
								{data.m.swissvolley.sourceLabel}
							</span>
						{/if}
						{#if event.externalSource === 'nds' && event.presentCount > 0}
							<span class="rounded-full bg-success-container px-2 py-0.5 text-[10px] font-semibold text-success">
								{event.presentCount} anwesend
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
			{#if event.status !== 'cancelled'}
				{@const a = att[event.id] ?? { confirmed: 0, maybe: 0, declined: 0, mine: null }}
				<div class="mt-3 flex gap-2">
					<button
						type="button"
						onclick={(e) => respond(event.id, 'confirmed', e)}
						class="flex h-9 flex-1 items-center justify-center gap-1.5 rounded-full text-[13px] font-semibold transition-colors hover:opacity-80 {a.mine ===
						'confirmed'
							? 'bg-emerald-600 text-white'
							: 'bg-surface-container-high text-on-surface-variant'}"
					>
						✓ {a.confirmed}
					</button>
					<button
						type="button"
						onclick={(e) => respond(event.id, 'declined', e)}
						class="flex h-9 flex-1 items-center justify-center gap-1.5 rounded-full text-[13px] font-semibold transition-colors hover:opacity-80 {a.mine ===
							'declined' || a.mine === 'declined-auto'
							? 'bg-red-100 text-red-700'
							: 'bg-surface-container-high text-on-surface-variant'}"
					>
						✗ {a.declined}
					</button>
					<button
						type="button"
						onclick={(e) => respond(event.id, 'unsure', e)}
						class="flex h-9 flex-1 items-center justify-center gap-1.5 rounded-full text-[13px] font-semibold transition-colors hover:opacity-80 {a.mine ===
						'unsure'
							? 'bg-amber-100 text-amber-800'
							: 'bg-surface-container-high text-on-surface-variant'}"
					>
						? {a.maybe}
					</button>
				</div>
			{/if}
			</div>
		{/each}
	</div>
{/if}
