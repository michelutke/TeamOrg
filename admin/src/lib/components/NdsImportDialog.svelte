<script lang="ts">
	import { enhance } from '$app/forms';
	import { invalidateAll } from '$app/navigation';
	import { X } from 'lucide-svelte';

	interface ParsedActivity {
		date: string;
		weekday: string | null;
		symbol: string;
		durationMin: number | null;
	}
	interface ParsedMember {
		funktion: string;
		lastName: string;
		firstName: string;
		birthDate: string | null;
	}
	interface Parsed {
		angebotId: string;
		kursName: string | null;
		hauptsportart: string | null;
		gruppengroesse: string | null;
		activities: ParsedActivity[];
		members: ParsedMember[];
	}
	interface ImportResult {
		teamId: string;
		membersImported: number;
		eventsCreated: number;
		attendanceImported: number;
	}
	interface PersonInput {
		lastName: string;
		firstName: string;
		birthDate: string | null;
		personNumber: string | null;
		funktion: string;
	}

	interface Props {
		clubId: string;
		onClose: () => void;
	}
	let { clubId, onClose }: Props = $props();

	type Step = 'upload' | 'preview' | 'done';
	let step = $state<Step>('upload');
	let teilnehmendeFile = $state<File | null>(null);
	let leiterFile = $state<File | null>(null);
	let listeFile = $state<File | null>(null);
	let busy = $state(false);
	let errorMsg = $state<string | null>(null);

	let parsed = $state<Parsed | null>(null);
	let persons = $state<PersonInput[]>([]);
	let teamName = $state('');
	let nutzergruppe = $state('');
	let importEvents = $state(true);
	let attendanceMode = $state<'keep' | 'discard'>('keep');
	let result = $state<ImportResult | null>(null);

	const leaders = $derived(parsed?.members.filter((m) => m.funktion === 'Leiter/in') ?? []);
	const players = $derived(parsed?.members.filter((m) => m.funktion !== 'Leiter/in') ?? []);
	const withPn = $derived(persons.filter((p) => p.personNumber).length);

	const payload = $derived(
		parsed
			? JSON.stringify({
					createTeamName: teamName.trim() || parsed.kursName || `NDS ${parsed.angebotId}`,
					nutzergruppe: nutzergruppe || null,
					parsed,
					persons,
					importEvents,
					attendanceMode
				})
			: ''
	);

	async function parseRoster(f: File): Promise<PersonInput[]> {
		const form = new FormData();
		form.append('file', f);
		const res = await fetch(`/manage/${clubId}/nds/parse-roster`, { method: 'POST', body: form });
		if (!res.ok) throw new Error('roster');
		return (await res.json()) as PersonInput[];
	}

	async function upload(e: SubmitEvent) {
		e.preventDefault();
		if (!listeFile) return;
		busy = true;
		errorMsg = null;
		try {
			const collected: PersonInput[] = [];
			if (teilnehmendeFile) collected.push(...(await parseRoster(teilnehmendeFile)));
			if (leiterFile) collected.push(...(await parseRoster(leiterFile)));

			const form = new FormData();
			form.append('file', listeFile);
			const res = await fetch(`/manage/${clubId}/nds/parse`, { method: 'POST', body: form });
			if (res.status === 422) {
				errorMsg = 'Die Datei ist keine gültige NDS-Anwesenheitsliste.';
				return;
			}
			if (!res.ok) {
				errorMsg = 'Die Datei konnte nicht gelesen werden.';
				return;
			}
			parsed = (await res.json()) as Parsed;
			persons = collected;
			teamName = parsed.kursName ?? '';
			step = 'preview';
		} catch {
			errorMsg = 'Eine der Dateien konnte nicht gelesen werden.';
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
		class="flex max-h-[85vh] w-full max-w-[560px] flex-col rounded-[28px] bg-surface-container-low p-6"
		role="dialog"
		aria-modal="true"
		aria-label="NDS-Import"
	>
		<div class="mb-4 flex items-start justify-between gap-4">
			<div class="flex flex-col gap-1">
				<h2 class="text-[20px] font-bold text-on-surface">NDS-Anwesenheitsliste importieren</h2>
				<p class="text-[13px] text-on-surface-variant">
					Lade die aus der NDS exportierte Anwesenheitsliste (.xlsx) hoch.
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

		{#if step === 'upload'}
			<form onsubmit={upload} class="flex flex-col gap-4">
				<p class="text-[13px] text-on-surface-variant">
					Reihenfolge: zuerst Teilnehmende, dann Leiter/innen (beide bringen die
					Personennummern), zuletzt die Anwesenheitsliste (Termine & Anwesenheiten).
				</p>
				<label class="flex flex-col gap-1 text-[13px] text-on-surface-variant">
					1. Teilnehmende (.csv) <span class="text-on-surface-variant/70">– optional</span>
					<input
						type="file"
						accept=".csv"
						onchange={(e) => (teilnehmendeFile = (e.currentTarget as HTMLInputElement).files?.[0] ?? null)}
						class="rounded-2xl bg-surface-container-high px-4 py-3 text-[14px] text-on-surface"
					/>
				</label>
				<label class="flex flex-col gap-1 text-[13px] text-on-surface-variant">
					2. Leiterinnen/Leiter (.xlsx) <span class="text-on-surface-variant/70">– optional</span>
					<input
						type="file"
						accept=".xlsx"
						onchange={(e) => (leiterFile = (e.currentTarget as HTMLInputElement).files?.[0] ?? null)}
						class="rounded-2xl bg-surface-container-high px-4 py-3 text-[14px] text-on-surface"
					/>
				</label>
				<label class="flex flex-col gap-1 text-[13px] text-on-surface-variant">
					3. Anwesenheitsliste (.xlsx) <span class="text-error">– erforderlich</span>
					<input
						type="file"
						accept=".xlsx"
						required
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
						disabled={busy || !listeFile}
						class="cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
					>
						{busy ? 'Wird gelesen…' : 'Dateien lesen'}
					</button>
				</div>
			</form>
		{:else if step === 'preview' && parsed}
			<div class="min-h-0 flex-1 overflow-y-auto pr-1">
				<div class="rounded-2xl bg-surface-container-high px-4 py-3 text-[13px] text-on-surface">
					<p><span class="text-on-surface-variant">Angebot:</span> {parsed.angebotId}</p>
					<p><span class="text-on-surface-variant">Kurs:</span> {parsed.kursName ?? '—'}</p>
					<p><span class="text-on-surface-variant">Hauptsportart:</span> {parsed.hauptsportart ?? '—'}</p>
					<p class="mt-1 font-medium">
						{parsed.activities.length} Aktivitäten · {leaders.length} Leiter · {players.length} Teilnehmer
					</p>
					{#if persons.length > 0}
						<p class="mt-1 text-on-surface-variant">{withPn} Personennummern aus Personen-Dateien</p>
					{:else}
						<p class="mt-1 text-error">
							Keine Personen-Dateien – Personennummern müssen später manuell erfasst werden.
						</p>
					{/if}
				</div>

				<div class="mt-4 flex flex-col gap-3">
					<label class="flex flex-col gap-1 text-[13px] text-on-surface-variant">
						Team-Name
						<input
							bind:value={teamName}
							placeholder={parsed.kursName ?? ''}
							class="rounded-xl bg-surface-container-high px-3 py-2 text-[14px] text-on-surface"
						/>
					</label>
					<label class="flex flex-col gap-1 text-[13px] text-on-surface-variant">
						Nutzergruppe (für Dauer-Prüfung beim Export)
						<select
							bind:value={nutzergruppe}
							class="rounded-xl bg-surface-container-high px-3 py-2 text-[14px] text-on-surface"
						>
							<option value="">— unbekannt —</option>
							<option value="NG1">NG 1</option>
							<option value="NG2">NG 2</option>
							<option value="NG4">NG 4</option>
							<option value="NG5">NG 5</option>
						</select>
					</label>
					<label class="flex items-center gap-2 text-[14px] text-on-surface">
						<input type="checkbox" bind:checked={importEvents} class="h-4 w-4 accent-primary" />
						Trainings & Spiele als Termine importieren
					</label>
					{#if importEvents}
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
						<p class="text-[12px] text-on-surface-variant">Erscheint danach in der Anwesenheitskontrolle und als «anwesend»-Hinweis am Termin.</p>
					{/if}
				</div>
			</div>

			{#if errorMsg}
				<p class="mt-3 text-[12px] font-medium text-error">{errorMsg}</p>
			{/if}

			<form
				method="POST"
				action="?/importNds"
				use:enhance={() => {
					busy = true;
					errorMsg = null;
					return async ({ result: r, update }) => {
						busy = false;
						if (r.type === 'success') {
							result = (r.data?.ndsImported as ImportResult) ?? null;
							step = 'done';
							await invalidateAll();
						} else if (r.type === 'failure') {
							errorMsg =
								r.data?.ndsError === 'angebotLinked'
									? 'Dieses Angebot ist bereits mit einem anderen Team verknüpft.'
									: 'Import fehlgeschlagen.';
						} else {
							await update();
						}
					};
				}}
				class="mt-5 flex justify-end gap-3"
			>
				<input type="hidden" name="payload" value={payload} />
				<button
					type="button"
					onclick={() => (step = 'upload')}
					class="cursor-pointer rounded-full border border-outline-variant bg-transparent px-6 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
				>
					Zurück
				</button>
				<button
					type="submit"
					disabled={busy}
					class="cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
				>
					{busy ? 'Importiere…' : 'Importieren'}
				</button>
			</form>
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
