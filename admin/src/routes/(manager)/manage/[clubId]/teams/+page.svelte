<script lang="ts">
	import { Plus, Download } from 'lucide-svelte';
	import SwissVolleyImportDialog from '$lib/components/SwissVolleyImportDialog.svelte';
	import MigrateTeamDialog from '$lib/components/MigrateTeamDialog.svelte';
	import type { PageData, ActionData } from './$types';

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();

	let showCreateForm = $state(false);
	let showSwissVolleyImport = $state(false);
	let migrateSource = $state<{ id: string; name: string } | null>(null);

	const activeTeams = $derived(data.teams.filter((t) => !t.deprecated));
	const deprecatedTeams = $derived(data.teams.filter((t) => t.deprecated));
	const migrateTargets = $derived(activeTeams.map((t) => ({ id: t.id, name: t.name })));

	const inputClasses =
		'w-full rounded-2xl border-none bg-surface-container-high px-[18px] py-3 text-[14px] text-on-surface outline-none placeholder:text-on-surface-variant focus:ring-2 focus:ring-primary';
	const labelClasses = 'mb-1 block text-[12px] font-medium text-on-surface-variant';
</script>

<svelte:head>
	<title>Teams — {data.club.name} — TeamOrg</title>
</svelte:head>

<div class="flex flex-col gap-6">
	<!-- Page header -->
	<div class="flex items-center justify-between">
		<div class="flex flex-col gap-1">
			<h1 class="font-display text-[30px] font-extrabold text-on-surface">Teams</h1>
			<p class="text-[13px] text-on-surface-variant">{data.club.name}</p>
		</div>
		<div class="flex items-center gap-3">
			{#if data.swissVolleyConnected}
				<button
					type="button"
					onclick={() => (showSwissVolleyImport = true)}
					class="flex cursor-pointer items-center gap-2 rounded-full border border-outline-variant bg-transparent py-[13px] pl-[22px] pr-6 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
				>
					<Download size={16} />
					{data.m.swissvolley.importButton}
				</button>
			{/if}
			<button
				type="button"
				onclick={() => (showCreateForm = !showCreateForm)}
				class="flex cursor-pointer items-center gap-2 rounded-full border-none bg-primary py-[13px] pl-[22px] pr-6 text-[14px] font-bold text-on-primary hover:opacity-90"
			>
				<Plus size={16} />
				New team
			</button>
		</div>
	</div>

	{#if showSwissVolleyImport}
		<SwissVolleyImportDialog
			clubId={data.clubId}
			m={data.m.swissvolley}
			onClose={() => (showSwissVolleyImport = false)}
		/>
	{/if}

	{#if migrateSource}
		<MigrateTeamDialog
			sourceTeamId={migrateSource.id}
			sourceTeamName={migrateSource.name}
			targets={migrateTargets}
			m={data.m.teamMigrate}
			onClose={() => (migrateSource = null)}
		/>
	{/if}

	<!-- Create form -->
	{#if showCreateForm}
		<div class="rounded-3xl bg-surface-container-low p-6">
			<h2 class="mb-4 text-[17px] font-bold text-on-surface">New team</h2>
			{#if form?.error}
				<p class="mb-3 text-[12px] font-medium text-error">{form.error}</p>
			{/if}
			<form method="POST" action="?/create" class="flex items-end gap-3">
				<div class="flex-1">
					<label for="team-name" class={labelClasses}>Name</label>
					<input id="team-name" name="name" type="text" required placeholder="e.g. U18 Boys" class={inputClasses} />
				</div>
				<div class="flex-1">
					<label for="team-desc" class={labelClasses}>Description</label>
					<input id="team-desc" name="description" type="text" placeholder="Optional" class={inputClasses} />
				</div>
				<button
					type="submit"
					class="cursor-pointer whitespace-nowrap rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90"
				>Create</button>
				<button
					type="button"
					onclick={() => (showCreateForm = false)}
					class="cursor-pointer whitespace-nowrap rounded-full border border-outline-variant bg-transparent px-6 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
				>Cancel</button>
			</form>
		</div>
	{/if}

	<!-- Team cards -->
	{#if data.teams.length === 0}
		<div class="rounded-3xl bg-surface-container-low px-6 py-12 text-center">
			<p class="text-[14px] text-on-surface-variant">No teams yet. Create one to get started.</p>
		</div>
	{:else}
		<div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
			{#each [...activeTeams, ...deprecatedTeams] as team}
				<div class="flex flex-col gap-2 rounded-[28px] bg-surface-container-low px-6 py-6 {team.deprecated ? 'opacity-80' : ''}">
					<div class="flex items-center gap-3">
						<h2 class="text-[18px] font-bold text-on-surface">{team.name}</h2>
						{#if team.deprecated}
							<span class="rounded-full bg-error-container px-3 py-1 text-[11px] font-medium text-error">
								{data.m.teamMigrate.deprecatedBadge}
							</span>
						{/if}
					</div>
					<p class="text-[14px] text-on-surface-variant">
						{team.memberCount} members{team.description ? ` · ${team.description}` : ''}
					</p>
					<div class="mt-1 flex items-center gap-4">
						<a
							href="/manage/{data.clubId}/teams/{team.id}"
							class="text-[14px] font-bold text-primary no-underline hover:underline"
						>View ›</a>
						{#if team.deprecated}
							<button
								type="button"
								onclick={() => (migrateSource = { id: team.id, name: team.name })}
								class="cursor-pointer border-none bg-transparent p-0 text-[14px] font-bold text-primary hover:underline"
							>{data.m.teamMigrate.action}</button>
						{/if}
					</div>
				</div>
			{/each}
		</div>
	{/if}
</div>
