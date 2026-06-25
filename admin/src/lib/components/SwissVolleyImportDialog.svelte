<script lang="ts">
	import { enhance } from '$app/forms';
	import { invalidateAll } from '$app/navigation';
	import { X } from 'lucide-svelte';
	import type { Dict } from '$lib/i18n';

	interface SvLeague {
		leagueId: number | null;
		caption: string | null;
	}

	interface SvTeam {
		teamId: number | null;
		seasonalTeamId: number | null;
		caption: string | null;
		gender: string | null;
		league: SvLeague | null;
	}

	interface ImportedTeam {
		teamId: string;
		svTeamId: number;
		name: string;
	}

	interface ImportResult {
		created: ImportedTeam[];
		skipped: number[];
	}

	interface Props {
		clubId: string;
		m: Dict['swissvolley'];
		onClose: () => void;
	}

	let { clubId, m, onClose }: Props = $props();

	let loading = $state(true);
	let loadError = $state<'noKey' | 'failed' | null>(null);
	let teams = $state<SvTeam[]>([]);
	let selected = $state<Set<number>>(new Set());
	let submitting = $state(false);
	let formError = $state<string | null>(null);
	let result = $state<ImportResult | null>(null);

	const importableTeams = $derived(teams.filter((t) => t.teamId !== null));
	const allSelected = $derived(
		importableTeams.length > 0 && selected.size === importableTeams.length
	);

	async function loadTeams() {
		loading = true;
		loadError = null;
		try {
			const res = await fetch(`/manage/${clubId}/teams/sv-teams`);
			if (res.status === 409) {
				loadError = 'noKey';
				return;
			}
			if (!res.ok) {
				loadError = 'failed';
				return;
			}
			const data = (await res.json()) as { teams: SvTeam[] };
			teams = data.teams ?? [];
		} catch {
			loadError = 'failed';
		} finally {
			loading = false;
		}
	}

	function toggle(id: number) {
		const next = new Set(selected);
		if (next.has(id)) next.delete(id);
		else next.add(id);
		selected = next;
	}

	function toggleAll() {
		if (allSelected) {
			selected = new Set();
		} else {
			selected = new Set(importableTeams.map((t) => t.teamId as number));
		}
	}

	function genderLabel(gender: string | null): string | null {
		// SwissVolley API returns lowercase 'm' | 'f' (design §10); be tolerant of case.
		switch (gender?.toLowerCase()) {
			case 'm':
				return m.genderM;
			case 'f':
				return m.genderF;
			case 'x':
				return m.genderMixed;
			default:
				return gender;
		}
	}

	loadTeams();
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
		aria-label={m.title}
	>
		<div class="mb-4 flex items-start justify-between gap-4">
			<div class="flex flex-col gap-1">
				<h2 class="text-[20px] font-bold text-on-surface">{m.title}</h2>
				<p class="text-[13px] text-on-surface-variant">{m.subtitle}</p>
			</div>
			<button
				type="button"
				onclick={onClose}
				aria-label={m.cancel}
				class="cursor-pointer rounded-full border-none bg-transparent p-2 text-on-surface-variant hover:bg-surface-container-high"
			>
				<X size={18} />
			</button>
		</div>

		{#if result}
			<div class="rounded-2xl bg-success-container px-4 py-4 text-[14px] text-on-surface">
				{#if result.created.length === 0}
					<p>{m.resultNone}</p>
				{:else}
					<p class="font-medium">
						{result.created.length}
						{m.resultCreated}{result.skipped.length > 0
							? ` · ${result.skipped.length} ${m.resultSkipped}`
							: ''}
					</p>
					<ul class="mt-2 list-disc pl-5 text-on-surface-variant">
						{#each result.created as c (c.svTeamId)}
							<li>{c.name}</li>
						{/each}
					</ul>
				{/if}
			</div>
			<div class="mt-5 flex justify-end">
				<button
					type="button"
					onclick={onClose}
					class="cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90"
				>
					{m.cancel}
				</button>
			</div>
		{:else if loading}
			<p class="py-10 text-center text-[14px] text-on-surface-variant">{m.loading}</p>
		{:else if loadError === 'noKey'}
			<div class="rounded-2xl bg-surface-container-high px-4 py-6 text-center">
				<p class="text-[15px] font-bold text-on-surface">{m.noKeyTitle}</p>
				<p class="mt-1 text-[13px] text-on-surface-variant">{m.noKeyBody}</p>
			</div>
		{:else if loadError === 'failed'}
			<p class="py-10 text-center text-[14px] text-error">{m.loadFailed}</p>
		{:else if importableTeams.length === 0}
			<p class="py-10 text-center text-[14px] text-on-surface-variant">{m.noTeams}</p>
		{:else}
			<div class="mb-2 flex justify-end">
				<button
					type="button"
					onclick={toggleAll}
					class="cursor-pointer rounded-full border-none bg-transparent px-3 py-1 text-[13px] font-medium text-primary hover:bg-surface-container-high"
				>
					{allSelected ? m.clearAll : m.selectAll}
				</button>
			</div>

			<form
				method="POST"
				action="?/importSv"
				use:enhance={() => {
					submitting = true;
					formError = null;
					return async ({ result: actionResult, update }) => {
						submitting = false;
						if (actionResult.type === 'success') {
							result = (actionResult.data?.imported as ImportResult) ?? null;
							await invalidateAll();
						} else if (actionResult.type === 'failure') {
							const key = actionResult.data?.importError as keyof Dict['swissvolley'] | undefined;
							formError = key ? m[key] : m.importFailed;
						} else {
							await update();
						}
					};
				}}
				class="flex min-h-0 flex-1 flex-col"
			>
				<div class="flex-1 overflow-y-auto pr-1">
					<ul class="flex flex-col gap-2">
						{#each importableTeams as team (team.teamId)}
							{@const id = team.teamId as number}
							<li>
								<label
									class="flex cursor-pointer items-start gap-3 rounded-2xl bg-surface-container-high px-4 py-3"
								>
									<input
										type="checkbox"
										name="svTeamIds"
										value={id}
										checked={selected.has(id)}
										onchange={() => toggle(id)}
										class="mt-1 h-4 w-4 accent-primary"
									/>
									<span class="flex flex-col">
										<span class="text-[14px] font-medium text-on-surface">
											{team.caption ?? `SwissVolley Team ${id}`}
										</span>
										<span class="text-[12px] text-on-surface-variant">
											{[team.league?.caption, genderLabel(team.gender)]
												.filter(Boolean)
												.join(' · ')}
										</span>
									</span>
								</label>
							</li>
						{/each}
					</ul>
				</div>

				{#if formError}
					<p class="mt-3 text-[12px] font-medium text-error">{formError}</p>
				{/if}

				<div class="mt-5 flex justify-end gap-3">
					<button
						type="button"
						onclick={onClose}
						class="cursor-pointer rounded-full border border-outline-variant bg-transparent px-6 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
					>
						{m.cancel}
					</button>
					<button
						type="submit"
						disabled={submitting || selected.size === 0}
						class="cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
					>
						{submitting ? m.importing : m.import}
					</button>
				</div>
			</form>
		{/if}
	</div>
</div>
