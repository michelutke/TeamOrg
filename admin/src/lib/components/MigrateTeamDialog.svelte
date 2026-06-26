<script lang="ts">
	import { enhance } from '$app/forms';
	import { invalidateAll } from '$app/navigation';
	import { X } from 'lucide-svelte';
	import type { Dict } from '$lib/i18n';

	interface TargetTeam {
		id: string;
		name: string;
	}

	interface MigrateResult {
		movedMembers: number;
		targetTeamId: string;
	}

	interface Props {
		sourceTeamId: string;
		sourceTeamName: string;
		targets: TargetTeam[];
		m: Dict['teamMigrate'];
		onClose: () => void;
	}

	let { sourceTeamId, sourceTeamName, targets, m, onClose }: Props = $props();

	let targetTeamId = $state('');
	let submitting = $state(false);
	let formError = $state<string | null>(null);
	let result = $state<MigrateResult | null>(null);
</script>

<div
	class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
	role="presentation"
	onclick={(e) => {
		if (e.target === e.currentTarget) onClose();
	}}
>
	<div
		class="flex max-h-[85vh] w-full max-w-[480px] flex-col rounded-[28px] bg-surface-container-low p-6"
		role="dialog"
		aria-modal="true"
		aria-label={m.title}
	>
		<div class="mb-4 flex items-start justify-between gap-4">
			<div class="flex flex-col gap-1">
				<h2 class="text-[20px] font-bold text-on-surface">{m.title}</h2>
				<p class="text-[13px] text-on-surface-variant">{sourceTeamName} · {m.subtitle}</p>
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
				<p class="font-medium">{result.movedMembers} {m.resultMoved}</p>
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
		{:else if targets.length === 0}
			<p class="py-10 text-center text-[14px] text-on-surface-variant">{m.noTargets}</p>
			<div class="mt-1 flex justify-end">
				<button
					type="button"
					onclick={onClose}
					class="cursor-pointer rounded-full border border-outline-variant bg-transparent px-6 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
				>
					{m.cancel}
				</button>
			</div>
		{:else}
			<form
				method="POST"
				action="?/migrateTo"
				use:enhance={() => {
					submitting = true;
					formError = null;
					return async ({ result: actionResult, update }) => {
						submitting = false;
						if (actionResult.type === 'success') {
							result = (actionResult.data?.migrated as MigrateResult) ?? null;
							await invalidateAll();
						} else if (actionResult.type === 'failure') {
							const key = actionResult.data?.migrateError as keyof Dict['teamMigrate'] | undefined;
							formError = key ? m[key] : m.errFailed;
						} else {
							await update();
						}
					};
				}}
				class="flex flex-col"
			>
				<input type="hidden" name="sourceTeamId" value={sourceTeamId} />

				<label for="migrate-target" class="mb-1 block text-[12px] font-medium text-on-surface-variant">
					{m.targetLabel}
				</label>
				<select
					id="migrate-target"
					name="targetTeamId"
					bind:value={targetTeamId}
					required
					class="w-full rounded-2xl border-none bg-surface-container-high px-[18px] py-3 text-[14px] text-on-surface outline-none focus:ring-2 focus:ring-primary"
				>
					<option value="" disabled>{m.selectPlaceholder}</option>
					{#each targets as t (t.id)}
						<option value={t.id}>{t.name}</option>
					{/each}
				</select>

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
						disabled={submitting || targetTeamId === ''}
						class="cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
					>
						{submitting ? m.submitting : m.submit}
					</button>
				</div>
			</form>
		{/if}
	</div>
</div>
