<script lang="ts">
	import type { PageData, ActionData } from './$types';

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();

	let showCreateForm = $state(false);

	function formatDate(iso: string): string {
		return new Date(iso).toLocaleDateString('en-GB', {
			day: '2-digit',
			month: 'short',
			year: 'numeric'
		});
	}

	const inputStyle = `
		width: 100%;
		background-color: #1C1C2E;
		border: 1px solid #2A2A40;
		color: #F0F0FF;
		font-size: 14px;
		height: 40px;
		padding: 0 16px;
		border-radius: 6px;
		outline: none;
	`;

	const activeTeams = $derived(data.teams.filter((t) => !t.archivedAt));
	const archivedTeams = $derived(data.teams.filter((t) => t.archivedAt));
</script>

<svelte:head>
	<title>Teams — {data.club.name} — TeamOrg Admin</title>
</svelte:head>

<!-- Create team -->
{#if !showCreateForm}
	<div class="mb-6">
		<button
			type="button"
			onclick={() => (showCreateForm = true)}
			style="background-color: #4F8EF7; color: #FFFFFF; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer;"
		>Create Team</button>
	</div>
{:else}
	<div
		class="mb-6"
		style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 24px;"
	>
		<h2 class="font-semibold mb-4" style="font-size: 16px; color: #F0F0FF;">New Team</h2>
		{#if form?.error}
			<p class="mb-3" style="font-size: 12px; color: #EF4444;">{form.error}</p>
		{/if}
		<form method="POST" action="?/create" class="flex gap-3 items-end">
			<div style="flex: 1;">
				<label for="team-name" class="block font-semibold mb-1" style="font-size: 12px; color: #F0F0FF;">Name</label>
				<input id="team-name" name="name" type="text" required placeholder="e.g. U18 Boys" style={inputStyle} />
			</div>
			<div style="flex: 1;">
				<label for="team-desc" class="block font-semibold mb-1" style="font-size: 12px; color: #F0F0FF;">Description</label>
				<input id="team-desc" name="description" type="text" placeholder="Optional" style={inputStyle} />
			</div>
			<button
				type="submit"
				style="background-color: #4F8EF7; color: #FFFFFF; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer; white-space: nowrap;"
			>Create</button>
			<button
				type="button"
				onclick={() => (showCreateForm = false)}
				style="background: transparent; border: 1px solid #2A2A40; color: #F0F0FF; font-size: 14px; height: 40px; padding: 0 16px; border-radius: 6px; cursor: pointer; white-space: nowrap;"
			>Cancel</button>
		</form>
	</div>
{/if}

<!-- Active teams -->
{#if activeTeams.length === 0}
	<div
		style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 48px 24px; text-align: center;"
	>
		<p style="font-size: 14px; color: #9090B0;">No teams yet. Create one to get started.</p>
	</div>
{:else}
	<div style="border: 1px solid #2A2A40; border-radius: 8px; overflow: hidden;">
		<table style="width: 100%; border-collapse: collapse;">
			<thead style="background-color: #13131F;">
				<tr>
					<th scope="col" style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;">Name</th>
					<th scope="col" style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;">Description</th>
					<th scope="col" style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;">Members</th>
					<th scope="col" style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;">Created</th>
				</tr>
			</thead>
			<tbody>
				{#each activeTeams as team}
					<tr
						style="background-color: #1C1C2E; border-top: 1px solid #2A2A40; cursor: pointer;"
						onclick={() => window.location.href = `/admin/clubs/${data.clubId}/teams/${team.id}`}
						onmouseenter={(e) => ((e.currentTarget as HTMLElement).style.backgroundColor = 'rgba(255,255,255,0.03)')}
						onmouseleave={(e) => ((e.currentTarget as HTMLElement).style.backgroundColor = '#1C1C2E')}
					>
						<td style="padding: 12px 16px; font-size: 14px; color: #F0F0FF;">{team.name}</td>
						<td style="padding: 12px 16px; font-size: 14px; color: #9090B0;">{team.description || '—'}</td>
						<td style="padding: 12px 16px; font-size: 14px; color: #F0F0FF;">{team.memberCount}</td>
						<td style="padding: 12px 16px; font-size: 14px; color: #9090B0;">{formatDate(team.createdAt)}</td>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>
{/if}

<!-- Archived teams -->
{#if archivedTeams.length > 0}
	<div class="mt-8">
		<h2 class="font-semibold mb-4" style="font-size: 16px; color: #9090B0;">Archived Teams</h2>
		<div style="border: 1px solid #2A2A40; border-radius: 8px; overflow: hidden; opacity: 0.6;">
			<table style="width: 100%; border-collapse: collapse;">
				<tbody>
					{#each archivedTeams as team}
						<tr style="background-color: #1C1C2E; border-top: 1px solid #2A2A40;">
							<td style="padding: 12px 16px; font-size: 14px; color: #9090B0;">{team.name}</td>
							<td style="padding: 12px 16px; font-size: 14px; color: #9090B0;">{team.memberCount} members</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
	</div>
{/if}
