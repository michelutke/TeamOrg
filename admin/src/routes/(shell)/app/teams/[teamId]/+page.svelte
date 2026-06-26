<script lang="ts">
	import { ArrowLeft, CalendarClock, Download, Mail } from 'lucide-svelte';
	import { goto } from '$app/navigation';
	import TeamBadge from '$lib/components/TeamBadge.svelte';
	import MemberRow from '$lib/components/MemberRow.svelte';
	import type { PageData, ActionData } from './$types';

	interface ImportableSeries {
		seriesId: string;
		patternType: string;
		weekdays: number[] | null;
		intervalDays: number | null;
		templateStartTime: string;
		templateEndTime: string;
		templateMeetupTime: string | null;
		templateTitle: string;
		templateType: string;
		templateLocation: string | null;
		templateMinAttendees: number | null;
		seriesStartDate: string;
		seriesEndDate: string | null;
		label: string;
	}

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();

	let inviteFor = $state<string | null>(null);

	const roleLabel = (role: string) =>
		data.m.roles[role as keyof typeof data.m.roles] ?? role;

	const coaches = $derived(data.members.filter((m) => m.role === 'coach'));
	const players = $derived(data.members.filter((m) => m.role !== 'coach'));

	// Schedule carry-over: offer importing the predecessor team's recurring series
	// (design §15). Only when the coach/manager can manage AND a predecessor with
	// series exists AND this team has none of its own yet.
	let carryOver = $state<ImportableSeries[] | null>(null);
	let carryOverLoading = $state(false);
	let carryOverError = $state(false);
	let showSeriesPicker = $state(false);

	async function loadCarryOver() {
		if (!data.canManage) return;
		carryOverLoading = true;
		carryOverError = false;
		try {
			const res = await fetch(`/app/teams/${data.team.id}/importable-series`);
			if (!res.ok) {
				carryOverError = true;
				return;
			}
			const result = (await res.json()) as {
				hasOwnSeries: boolean;
				series: ImportableSeries[];
			};
			if (!result.hasOwnSeries && result.series.length > 0) carryOver = result.series;
		} catch {
			carryOverError = true;
		} finally {
			carryOverLoading = false;
		}
	}

	// Pre-fill the existing create form from the chosen template (start/end combine
	// the new season's start date with the template times; coach edits everything).
	function takeOver(s: ImportableSeries) {
		const startAt = `${s.seriesStartDate}T${s.templateStartTime}`;
		const endAt = `${s.seriesStartDate}T${s.templateEndTime}`;
		const params = new URLSearchParams();
		params.set('teamId', data.team.id);
		params.set('title', s.templateTitle);
		params.set('type', s.templateType);
		params.set('startAt', startAt);
		params.set('endAt', endAt);
		if (s.templateMeetupTime)
			params.set('meetupAt', `${s.seriesStartDate}T${s.templateMeetupTime}`);
		if (s.templateLocation) params.set('location', s.templateLocation);
		if (s.templateMinAttendees != null)
			params.set('minAttendees', String(s.templateMinAttendees));
		params.set('patternType', s.patternType);
		if (s.weekdays) params.set('weekdays', s.weekdays.join(','));
		if (s.intervalDays != null) params.set('intervalDays', String(s.intervalDays));
		if (s.seriesEndDate) params.set('seriesEndDate', s.seriesEndDate);
		goto(`/app/events/new?${params.toString()}`);
	}

	loadCarryOver();
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

{#if carryOver}
	<section class="mb-8 rounded-[28px] bg-surface-container-low p-5">
		<div class="flex items-start gap-3">
			<div class="rounded-2xl bg-secondary-container p-2 text-on-secondary-container">
				<CalendarClock size={20} />
			</div>
			<div class="flex flex-col gap-1">
				<h2 class="text-[16px] font-bold text-on-surface">{data.m.schedule.carryOverTitle}</h2>
				<p class="text-[13px] text-on-surface-variant">{data.m.schedule.carryOverBody}</p>
			</div>
		</div>

		{#if !showSeriesPicker}
			<button
				type="button"
				onclick={() => (showSeriesPicker = true)}
				class="mt-4 cursor-pointer rounded-full border-none bg-primary px-5 py-2.5 text-[14px] font-bold text-on-primary hover:opacity-90"
			>
				{data.m.schedule.carryOverAction}
			</button>
		{:else}
			<p class="mb-2 mt-4 text-[12px] font-medium text-primary">{data.m.schedule.pickSeries}</p>
			<ul class="flex flex-col gap-2">
				{#each carryOver as s (s.seriesId)}
					<li>
						<button
							type="button"
							onclick={() => takeOver(s)}
							class="flex w-full cursor-pointer items-center justify-between gap-3 rounded-2xl border-none bg-surface-container-high px-4 py-3 text-left hover:opacity-90"
						>
							<span class="text-[14px] font-medium text-on-surface">{s.label}</span>
							<span class="text-[13px] font-medium text-primary">{data.m.schedule.takeOver}</span>
						</button>
					</li>
				{/each}
			</ul>
		{/if}
	</section>
{/if}

{#if data.canManage && data.ndsMembers.length > 0}
	<section class="mb-8 rounded-[28px] bg-surface-container-low p-5">
		<div class="mb-4 flex items-center justify-between gap-3">
			<h2 class="text-[16px] font-bold text-on-surface">NDS / J+S</h2>
			{#if data.ndsPreflight?.ok}
				<a
					href="/app/teams/{data.team.id}/nds/export"
					class="inline-flex items-center gap-2 rounded-full border-none bg-primary px-5 py-2.5 text-[14px] font-bold text-on-primary no-underline hover:opacity-90"
				>
					<Download size={16} /> NDS-Export (CSV)
				</a>
			{/if}
		</div>

		{#if data.ndsPreflight && !data.ndsPreflight.ok}
			<div class="mb-4 rounded-2xl bg-error-container px-4 py-3 text-[13px] text-on-surface">
				<p class="font-medium">Export noch nicht möglich:</p>
				<ul class="mt-1 list-disc pl-5">
					{#each data.ndsPreflight.issues.filter((i) => i.severity === 'error') as issue (issue.code)}
						<li>{issue.message}</li>
					{/each}
				</ul>
			</div>
		{/if}
		{#if data.ndsPreflight?.issues.some((i) => i.severity === 'warning')}
			<div class="mb-4 rounded-2xl bg-surface-container-high px-4 py-3 text-[13px] text-on-surface-variant">
				{#each data.ndsPreflight.issues.filter((i) => i.severity === 'warning') as issue (issue.code)}
					<p>⚠ {issue.message}</p>
				{/each}
			</div>
		{/if}

		{#if form?.ndsInvite}
			<div class="mb-4 rounded-2xl bg-success-container px-4 py-3 text-[13px] text-on-surface">
				<p class="font-medium">Einladungslink erstellt:</p>
				<code class="break-all text-[12px]">{form.ndsInvite.inviteUrl}</code>
			</div>
		{/if}

		<div class="flex flex-col gap-2">
			{#each data.ndsMembers as m (m.id)}
				<div class="rounded-2xl bg-surface-container-high px-4 py-3">
					<div class="flex flex-wrap items-center justify-between gap-2">
						<div class="flex items-center gap-2">
							<span class="text-[14px] font-medium text-on-surface">{m.firstName} {m.lastName}</span>
							<span class="rounded-full bg-surface-container px-2 py-0.5 text-[11px] text-on-surface-variant">
								{m.funktion}
							</span>
							{#if m.claimed}
								<span class="rounded-full bg-success-container px-2 py-0.5 text-[11px] text-on-surface">verknüpft</span>
							{/if}
						</div>
						<div class="flex items-center gap-2">
							<form method="POST" action="?/setNdsPersonNumber" class="flex items-center gap-2">
								<input type="hidden" name="memberId" value={m.id} />
								<input
									name="personNumber"
									value={m.personNumber ?? ''}
									placeholder="Personennr."
									class="w-32 rounded-lg bg-surface-container px-2 py-1 text-[13px] text-on-surface {m.personNumber ? '' : 'ring-1 ring-error'}"
								/>
								<button
									type="submit"
									class="cursor-pointer rounded-full border-none bg-secondary-container px-3 py-1 text-[12px] font-medium text-on-secondary-container hover:opacity-90"
								>Speichern</button>
							</form>
							{#if !m.claimed}
								<button
									type="button"
									onclick={() => (inviteFor = inviteFor === m.id ? null : m.id)}
									class="inline-flex cursor-pointer items-center gap-1 rounded-full border border-outline-variant bg-transparent px-3 py-1 text-[12px] font-medium text-on-surface-variant hover:bg-surface-container"
								>
									<Mail size={13} /> Einladen
								</button>
							{/if}
						</div>
					</div>
					{#if inviteFor === m.id}
						<form method="POST" action="?/inviteNdsMember" class="mt-2 flex items-center gap-2">
							<input type="hidden" name="memberId" value={m.id} />
							<input
								name="email"
								type="email"
								placeholder="E-Mail (optional – sonst nur Link)"
								class="flex-1 rounded-lg bg-surface-container px-2 py-1 text-[13px] text-on-surface"
							/>
							<button
								type="submit"
								class="cursor-pointer rounded-full border-none bg-primary px-4 py-1 text-[12px] font-bold text-on-primary hover:opacity-90"
							>Link erstellen</button>
						</form>
					{/if}
				</div>
			{/each}
		</div>
		{#if form?.ndsError}
			<p class="mt-3 text-[12px] font-medium text-error">
				{form.ndsError === 'badNumber' ? 'Ungültige Personennummer.' : 'Aktion fehlgeschlagen.'}
			</p>
		{/if}
	</section>
{/if}

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
