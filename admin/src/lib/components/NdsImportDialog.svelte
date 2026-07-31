<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { X } from 'lucide-svelte';
	import NdsMappingStep from './NdsMappingStep.svelte';
	import NdsEventsStep from './NdsEventsStep.svelte';
	import {
		assemblePayload,
		conflictCounts,
		defaultMappings,
		mergeRows,
		step3Gate,
		type ConflictResolutionInput,
		type MappingChoice,
		type NdsParseResponse,
		type SeriesTimeInput
	} from '$lib/nds-import-wizard';

	interface TeamOption {
		id: string;
		name: string;
	}
	interface TeamMemberOption {
		userId: string;
		displayName: string;
	}
	interface ImportResult {
		teamId: string;
		membersImported: number;
		eventsCreated: number;
		attendanceImported: number;
	}

	interface Props {
		clubId: string;
		onClose: () => void;
		// Manage-page entry: club's existing teams for the picker + "Neues Team" option.
		teams?: TeamOption[];
		// Team-page entry: fixed target team, roster already loaded by the page.
		fixedTeamId?: string;
		fixedTeamName?: string;
		teamMembers?: TeamMemberOption[];
	}

	let { clubId, onClose, teams = [], fixedTeamId, fixedTeamName, teamMembers = [] }: Props = $props();

	const parseUrl = fixedTeamId ? `/app/teams/${fixedTeamId}/nds/parse` : `/manage/${clubId}/nds/parse`;
	const importUrl = fixedTeamId
		? `/app/teams/${fixedTeamId}/nds/import`
		: `/manage/${clubId}/nds/import`;

	type Step = 'files' | 'mapping' | 'events' | 'confirm' | 'done';
	let step = $state<Step>('files');
	let busy = $state(false);
	let errorMsg = $state<string | null>(null);

	// Step 1 — Dateien
	let teilnehmendeFile = $state<File | null>(null);
	let leiterFile = $state<File | null>(null);
	let listeFile = $state<File | null>(null);
	let teamMode = $state<'existing' | 'new'>(teams.length > 0 ? 'existing' : 'new');
	let selectedTeamId = $state('');
	let newTeamName = $state('');

	const hasFile = $derived(!!teilnehmendeFile || !!leiterFile || !!listeFile);
	const teamChosen = $derived(
		!!fixedTeamId ||
			(teamMode === 'existing' ? selectedTeamId !== '' : newTeamName.trim() !== '')
	);

	// Resolved once parse succeeds.
	let parsed = $state<NdsParseResponse | null>(null);
	let resolvedTeamId = $state<string | null>(null);
	let resolvedTeamMembers = $state<TeamMemberOption[]>([]);
	let mappings = $state<Map<string, MappingChoice>>(new Map());
	let seriesTimes = $state<Map<string, SeriesTimeInput>>(new Map());
	let resolutions = $state<Map<string, ConflictResolutionInput>>(new Map());
	let nutzergruppe = $state('');
	let attendanceMode = $state<'keep' | 'discard'>('keep');
	let result = $state<ImportResult | null>(null);

	const hasAwl = $derived(!!parsed?.anwesenheitsliste);
	const importEvents = $derived(hasAwl);
	// Mapping-table rows: the parse response's `persons` only carries the Teilnehmende/Leiter file
	// rows — the AWL roster is merged in server-side for suggestions but never returned as rows —
	// so the table union-merges them client-side (mirrors the server's own mergeMemberRows).
	const mappingRows = $derived(
		parsed ? mergeRows(parsed.anwesenheitsliste?.members, parsed.persons) : []
	);

	async function fetchTeamMembers(teamId: string): Promise<TeamMemberOption[]> {
		if (fixedTeamId) return teamMembers;
		try {
			const res = await fetch(`/manage/${clubId}/nds/team-members?teamId=${teamId}`);
			if (!res.ok) return [];
			return (await res.json()) as TeamMemberOption[];
		} catch {
			return [];
		}
	}

	async function submitFiles(e: SubmitEvent) {
		e.preventDefault();
		if (!hasFile || !teamChosen) return;
		busy = true;
		errorMsg = null;
		try {
			const form = new FormData();
			if (teilnehmendeFile) form.append('teilnehmende', teilnehmendeFile);
			if (leiterFile) form.append('leiter', leiterFile);
			if (listeFile) form.append('anwesenheitsliste', listeFile);
			const teamIdForParse = fixedTeamId ?? (teamMode === 'existing' ? selectedTeamId : '');
			if (teamIdForParse) form.append('teamId', teamIdForParse);

			const res = await fetch(parseUrl, { method: 'POST', body: form });
			if (res.status === 422) {
				errorMsg = 'Eine der Dateien konnte nicht gelesen werden.';
				return;
			}
			if (!res.ok) {
				errorMsg = 'Die Dateien konnten nicht gelesen werden.';
				return;
			}
			const response = (await res.json()) as NdsParseResponse;
			parsed = response;
			resolvedTeamId = fixedTeamId ?? (teamMode === 'existing' ? selectedTeamId : response.linkedTeamId);
			resolvedTeamMembers = resolvedTeamId ? await fetchTeamMembers(resolvedTeamId) : [];
			mappings = defaultMappings(response.memberSuggestions);
			resolutions = new Map(
				response.conflicts.map((c) => [c.seriesKey, { keep: 'teamorg', overrides: new Map() }])
			);
			seriesTimes = new Map();
			nutzergruppe = '';
			step = 'mapping';
		} catch {
			errorMsg = 'Eine der Dateien konnte nicht gelesen werden.';
		} finally {
			busy = false;
		}
	}

	function goToEventsOrConfirm() {
		step = hasAwl ? 'events' : 'confirm';
	}

	const eventsGateOk = $derived(
		parsed ? step3Gate(parsed.series, parsed.conflicts, seriesTimes, resolutions) : false
	);

	const counts = $derived.by(() => {
		let mapped = 0;
		let created = 0;
		let skipped = 0;
		for (const c of mappings.values()) {
			if (c.action === 'map') mapped++;
			else if (c.action === 'create') created++;
			else skipped++;
		}
		const events = parsed ? conflictCounts(parsed.series, parsed.conflicts, resolutions) : null;
		return { mapped, created, skipped, events };
	});

	async function submitImport() {
		if (!parsed) return;
		busy = true;
		errorMsg = null;
		try {
			const payload = assemblePayload({
				teamId: resolvedTeamId,
				createTeamName: !resolvedTeamId ? newTeamName : undefined,
				nutzergruppe,
				parsed: parsed.anwesenheitsliste,
				persons: parsed.persons,
				importEvents,
				attendanceMode,
				mappings,
				seriesTimes,
				resolutions
			});
			const res = await fetch(importUrl, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify(payload)
			});
			if (res.status === 409) {
				errorMsg =
					'Dieses Angebot ist bereits mit einem Team verknüpft. Datei neu einlesen — der Import aktualisiert dann automatisch das verknüpfte Team.';
				return;
			}
			if (!res.ok) {
				errorMsg = 'Import fehlgeschlagen.';
				return;
			}
			result = (await res.json()) as ImportResult;
			step = 'done';
			await invalidateAll();
		} catch {
			errorMsg = 'Import fehlgeschlagen.';
		} finally {
			busy = false;
		}
	}
</script>

<div
	class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
	role="presentation"
	onclick={(e) => {
		if (e.target === e.currentTarget) onClose();
	}}
>
	<div
		class="flex max-h-[85vh] w-full max-w-[720px] flex-col rounded-[28px] bg-surface-container-low p-6"
		role="dialog"
		aria-modal="true"
		aria-label="NDS-Import"
	>
		<div class="mb-4 flex items-start justify-between gap-4">
			<div class="flex flex-col gap-1">
				<h2 class="text-[20px] font-bold text-on-surface">NDS-Import</h2>
				<p class="text-[13px] text-on-surface-variant">
					{fixedTeamName ? `Team: ${fixedTeamName}` : 'Dateien → Zuordnung → Events & Konflikte → Bestätigen'}
				</p>
			</div>
			<button
				type="button"
				onclick={onClose}
				aria-label="Schliessen"
				class="cursor-pointer rounded-full border-none bg-transparent p-2 text-on-surface-variant hover:bg-surface-container-high"
			>
				<X size={18} />
			</button>
		</div>

		{#if step === 'files'}
			<form onsubmit={submitFiles} class="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto pr-1">
				{#if !fixedTeamId}
					<div class="flex flex-col gap-2">
						<p class="text-[13px] font-medium text-on-surface">Team</p>
						{#if teams.length > 0}
							<label class="flex items-center gap-2 text-[13px] text-on-surface">
								<input type="radio" bind:group={teamMode} value="existing" class="accent-primary" />
								Bestehendes Team
							</label>
							{#if teamMode === 'existing'}
								<select
									bind:value={selectedTeamId}
									class="rounded-xl bg-surface-container-high px-3 py-2 text-[14px] text-on-surface"
								>
									<option value="">— wählen —</option>
									{#each teams as t (t.id)}
										<option value={t.id}>{t.name}</option>
									{/each}
								</select>
							{/if}
						{/if}
						<label class="flex items-center gap-2 text-[13px] text-on-surface">
							<input type="radio" bind:group={teamMode} value="new" class="accent-primary" />
							Neues Team
						</label>
						{#if teamMode === 'new'}
							<input
								bind:value={newTeamName}
								placeholder="Team-Name"
								class="rounded-xl bg-surface-container-high px-3 py-2 text-[14px] text-on-surface"
							/>
						{/if}
					</div>
				{/if}

				<label class="flex flex-col gap-1 text-[13px] text-on-surface-variant">
					Teilnehmende (.csv) <span class="text-on-surface-variant/70">– optional</span>
					<input
						type="file"
						accept=".csv"
						onchange={(e) => (teilnehmendeFile = (e.currentTarget as HTMLInputElement).files?.[0] ?? null)}
						class="rounded-2xl bg-surface-container-high px-4 py-3 text-[14px] text-on-surface"
					/>
				</label>
				<label class="flex flex-col gap-1 text-[13px] text-on-surface-variant">
					Leiter/innen (.xlsx) <span class="text-on-surface-variant/70">– optional</span>
					<input
						type="file"
						accept=".xlsx"
						onchange={(e) => (leiterFile = (e.currentTarget as HTMLInputElement).files?.[0] ?? null)}
						class="rounded-2xl bg-surface-container-high px-4 py-3 text-[14px] text-on-surface"
					/>
				</label>
				<label class="flex flex-col gap-1 text-[13px] text-on-surface-variant">
					Anwesenheitsliste (.xlsx)
					<span class="rounded-full bg-primary-container px-2 py-0.5 text-[11px] text-on-primary-container">
						empfohlen
					</span>
					<input
						type="file"
						accept=".xlsx"
						onchange={(e) => (listeFile = (e.currentTarget as HTMLInputElement).files?.[0] ?? null)}
						class="rounded-2xl bg-surface-container-high px-4 py-3 text-[14px] text-on-surface"
					/>
				</label>

				{#if errorMsg}
					<p class="text-[12px] font-medium text-error">{errorMsg}</p>
				{/if}
				<div class="flex justify-end gap-3">
					<button
						type="button"
						onclick={onClose}
						class="cursor-pointer rounded-full border border-outline-variant bg-transparent px-6 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
					>
						Abbrechen
					</button>
					<button
						type="submit"
						disabled={busy || !hasFile || !teamChosen}
						class="cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
					>
						{busy ? 'Wird gelesen…' : 'Weiter'}
					</button>
				</div>
			</form>
		{:else if step === 'mapping' && parsed}
			<NdsMappingStep
				persons={mappingRows}
				suggestions={parsed.memberSuggestions}
				teamMembers={resolvedTeamMembers}
				bind:mappings
			/>
			<div class="mt-4 flex justify-end gap-3">
				<button
					type="button"
					onclick={() => (step = 'files')}
					class="cursor-pointer rounded-full border border-outline-variant bg-transparent px-6 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
				>
					Zurück
				</button>
				<button
					type="button"
					onclick={goToEventsOrConfirm}
					class="cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90"
				>
					Weiter
				</button>
			</div>
		{:else if step === 'events' && parsed}
			<NdsEventsStep
				series={parsed.series}
				conflicts={parsed.conflicts}
				bind:seriesTimes
				bind:resolutions
			/>
			<div class="mt-4 flex justify-end gap-3">
				<button
					type="button"
					onclick={() => (step = 'mapping')}
					class="cursor-pointer rounded-full border border-outline-variant bg-transparent px-6 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
				>
					Zurück
				</button>
				<button
					type="button"
					disabled={!eventsGateOk}
					onclick={() => (step = 'confirm')}
					class="cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
				>
					Weiter
				</button>
			</div>
		{:else if step === 'confirm' && parsed}
			<div class="min-h-0 flex-1 overflow-y-auto pr-1">
				<div class="rounded-2xl bg-surface-container-high px-4 py-3 text-[13px] text-on-surface">
					<p class="font-medium">Zusammenfassung</p>
					<ul class="mt-2 list-disc pl-5 text-on-surface-variant">
						<li>{counts.mapped} zugeordnet</li>
						<li>{counts.created} neu</li>
						<li>{counts.skipped} übersprungen</li>
						{#if counts.events}
							<li>{counts.events?.eventsNew} Events neu</li>
							<li>
								{counts.events?.keepTeamorg} Konflikte TeamOrg / {counts.events?.keepNds} Konflikte NDS
							</li>
							<li>Anwesenheiten: {attendanceMode === 'keep' ? 'ja' : 'nein'}</li>
						{/if}
					</ul>
				</div>

				<div class="mt-4 flex flex-col gap-3">
					{#if hasAwl}
						<label class="flex items-center gap-2 text-[14px] text-on-surface">
							<input
								type="checkbox"
								checked={attendanceMode === 'keep'}
								onchange={(e) =>
									(attendanceMode = (e.currentTarget as HTMLInputElement).checked ? 'keep' : 'discard')}
								class="h-4 w-4 accent-primary"
							/>
							Im NDS-Sheet mit «J» markierte Anwesenheiten als dokumentierte Präsenz importieren
						</label>
					{/if}
					<label class="flex flex-col gap-1 text-[13px] text-on-surface-variant">
						Nutzergruppe (für Dauer-Prüfung beim Export)
						<select
							bind:value={nutzergruppe}
							class="rounded-xl bg-surface-container-high px-3 py-2 text-[14px] text-on-surface"
						>
							<option value="">— unbekannt —</option>
							<option value="NG1">NG 1 — Sportverein</option>
							<option value="NG2">NG 2 — wetterabhängige Sportart</option>
							<option value="NG4">NG 4 — Kanton/Gemeinde/Verband</option>
							<option value="NG5">NG 5 — freiwilliger Schulsport</option>
						</select>
					</label>
				</div>
			</div>

			{#if errorMsg}
				<p class="mt-3 text-[12px] font-medium text-error">{errorMsg}</p>
			{/if}
			<div class="mt-4 flex justify-end gap-3">
				<button
					type="button"
					onclick={() => (step = hasAwl ? 'events' : 'mapping')}
					class="cursor-pointer rounded-full border border-outline-variant bg-transparent px-6 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
				>
					Zurück
				</button>
				<button
					type="button"
					disabled={busy}
					onclick={submitImport}
					class="cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
				>
					{busy ? 'Importiere…' : 'Importieren'}
				</button>
			</div>
		{:else if step === 'done' && result}
			<div class="rounded-2xl bg-success-container px-4 py-4 text-[14px] text-on-surface">
				<p class="font-medium">Import erfolgreich</p>
				<ul class="mt-2 list-disc pl-5 text-on-surface-variant">
					<li>{result.membersImported} Mitglieder</li>
					<li>{result.eventsCreated} Termine</li>
					<li>{result.attendanceImported} Anwesenheiten übernommen</li>
				</ul>
			</div>
			<div class="mt-5 flex justify-end">
				<button
					type="button"
					onclick={onClose}
					class="cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90"
				>
					Fertig
				</button>
			</div>
		{/if}
	</div>
</div>
