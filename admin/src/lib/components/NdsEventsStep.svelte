<script lang="ts">
	import {
		seriesImportsAnyEvent,
		type ConflictKeep,
		type ConflictResolutionInput,
		type NdsConflictGroup,
		type NdsSeries,
		type SeriesTimeInput
	} from '$lib/nds-import-wizard';

	interface Props {
		series: NdsSeries[];
		conflicts: NdsConflictGroup[];
		seriesTimes: Map<string, SeriesTimeInput>;
		resolutions: Map<string, ConflictResolutionInput>;
	}

	let {
		series,
		conflicts,
		seriesTimes = $bindable(),
		resolutions = $bindable()
	}: Props = $props();

	const weekdayLabels = ['MO', 'DI', 'MI', 'DO', 'FR', 'SA', 'SO'];
	const symbolLabels: Record<string, string> = { T: 'Training', W: 'Spiel' };

	let expanded = $state(new Set<string>());

	function toggleExpanded(seriesKey: string) {
		const next = new Set(expanded);
		if (next.has(seriesKey)) next.delete(seriesKey);
		else next.add(seriesKey);
		expanded = next;
	}

	function label(s: NdsSeries): string {
		const type = symbolLabels[s.symbol] ?? s.symbol;
		const parts = [
			s.weekday != null ? weekdayLabels[s.weekday] : s.dates[0],
			type,
			s.durationMin ? `${s.durationMin} min` : null,
			`${s.count} Termine`
		];
		return parts.filter(Boolean).join(' · ');
	}

	function conflictFor(seriesKey: string): NdsConflictGroup | undefined {
		return conflicts.find((c) => c.seriesKey === seriesKey);
	}

	function timeFor(seriesKey: string): SeriesTimeInput {
		return seriesTimes.get(seriesKey) ?? { startTime: '', endTime: '', location: '' };
	}

	function endFromDuration(startTime: string, durationMin: number | null): string {
		if (!durationMin || !/^\d{2}:\d{2}$/.test(startTime)) return '';
		const [h, m] = startTime.split(':').map(Number);
		const total = h * 60 + m + durationMin;
		const eh = Math.floor((total / 60) % 24)
			.toString()
			.padStart(2, '0');
		const em = (total % 60).toString().padStart(2, '0');
		return `${eh}:${em}`;
	}

	function updateTime(s: NdsSeries, patch: Partial<SeriesTimeInput>) {
		const current = timeFor(s.seriesKey);
		const next = { ...current, ...patch };
		if (patch.startTime !== undefined && (!current.endTime || current.endTime === '')) {
			next.endTime = endFromDuration(patch.startTime, s.durationMin);
		}
		const map = new Map(seriesTimes);
		map.set(s.seriesKey, next);
		seriesTimes = map;
	}

	function resolutionFor(seriesKey: string): ConflictResolutionInput {
		return resolutions.get(seriesKey) ?? { keep: 'teamorg', overrides: new Map() };
	}

	function setGroupKeep(seriesKey: string, keep: ConflictKeep) {
		const current = resolutionFor(seriesKey);
		const map = new Map(resolutions);
		map.set(seriesKey, { keep, overrides: current.overrides });
		resolutions = map;
	}

	function setDateOverride(seriesKey: string, date: string, keep: ConflictKeep) {
		const current = resolutionFor(seriesKey);
		const overrides = new Map(current.overrides);
		overrides.set(date, keep);
		const map = new Map(resolutions);
		map.set(seriesKey, { keep: current.keep, overrides });
		resolutions = map;
	}
</script>

<div class="min-h-0 flex-1 overflow-y-auto pr-1">
	<p class="mb-3 text-[13px] text-on-surface-variant">
		Lege für jede erkannte Serie Start- und Endzeit fest und löse Terminkonflikte auf.
	</p>
	<div class="flex flex-col gap-3">
		{#each series as s (s.seriesKey)}
			{@const conflict = conflictFor(s.seriesKey)}
			{@const importsEvent = seriesImportsAnyEvent(s, conflicts, resolutions)}
			{@const time = timeFor(s.seriesKey)}
			<div class="rounded-2xl bg-surface-container-high p-4">
				<p class="mb-3 text-[14px] font-medium text-on-surface">{label(s)}</p>

				{#if importsEvent}
					<div class="flex flex-wrap items-end gap-3">
						<label class="flex flex-col gap-1 text-[12px] text-on-surface-variant">
							Start
							<input
								type="time"
								value={time.startTime}
								onchange={(e) =>
									updateTime(s, { startTime: (e.currentTarget as HTMLInputElement).value })}
								class="rounded-xl bg-surface-container px-3 py-2 text-[14px] text-on-surface"
							/>
						</label>
						<label class="flex flex-col gap-1 text-[12px] text-on-surface-variant">
							Ende
							<input
								type="time"
								value={time.endTime}
								onchange={(e) =>
									updateTime(s, { endTime: (e.currentTarget as HTMLInputElement).value })}
								class="rounded-xl bg-surface-container px-3 py-2 text-[14px] text-on-surface"
							/>
						</label>
						<label class="flex flex-1 flex-col gap-1 text-[12px] text-on-surface-variant">
							Ort
							<input
								value={time.location ?? ''}
								onchange={(e) =>
									updateTime(s, { location: (e.currentTarget as HTMLInputElement).value })}
								class="rounded-xl bg-surface-container px-3 py-2 text-[14px] text-on-surface"
							/>
						</label>
					</div>
				{:else}
					<p class="text-[13px] text-on-surface-variant">
						Alle Termine bleiben TeamOrg — keine Uhrzeit nötig.
					</p>
				{/if}

				{#if conflict}
					{@const resolution = resolutionFor(s.seriesKey)}
					<div class="mt-4 rounded-xl bg-surface-container px-3 py-3">
						<p class="mb-2 text-[12px] font-medium text-on-surface-variant">
							{conflict.dates.length} Terminkonflikt(e) mit bestehenden TeamOrg-Terminen
						</p>
						<div class="flex flex-col gap-1">
							<label class="flex items-center gap-2 text-[13px] text-on-surface">
								<input
									type="radio"
									name={`keep-${s.seriesKey}`}
									checked={resolution.keep === 'teamorg'}
									onchange={() => setGroupKeep(s.seriesKey, 'teamorg')}
									class="accent-primary"
								/>
								TeamOrg behalten
							</label>
							<label class="flex items-center gap-2 text-[13px] text-on-surface">
								<input
									type="radio"
									name={`keep-${s.seriesKey}`}
									checked={resolution.keep === 'nds'}
									onchange={() => setGroupKeep(s.seriesKey, 'nds')}
									class="accent-primary"
								/>
								NDS übernehmen
							</label>
						</div>
						<button
							type="button"
							onclick={() => toggleExpanded(s.seriesKey)}
							class="mt-2 cursor-pointer border-none bg-transparent p-0 text-[12px] font-medium text-primary underline"
						>
							{expanded.has(s.seriesKey) ? 'Termine ausblenden' : 'Einzelne Termine anpassen'}
						</button>
						{#if expanded.has(s.seriesKey)}
							<div class="mt-2 flex flex-col gap-2">
								{#each conflict.dates as d (d.date)}
									{@const dateKeep = resolution.overrides.get(d.date) ?? resolution.keep}
									<div class="rounded-lg bg-surface-container-low px-3 py-2">
										<p class="text-[12px] text-on-surface-variant">
											{d.date} — bestehend: <strong>{d.existingEventTitle}</strong>
											({new Date(d.existingEventStart).toLocaleString('de-CH')})
										</p>
										{#if dateKeep === 'nds'}
											<p class="text-[12px] font-medium text-error">
												Dieser bestehende Termin wird storniert.
											</p>
										{/if}
										<div class="mt-1 flex gap-3">
											<label class="flex items-center gap-1 text-[12px] text-on-surface">
												<input
													type="radio"
													name={`keep-${s.seriesKey}-${d.date}`}
													checked={dateKeep === 'teamorg'}
													onchange={() => setDateOverride(s.seriesKey, d.date, 'teamorg')}
													class="accent-primary"
												/>
												TeamOrg
											</label>
											<label class="flex items-center gap-1 text-[12px] text-on-surface">
												<input
													type="radio"
													name={`keep-${s.seriesKey}-${d.date}`}
													checked={dateKeep === 'nds'}
													onchange={() => setDateOverride(s.seriesKey, d.date, 'nds')}
													class="accent-primary"
												/>
												NDS
											</label>
										</div>
									</div>
								{/each}
							</div>
						{/if}
					</div>
				{/if}
			</div>
		{/each}
	</div>
</div>
