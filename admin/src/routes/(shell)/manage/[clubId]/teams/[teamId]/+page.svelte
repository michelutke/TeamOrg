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
		>{data.m.manage.backToTeams}</a>
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
					>{data.m.manage.attendance}</a>
					<button
						type="button"
						onclick={() => (showEditForm = true)}
						class="cursor-pointer rounded-full border border-outline-variant bg-transparent px-5 py-2.5 text-[14px] font-bold text-on-surface-variant hover:bg-surface-container-high"
					>{data.m.common.edit}</button>
					<button
						type="button"
						onclick={() => (showArchiveModal = true)}
						class="cursor-pointer rounded-full border border-error bg-transparent px-5 py-2.5 text-[14px] font-bold text-error hover:bg-error-container"
					>{data.m.manage.archive}</button>
				</div>
			{/if}
		</div>

		{#if showEditForm}
			<form method="POST" action="?/updateTeam">
				<div class="mb-4 grid grid-cols-2 gap-4">
					<div>
						<label for="edit-name" class={labelClasses}>{data.m.profile.name}</label>
						<input id="edit-name" name="name" type="text" value={data.team.name} class={inputClasses} />
					</div>
					<div>
						<label for="edit-desc" class={labelClasses}>{data.m.events.description}</label>
						<input id="edit-desc" name="description" type="text" value={data.team.description || ''} class={inputClasses} />
					</div>
				</div>
				<div class="flex gap-3">
					<button type="submit" class={filledBtn}>{data.m.common.save}</button>
					<button type="button" onclick={() => (showEditForm = false)} class={outlinedBtn}>{data.m.common.cancel}</button>
				</div>
			</form>
		{:else}
			<p class="text-[14px] text-on-surface-variant">
				{data.team.memberCount} {data.m.teams.members}{data.team.description ? ` · ${data.team.description}` : ''}
			</p>
		{/if}
	</div>

	<!-- Invite section -->
	<div class="rounded-3xl bg-surface-container-low p-6">
		<h2 class="mb-1 font-display text-[20px] font-bold text-on-surface">{data.m.manage.inviteMembersTitle}</h2>
		<p class="mb-4 text-[13px] text-on-surface-variant">
			{data.m.manage.inviteMembersBody}
		</p>

		{#if form?.action === 'invite_sent' && form.expiresAt}
			<div class="mb-4 rounded-2xl bg-white p-4">
				<p class="mb-2 text-[12px] font-bold text-success">
					{data.m.manage.inviteEmailedToPrefix} {form.email}
				</p>
				<p class="text-[12px] text-on-surface-variant">
					{data.m.manage.inviteOnlyEmailPrefix} {form.email} {data.m.manage.inviteOnlyEmailSuffix} {data.m.manage.expiresLabel} {new Date(
						form.expiresAt
					).toLocaleString('en-GB')}
				</p>
			</div>
		{:else if form?.action === 'invite_created' && form.inviteUrl}
			<div class="mb-4 rounded-2xl bg-white p-4">
				<p class="mb-2 text-[12px] font-bold text-success">{data.m.manage.shareableInviteGenerated}</p>
				<input
					type="text"
					readonly
					value={form.inviteUrl}
					onclick={(e) => (e.currentTarget as HTMLInputElement).select()}
					class="{inputClasses} cursor-text"
				/>
				<p class="mt-2 text-[12px] text-on-surface-variant">
					{data.m.manage.expiresLabel} {new Date(form.expiresAt).toLocaleString('en-GB')}
				</p>
			</div>
		{/if}

		{#if !showInviteForm}
			<button type="button" onclick={() => (showInviteForm = true)} class={filledBtn}>
				{data.m.manage.inviteMember}
			</button>
		{:else}
			<form method="POST" action="?/createInvite" class="flex flex-wrap items-end gap-3">
				<div>
					<label for="invite-role" class={labelClasses}>{data.m.invite.roleLabel}</label>
					<select
						id="invite-role"
						name="role"
						class="cursor-pointer rounded-2xl border-none bg-surface-container-high px-4 py-3 text-[14px] text-on-surface outline-none"
					>
						<option value="player">{data.m.roles.player}</option>
						<option value="coach">{data.m.roles.coach}</option>
					</select>
				</div>
				<div class="min-w-[240px] flex-1">
					<label for="invite-email" class={labelClasses}>{data.m.manage.emailOptionalLabel}</label>
					<input
						id="invite-email"
						name="email"
						type="email"
						placeholder={data.m.manage.emailPlaceholderPerson}
						class={inputClasses}
					/>
				</div>
				<button type="submit" class="{filledBtn} whitespace-nowrap">{data.m.manage.createInvite}</button>
				<button
					type="button"
					onclick={() => (showInviteForm = false)}
					class="{outlinedBtn} whitespace-nowrap"
				>{data.m.common.cancel}</button>
			</form>
		{/if}
	</div>

	<!-- Subgroups -->
	<div class="rounded-3xl bg-surface-container-low p-6">
		<h2 class="mb-1 font-display text-[20px] font-bold text-on-surface">{data.m.manage.subgroupsTitle}</h2>
		<p class="mb-4 text-[13px] text-on-surface-variant">
			{data.m.manage.subgroupsBody}
		</p>

		{#if data.subGroups.length > 0}
			<div class="mb-4 flex flex-col gap-2">
				{#each data.subGroups as sg (sg.id)}
					<div class="flex items-center justify-between gap-3 rounded-2xl bg-white px-4 py-3">
						{#if renameTargetId === sg.id}
							<form method="POST" action="?/renameSubGroup" class="flex flex-1 items-center gap-2">
								<input type="hidden" name="subGroupId" value={sg.id} />
								<input name="name" value={sg.name} class={inputClasses} />
								<button type="submit" class="{filledBtn} whitespace-nowrap">{data.m.common.save}</button>
								<button
									type="button"
									onclick={() => (renameTargetId = null)}
									class="{outlinedBtn} whitespace-nowrap">{data.m.common.cancel}</button
								>
							</form>
						{:else}
							<div>
								<span class="text-[14px] font-medium text-on-surface">{sg.name}</span>
								<span class="ml-2 text-[12px] text-on-surface-variant">
									{sg.memberCount} {sg.memberCount === 1 ? data.m.manage.memberSingular : data.m.teams.members}
								</span>
							</div>
							<div class="flex shrink-0 gap-2">
								<button
									type="button"
									onclick={() => (renameTargetId = sg.id)}
									class="rounded-full border border-outline-variant bg-transparent px-4 py-1.5 text-[12px] font-bold text-on-surface-variant hover:bg-surface-container-high"
									>{data.m.manage.rename}</button
								>
								<button
									type="button"
									onclick={() => (deleteSubGroupTarget = { id: sg.id, name: sg.name })}
									class="rounded-full border-none bg-transparent px-4 py-1.5 text-[12px] font-bold text-error hover:bg-error-container/50"
									>{data.m.manage.delete}</button
								>
							</div>
						{/if}
					</div>
				{/each}
			</div>
		{/if}

		{#if !showSubGroupForm}
			<button type="button" onclick={() => (showSubGroupForm = true)} class={filledBtn}>
				{data.m.manage.addSubgroup}
			</button>
		{:else}
			<form method="POST" action="?/createSubGroup" class="flex flex-wrap items-end gap-3">
				<div class="min-w-[240px] flex-1">
					<label for="sg-name" class={labelClasses}>{data.m.manage.subgroupNameLabel}</label>
					<input id="sg-name" name="name" placeholder={data.m.manage.subgroupNamePlaceholder} class={inputClasses} />
				</div>
				<button type="submit" class="{filledBtn} whitespace-nowrap">{data.m.common.create}</button>
				<button
					type="button"
					onclick={() => (showSubGroupForm = false)}
					class="{outlinedBtn} whitespace-nowrap">{data.m.common.cancel}</button
				>
			</form>
		{/if}
	</div>

	<!-- Members table -->
	<div class="overflow-hidden rounded-3xl bg-surface-container-low py-1">
		<div class="px-6 pb-1.5 pt-3">
			<h2 class="text-[13px] font-bold text-on-surface">{data.m.teams.members}</h2>
		</div>

		{#if data.members.length === 0}
			<div class="border-t border-outline-variant bg-white px-6 py-8 text-center">
				<p class="text-[14px] text-on-surface-variant">
					{data.m.manage.noMembersYet}
				</p>
			</div>
		{:else}
			<table class="w-full border-collapse">
				<thead>
					<tr>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">{data.m.profile.name}</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">{data.m.invite.roleLabel}</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">{data.m.member.jersey}</th>
						<th scope="col" class="px-6 py-3.5 text-left text-[12px] font-bold text-on-surface-variant">{data.m.member.position}</th>
						<th scope="col" class="px-6 py-3.5 text-right text-[12px] font-bold text-on-surface-variant">{data.m.manage.actionsLabel}</th>
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
										>{member.role === 'coach' ? data.m.manage.makePlayer : data.m.manage.makeCoach}</button>
									</form>
									<button
										type="button"
										onclick={() => (removeMemberTarget = { userId: member.userId, name: member.displayName })}
										class="cursor-pointer rounded-full border-none bg-transparent px-4 py-1.5 text-[12px] font-bold text-error hover:bg-error-container/50"
									>{data.m.manage.remove}</button>
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
			<h3 id="archive-title" class="mb-3 font-display text-[22px] font-extrabold text-on-surface">{data.m.manage.archiveTeamTitle}</h3>
			<p class="mb-6 text-[14px] text-on-surface-variant">
				{data.m.manage.archiveConfirmPrefix}{data.team.name}{data.m.manage.archiveConfirmSuffix}
			</p>
			<div class="flex gap-3">
				<form method="POST" action="?/archive">
					<button
						type="submit"
						class="cursor-pointer rounded-full border-none bg-error px-6 py-3 text-[14px] font-bold text-on-error hover:opacity-90"
					>{data.m.manage.archive}</button>
				</form>
				<button type="button" onclick={() => (showArchiveModal = false)} class={outlinedBtn}>{data.m.common.cancel}</button>
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
			<h3 id="remove-member-title" class="mb-3 font-display text-[22px] font-extrabold text-on-surface">{data.m.manage.removeMemberTitle}</h3>
			<p class="mb-6 text-[14px] text-on-surface-variant">
				{data.m.manage.removeConfirmPrefix}{removeMemberTarget.name}{data.m.manage.removeConfirmMiddle}{data.team.name}{data.m.manage.removeConfirmSuffix}
			</p>
			<div class="flex gap-3">
				<form method="POST" action="?/removeMember">
					<input type="hidden" name="userId" value={removeMemberTarget.userId} />
					<button
						type="submit"
						class="cursor-pointer rounded-full border-none bg-error px-6 py-3 text-[14px] font-bold text-on-error hover:opacity-90"
					>{data.m.manage.remove}</button>
				</form>
				<button type="button" onclick={() => (removeMemberTarget = null)} class={outlinedBtn}>{data.m.common.cancel}</button>
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
				{data.m.manage.deleteSubgroupTitle}
			</h3>
			<p class="mb-6 text-[14px] text-on-surface-variant">
				{data.m.manage.deleteSubgroupConfirmPrefix}{deleteSubGroupTarget.name}{data.m.manage.deleteSubgroupConfirmSuffix}
			</p>
			<div class="flex gap-3">
				<form method="POST" action="?/deleteSubGroup">
					<input type="hidden" name="subGroupId" value={deleteSubGroupTarget.id} />
					<button
						type="submit"
						class="cursor-pointer rounded-full border-none bg-error px-6 py-3 text-[14px] font-bold text-on-error hover:opacity-90"
					>{data.m.manage.delete}</button>
				</form>
				<button type="button" onclick={() => (deleteSubGroupTarget = null)} class={outlinedBtn}>{data.m.common.cancel}</button>
			</div>
		</div>
	</div>
{/if}
