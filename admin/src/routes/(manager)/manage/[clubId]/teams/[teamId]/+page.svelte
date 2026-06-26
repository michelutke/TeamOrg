<script lang="ts">
	import type { PageData, ActionData } from './$types';

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();

	let showEditForm = $state(false);
	let showArchiveModal = $state(false);
	let removeMemberTarget = $state<{ userId: string; name: string } | null>(null);
	let showInviteForm = $state(false);
	let showSubGroupForm = $state(false);
	let renameTargetId = $state<string | null>(null);
	let deleteSubGroupTarget = $state<{ id: string; name: string } | null>(null);

	function roleChipClasses(role: string): string {
		if (role === 'coach') return 'bg-tertiary-container text-on-tertiary-container';
		return 'bg-surface-container-high text-on-surface-variant';
	}

	const inputClasses =
		'w-full rounded-2xl border-none bg-surface-container-high px-[18px] py-3 text-[14px] text-on-surface outline-none placeholder:text-on-surface-variant focus:ring-2 focus:ring-primary';
	const labelClasses = 'mb-1 block text-[12px] font-medium text-on-surface-variant';
	const filledBtn =
		'cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90';
	const outlinedBtn =
		'cursor-pointer rounded-full border border-outline-variant bg-transparent px-6 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high';
	const modalCard =
		'mx-4 w-full max-w-[480px] rounded-[28px] bg-white p-6 shadow-[0px_8px_32px_0px_rgba(0,0,0,0.12)]';
</script>

<svelte:head>
	<title>{data.team.name} — TeamOrg</title>
</svelte:head>

<div class="flex flex-col gap-6">
	<!-- Back link -->
	<nav class="text-[13px]">
		<a
			href="/manage/{data.clubId}/teams"
			class="text-on-surface-variant no-underline hover:text-primary"
		>‹ Back to Teams</a>
	</nav>

	<!-- Team info card -->
	<div class="rounded-3xl bg-surface-container-low p-6">
		<div class="mb-4 flex items-center justify-between">
			<h1 class="font-display text-[24px] font-extrabold text-on-surface">{data.team.name}</h1>
			{#if !showEditForm}
				<div class="flex gap-2">
					<a
						href="/manage/{data.clubId}/teams/{data.team.id}/attendance"
						class="cursor-pointer rounded-full border border-outline-variant bg-transparent px-5 py-2.5 text-[14px] font-bold text-on-surface-variant no-underline hover:bg-surface-container-high"
					>Attendance</a>
					<button
						type="button"
						onclick={() => (showEditForm = true)}
						class="cursor-pointer rounded-full border border-outline-variant bg-transparent px-5 py-2.5 text-[14px] font-bold text-on-surface-variant hover:bg-surface-container-high"
					>Edit</button>
					<button
						type="button"
						onclick={() => (showArchiveModal = true)}
						class="cursor-pointer rounded-full border border-error bg-transparent px-5 py-2.5 text-[14px] font-bold text-error hover:bg-error-container"
					>Archive</button>
				</div>
			{/if}
		</div>

		{#if showEditForm}
			<form method="POST" action="?/updateTeam">
				<div class="mb-4 grid grid-cols-2 gap-4">
					<div>
						<label for="edit-name" class={labelClasses}>Name</label>
						<input id="edit-name" name="name" type="text" value={data.team.name} class={inputClasses} />
					</div>
					<div>
						<label for="edit-desc" class={labelClasses}>Description</label>
						<input id="edit-desc" name="description" type="text" value={data.team.description || ''} class={inputClasses} />
					</div>
				</div>
				<div class="flex gap-3">
					<button type="submit" class={filledBtn}>Save Changes</button>
					<button type="button" onclick={() => (showEditForm = false)} class={outlinedBtn}>Cancel</button>
				</div>
			</form>
		{:else}
			<p class="text-[14px] text-on-surface-variant">
				{data.team.memberCount} members{data.team.description ? ` · ${data.team.description}` : ''}
			</p>
		{/if}
	</div>

	<!-- Invite section -->
	<div class="rounded-3xl bg-surface-container-low p-6">
		<h2 class="mb-1 font-display text-[20px] font-bold text-on-surface">Invite members</h2>
		<p class="mb-4 text-[13px] text-on-surface-variant">
			Add an email to invite one person privately — only that address can join (best for
			coaches). Leave it empty for a shareable link anyone can use.
		</p>

		{#if form?.action === 'invite_sent' && form.expiresAt}
			<div class="mb-4 rounded-2xl bg-white p-4">
				<p class="mb-2 text-[12px] font-bold text-success">
					Invite emailed to {form.email}
				</p>
				<p class="text-[12px] text-on-surface-variant">
					Only {form.email} can use this invite. Expires: {new Date(
						form.expiresAt
					).toLocaleString('en-GB')}
				</p>
			</div>
		{:else if form?.action === 'invite_created' && form.inviteUrl}
			<div class="mb-4 rounded-2xl bg-white p-4">
				<p class="mb-2 text-[12px] font-bold text-success">Shareable invite link generated!</p>
				<input
					type="text"
					readonly
					value={form.inviteUrl}
					onclick={(e) => (e.currentTarget as HTMLInputElement).select()}
					class="{inputClasses} cursor-text"
				/>
				<p class="mt-2 text-[12px] text-on-surface-variant">
					Expires: {new Date(form.expiresAt).toLocaleString('en-GB')}
				</p>
			</div>
		{/if}

		{#if !showInviteForm}
			<button type="button" onclick={() => (showInviteForm = true)} class={filledBtn}>
				Invite a member
			</button>
		{:else}
			<form method="POST" action="?/createInvite" class="flex flex-wrap items-end gap-3">
				<div>
					<label for="invite-role" class={labelClasses}>Role</label>
					<select
						id="invite-role"
						name="role"
						class="cursor-pointer rounded-2xl border-none bg-surface-container-high px-4 py-3 text-[14px] text-on-surface outline-none"
					>
						<option value="player">Player</option>
						<option value="coach">Coach</option>
					</select>
				</div>
				<div class="min-w-[240px] flex-1">
					<label for="invite-email" class={labelClasses}>Email (optional)</label>
					<input
						id="invite-email"
						name="email"
						type="email"
						placeholder="person@example.com"
						class={inputClasses}
					/>
				</div>
				<button type="submit" class="{filledBtn} whitespace-nowrap">Create invite</button>
				<button
					type="button"
					onclick={() => (showInviteForm = false)}
					class="{outlinedBtn} whitespace-nowrap"
				>Cancel</button>
			</form>
		{/if}
	</div>

	<!-- Subgroups -->
	<div class="rounded-3xl bg-surface-container-low p-6">
		<h2 class="mb-1 font-display text-[20px] font-bold text-on-surface">Subgroups</h2>
		<p class="mb-4 text-[13px] text-on-surface-variant">
			Organise the roster into subgroups (e.g. starters, U18) to target events at part of the team.
		</p>

		{#if data.subGroups.length > 0}
			<div class="mb-4 flex flex-col gap-2">
				{#each data.subGroups as sg (sg.id)}
					<div class="flex items-center justify-between gap-3 rounded-2xl bg-white px-4 py-3">
						{#if renameTargetId === sg.id}
							<form method="POST" action="?/renameSubGroup" class="flex flex-1 items-center gap-2">
								<input type="hidden" name="subGroupId" value={sg.id} />
								<input name="name" value={sg.name} class={inputClasses} />
								<button type="submit" class="{filledBtn} whitespace-nowrap">Save</button>
								<button
									type="button"
									onclick={() => (renameTargetId = null)}
									class="{outlinedBtn} whitespace-nowrap">Cancel</button
								>
							</form>
						{:else}
							<div>
								<span class="text-[14px] font-medium text-on-surface">{sg.name}</span>
								<span class="ml-2 text-[12px] text-on-surface-variant">
									{sg.memberCount} member{sg.memberCount === 1 ? '' : 's'}
								</span>
							</div>
							<div class="flex shrink-0 gap-2">
								<button
									type="button"
									onclick={() => (renameTargetId = sg.id)}
									class="rounded-full border border-outline-variant bg-transparent px-4 py-1.5 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high"
									>Rename</button
								>
								<button
									type="button"
									onclick={() => (deleteSubGroupTarget = { id: sg.id, name: sg.name })}
									class="rounded-full border-none bg-transparent px-4 py-1.5 text-[12px] font-bold text-error hover:bg-error-container/50"
									>Delete</button
								>
							</div>
						{/if}
					</div>
				{/each}
			</div>
		{/if}

		{#if !showSubGroupForm}
			<button type="button" onclick={() => (showSubGroupForm = true)} class={filledBtn}>
				Add subgroup
			</button>
		{:else}
			<form method="POST" action="?/createSubGroup" class="flex flex-wrap items-end gap-3">
				<div class="min-w-[240px] flex-1">
					<label for="sg-name" class={labelClasses}>Subgroup name</label>
					<input id="sg-name" name="name" placeholder="e.g. Starters" class={inputClasses} />
				</div>
				<button type="submit" class="{filledBtn} whitespace-nowrap">Create</button>
				<button
					type="button"
					onclick={() => (showSubGroupForm = false)}
					class="{outlinedBtn} whitespace-nowrap">Cancel</button
				>
			</form>
		{/if}
	</div>

	<!-- Members table -->
	<div class="overflow-hidden rounded-3xl bg-surface-container-low py-1">
		<div class="px-6 pb-1.5 pt-3">
			<h2 class="text-[13px] font-bold text-on-surface">Members</h2>
		</div>

		{#if data.members.length === 0}
			<div class="border-t border-outline-variant bg-white px-6 py-8 text-center">
				<p class="text-[14px] text-on-surface-variant">
					No members yet. Generate an invite link to add members.
				</p>
			</div>
		{:else}
			<table class="w-full border-collapse">
				<thead>
					<tr>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Name</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Role</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Jersey</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">Position</th>
						<th scope="col" class="px-6 py-3.5 text-right text-[12px] font-bold text-on-surface-variant">Actions</th>
					</tr>
				</thead>
				<tbody>
					{#each data.members as member}
						<tr class="border-t border-outline-variant bg-white">
							<td class="px-6 py-[13px] text-[14px] font-medium text-on-surface">{member.displayName}</td>
							<td class="px-6 py-[13px]">
								<span class="rounded-full px-3 py-1 text-[11px] font-bold {roleChipClasses(member.role)}">
									{member.role}
								</span>
							</td>
							<td class="px-6 py-[13px] text-[14px] text-on-surface-variant">{member.jerseyNumber ?? '—'}</td>
							<td class="px-6 py-[13px] text-[14px] text-on-surface-variant">{member.position || '—'}</td>
							<td class="px-6 py-[13px] text-right">
								<div class="flex justify-end gap-2">
									<form method="POST" action="?/changeRole">
										<input type="hidden" name="userId" value={member.userId} />
										<input type="hidden" name="role" value={member.role === 'coach' ? 'player' : 'coach'} />
										<button
											type="submit"
											class="cursor-pointer rounded-full border border-outline-variant bg-transparent px-4 py-1.5 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high"
										>Make {member.role === 'coach' ? 'Player' : 'Coach'}</button>
									</form>
									<button
										type="button"
										onclick={() => (removeMemberTarget = { userId: member.userId, name: member.displayName })}
										class="cursor-pointer rounded-full border-none bg-transparent px-4 py-1.5 text-[12px] font-bold text-error hover:bg-error-container/50"
									>Remove</button>
								</div>
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		{/if}
	</div>
</div>

<!-- Archive confirmation modal -->
{#if showArchiveModal}
	<div
		class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
		role="dialog"
		aria-modal="true"
		aria-labelledby="archive-title"
	>
		<div class={modalCard}>
			<h3 id="archive-title" class="mb-3 font-display text-[22px] font-extrabold text-on-surface">Archive team</h3>
			<p class="mb-6 text-[14px] text-on-surface-variant">
				Archive {data.team.name}? Members will no longer see this team. This can be reversed later.
			</p>
			<div class="flex gap-3">
				<form method="POST" action="?/archive">
					<button
						type="submit"
						class="cursor-pointer rounded-full border-none bg-error px-6 py-3 text-[14px] font-bold text-on-error hover:opacity-90"
					>Archive</button>
				</form>
				<button type="button" onclick={() => (showArchiveModal = false)} class={outlinedBtn}>Cancel</button>
			</div>
		</div>
	</div>
{/if}

<!-- Remove member confirmation modal -->
{#if removeMemberTarget}
	<div
		class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
		role="dialog"
		aria-modal="true"
		aria-labelledby="remove-member-title"
	>
		<div class={modalCard}>
			<h3 id="remove-member-title" class="mb-3 font-display text-[22px] font-extrabold text-on-surface">Remove member</h3>
			<p class="mb-6 text-[14px] text-on-surface-variant">
				Remove {removeMemberTarget.name} from {data.team.name}? They will lose access immediately.
			</p>
			<div class="flex gap-3">
				<form method="POST" action="?/removeMember">
					<input type="hidden" name="userId" value={removeMemberTarget.userId} />
					<button
						type="submit"
						class="cursor-pointer rounded-full border-none bg-error px-6 py-3 text-[14px] font-bold text-on-error hover:opacity-90"
					>Remove</button>
				</form>
				<button type="button" onclick={() => (removeMemberTarget = null)} class={outlinedBtn}>Cancel</button>
			</div>
		</div>
	</div>
{/if}

<!-- Delete subgroup confirmation modal -->
{#if deleteSubGroupTarget}
	<div
		class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
		role="dialog"
		aria-modal="true"
		aria-labelledby="delete-subgroup-title"
	>
		<div class={modalCard}>
			<h3 id="delete-subgroup-title" class="mb-3 font-display text-[22px] font-extrabold text-on-surface">
				Delete subgroup
			</h3>
			<p class="mb-6 text-[14px] text-on-surface-variant">
				Delete subgroup {deleteSubGroupTarget.name}? Members stay on the team; only the grouping is removed.
			</p>
			<div class="flex gap-3">
				<form method="POST" action="?/deleteSubGroup">
					<input type="hidden" name="subGroupId" value={deleteSubGroupTarget.id} />
					<button
						type="submit"
						class="cursor-pointer rounded-full border-none bg-error px-6 py-3 text-[14px] font-bold text-on-error hover:opacity-90"
					>Delete</button>
				</form>
				<button type="button" onclick={() => (deleteSubGroupTarget = null)} class={outlinedBtn}>Cancel</button>
			</div>
		</div>
	</div>
{/if}
