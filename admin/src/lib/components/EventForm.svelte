<script lang="ts" module>
	export interface EventFormValues {
		title: string;
		type: string;
		startAt: string | null;
		endAt: string | null;
		meetupAt: string | null;
		location: string | null;
		description: string | null;
		minAttendees: number | null;
		teamIds: string[];
		recurring?: {
			patternType: string;
			weekdays?: number[] | null;
			intervalDays?: number | null;
			seriesEndDate?: string | null;
		} | null;
	}
</script>

<script lang="ts">
	import { enhance } from '$app/forms';
	import type { Dict } from '$lib/i18n';

	interface Props {
		teams: { id: string; name: string }[];
		m: Dict;
		heading: string;
		submitLabel: string;
		action?: string;
		error?: string | null;
		initial?: Partial<EventFormValues>;
	}

	let {
		teams,
		m,
		heading,
		submitLabel,
		action = '',
		error = null,
		initial = {}
	}: Props = $props();

	// datetime-local <-> ISO. The visible inputs (no name) hold local values; hidden
	// inputs carry the ISO strings the API expects, derived reactively.
	function toLocal(iso: string | null | undefined): string {
		if (!iso) return '';
		const d = new Date(iso);
		const off = d.getTimezoneOffset() * 60000;
		return new Date(d.getTime() - off).toISOString().slice(0, 16);
	}
	function toISO(local: string): string {
		return local ? new Date(local).toISOString() : '';
	}

	let title = $state(initial.title ?? '');
	let type = $state(initial.type ?? 'training');
	let startLocal = $state(toLocal(initial.startAt));
	let endLocal = $state(toLocal(initial.endAt));
	let meetupLocal = $state(toLocal(initial.meetupAt));
	let location = $state(initial.location ?? '');
	let description = $state(initial.description ?? '');
	let minAttendees = $state(initial.minAttendees != null ? String(initial.minAttendees) : '');
	let selected = $state<string[]>(initial.teamIds ?? (teams.length === 1 ? [teams[0].id] : []));

	function toggleTeam(id: string) {
		selected = selected.includes(id) ? selected.filter((t) => t !== id) : [...selected, id];
	}

	// Recurrence (0=Mon..6=Sun, matching the backend's weekday encoding).
	let recurringEnabled = $state(initial.recurring != null);
	let patternType = $state(initial.recurring?.patternType ?? 'weekly');
	let weekdays = $state<number[]>(initial.recurring?.weekdays ?? []);
	let intervalDays = $state(
		initial.recurring?.intervalDays != null ? String(initial.recurring.intervalDays) : ''
	);
	let seriesEndDate = $state(initial.recurring?.seriesEndDate ?? '');

	function toggleWeekday(d: number) {
		weekdays = weekdays.includes(d) ? weekdays.filter((w) => w !== d) : [...weekdays, d].sort();
	}

	const types = ['training', 'match', 'other'] as const;
	const patterns = ['weekly', 'daily', 'custom'] as const;
	const weekdayKeys = [
		'weekdayMon',
		'weekdayTue',
		'weekdayWed',
		'weekdayThu',
		'weekdayFri',
		'weekdaySat',
		'weekdaySun'
	] as const;
	const inputCls =
		'w-full rounded-2xl bg-surface-container-high px-4 py-3 text-[14px] text-on-surface outline-none';
</script>

<form method="POST" {action} use:enhance class="mx-auto flex max-w-[640px] flex-col gap-5">
	<h1 class="font-display text-[26px] font-extrabold text-on-surface">{heading}</h1>

	<!-- Teams -->
	<fieldset class="flex flex-col gap-2">
		<legend class="mb-1 text-[12px] font-medium text-primary">{m.eventForm.fTeams}</legend>
		<div class="flex flex-wrap gap-2">
			{#each teams as team (team.id)}
				<button
					type="button"
					onclick={() => toggleTeam(team.id)}
					class="rounded-full px-4 py-2 text-[13px] font-medium transition-colors {selected.includes(
						team.id
					)
						? 'bg-secondary-container text-on-secondary-container'
						: 'bg-surface-container-high text-on-surface-variant'}"
				>
					{team.name}
				</button>
			{/each}
		</div>
		{#each selected as id (id)}
			<input type="hidden" name="teamIds" value={id} />
		{/each}
	</fieldset>

	<label class="flex flex-col gap-1">
		<span class="text-[12px] font-medium text-primary">{m.eventForm.fTitle}</span>
		<input name="title" bind:value={title} required class={inputCls} />
	</label>

	<label class="flex flex-col gap-1">
		<span class="text-[12px] font-medium text-primary">{m.eventForm.fType}</span>
		<select name="type" bind:value={type} class={inputCls}>
			{#each types as t (t)}
				<option value={t}>{m.eventTypes[t]}</option>
			{/each}
		</select>
	</label>

	<div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
		<label class="flex flex-col gap-1">
			<span class="text-[12px] font-medium text-primary">{m.eventForm.fStart}</span>
			<input type="datetime-local" bind:value={startLocal} required class={inputCls} />
			<input type="hidden" name="startAt" value={toISO(startLocal)} />
		</label>
		<label class="flex flex-col gap-1">
			<span class="text-[12px] font-medium text-primary">{m.eventForm.fEnd}</span>
			<input type="datetime-local" bind:value={endLocal} required class={inputCls} />
			<input type="hidden" name="endAt" value={toISO(endLocal)} />
		</label>
	</div>

	<label class="flex flex-col gap-1">
		<span class="text-[12px] font-medium text-primary">{m.eventForm.fMeetup}</span>
		<input type="datetime-local" bind:value={meetupLocal} class={inputCls} />
		<input type="hidden" name="meetupAt" value={toISO(meetupLocal)} />
	</label>

	<label class="flex flex-col gap-1">
		<span class="text-[12px] font-medium text-primary">{m.eventForm.fLocation}</span>
		<input name="location" bind:value={location} class={inputCls} />
	</label>

	<label class="flex flex-col gap-1">
		<span class="text-[12px] font-medium text-primary">{m.eventForm.fDescription}</span>
		<textarea name="description" bind:value={description} rows="3" class={inputCls}></textarea>
	</label>

	<label class="flex flex-col gap-1">
		<span class="text-[12px] font-medium text-primary">{m.eventForm.fMinAttendees}</span>
		<input type="number" name="minAttendees" bind:value={minAttendees} min="0" class={inputCls} />
	</label>

	<!-- Recurrence -->
	<fieldset class="flex flex-col gap-3 rounded-2xl bg-surface-container-low p-4">
		<legend class="px-1 text-[12px] font-medium text-primary">
			{m.eventForm.recurringSection}
		</legend>
		<label class="flex cursor-pointer items-center gap-2">
			<input
				type="checkbox"
				bind:checked={recurringEnabled}
				class="h-4 w-4 accent-primary"
			/>
			<span class="text-[14px] text-on-surface">{m.eventForm.recurringEnable}</span>
		</label>

		{#if recurringEnabled}
			<input type="hidden" name="recurringEnabled" value="true" />
			<input type="hidden" name="patternType" value={patternType} />

			<label class="flex flex-col gap-1">
				<span class="text-[12px] font-medium text-primary">{m.eventForm.fPattern}</span>
				<select bind:value={patternType} class={inputCls}>
					{#each patterns as p (p)}
						<option value={p}>
							{p === 'weekly'
								? m.eventForm.patternWeekly
								: p === 'daily'
									? m.eventForm.patternDaily
									: m.eventForm.patternCustom}
						</option>
					{/each}
				</select>
			</label>

			{#if patternType === 'weekly'}
				<fieldset class="flex flex-col gap-2">
					<legend class="mb-1 text-[12px] font-medium text-primary">
						{m.eventForm.fWeekdays}
					</legend>
					<div class="flex flex-wrap gap-2">
						{#each weekdayKeys as key, d (d)}
							<button
								type="button"
								onclick={() => toggleWeekday(d)}
								class="rounded-full px-3 py-2 text-[13px] font-medium transition-colors {weekdays.includes(
									d
								)
									? 'bg-secondary-container text-on-secondary-container'
									: 'bg-surface-container-high text-on-surface-variant'}"
							>
								{m.eventForm[key]}
							</button>
						{/each}
					</div>
					{#each weekdays as d (d)}
						<input type="hidden" name="weekdays" value={d} />
					{/each}
				</fieldset>
			{/if}

			{#if patternType === 'custom'}
				<label class="flex flex-col gap-1">
					<span class="text-[12px] font-medium text-primary">{m.eventForm.fIntervalDays}</span>
					<input
						type="number"
						name="intervalDays"
						bind:value={intervalDays}
						min="1"
						class={inputCls}
					/>
				</label>
			{/if}

			<label class="flex flex-col gap-1">
				<span class="text-[12px] font-medium text-primary">{m.eventForm.fSeriesEnd}</span>
				<input type="date" name="seriesEndDate" bind:value={seriesEndDate} class={inputCls} />
			</label>
		{/if}
	</fieldset>

	{#if error}
		<p class="text-[13px] font-medium text-error">{error}</p>
	{/if}

	<div class="flex gap-3">
		<a
			href="/app/events"
			class="rounded-full bg-surface-container-high px-6 py-3 text-[14px] font-medium text-on-surface"
		>
			{m.common.cancel}
		</a>
		<button
			type="submit"
			disabled={selected.length === 0}
			class="flex-1 rounded-full border-none bg-primary py-3 text-[15px] font-bold text-on-primary hover:opacity-90 disabled:opacity-40"
		>
			{submitLabel}
		</button>
	</div>
</form>
