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

	function roleBadgeStyle(role: string): string {
		if (role === 'coach') return 'color: #FACC15; background: rgba(250,204,21,0.12);';
		return 'color: #9090B0; background: rgba(144,144,176,0.12);';
	}
</script>

<svelte:head>
	<title>{data.team.name} — TeamOrg Admin</title>
</svelte:head>

<!-- Back link -->
<nav class="mb-4" style="font-size: 14px; color: #9090B0;">
	<a href="/admin/clubs/{data.clubId}/teams" style="color: #9090B0; text-decoration: none;">← Back to Teams</a>
</nav>

<!-- Team info card -->
<div
	class="mb-6"
	style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 24px;"
>
	<div class="flex items-center justify-between mb-4">
		<h2 class="font-semibold" style="font-size: 16px; color: #F0F0FF;">Team Info</h2>
		{#if !showEditForm}
			<div class="flex gap-2">
				<button
					type="button"
					onclick={() => (showEditForm = true)}
					style="background: transparent; border: 1px solid #2A2A40; color: #F0F0FF; font-size: 14px; height: 36px; padding: 0 12px; border-radius: 6px; cursor: pointer;"
				>Edit</button>
				<button
					type="button"
					onclick={() => (showArchiveModal = true)}
					style="background: transparent; border: 1px solid #EF4444; color: #EF4444; font-size: 14px; height: 36px; padding: 0 12px; border-radius: 6px; cursor: pointer;"
				>Archive</button>
			</div>
		{/if}
	</div>

	{#if showEditForm}
		<form method="POST" action="?/updateTeam">
			<div class="grid gap-4 mb-4" style="grid-template-columns: 1fr 1fr;">
				<div>
					<label for="edit-name" class="block font-semibold mb-1" style="font-size: 12px; color: #F0F0FF;">Name</label>
					<input id="edit-name" name="name" type="text" value={data.team.name} style={inputStyle} />
				</div>
				<div>
					<label for="edit-desc" class="block font-semibold mb-1" style="font-size: 12px; color: #F0F0FF;">Description</label>
					<input id="edit-desc" name="description" type="text" value={data.team.description || ''} style={inputStyle} />
				</div>
			</div>
			<div class="flex gap-3">
				<button
					type="submit"
					style="background-color: #4F8EF7; color: #FFFFFF; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer;"
				>Save Changes</button>
				<button
					type="button"
					onclick={() => (showEditForm = false)}
					style="background: transparent; border: 1px solid #2A2A40; color: #F0F0FF; font-size: 14px; height: 40px; padding: 0 16px; border-radius: 6px; cursor: pointer;"
				>Cancel</button>
			</div>
		</form>
	{:else}
		<dl class="grid gap-3" style="grid-template-columns: repeat(3, 1fr);">
			<div>
				<dt class="font-semibold" style="font-size: 12px; color: #9090B0;">Name</dt>
				<dd style="font-size: 14px; color: #F0F0FF; margin-top: 4px;">{data.team.name}</dd>
			</div>
			<div>
				<dt class="font-semibold" style="font-size: 12px; color: #9090B0;">Description</dt>
				<dd style="font-size: 14px; color: #F0F0FF; margin-top: 4px;">{data.team.description || '—'}</dd>
			</div>
			<div>
				<dt class="font-semibold" style="font-size: 12px; color: #9090B0;">Members</dt>
				<dd style="font-size: 14px; color: #F0F0FF; margin-top: 4px;">{data.team.memberCount}</dd>
			</div>
		</dl>
	{/if}
</div>

<!-- Invite section -->
<div
	class="mb-6"
	style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 24px;"
>
	<h2 class="font-semibold mb-4" style="font-size: 16px; color: #F0F0FF;">Invite Members</h2>

	{#if form?.action === 'invite_created' && form.inviteUrl}
		<div class="mb-4" style="background-color: #13131F; border: 1px solid #2A2A40; border-radius: 6px; padding: 16px;">
			<p class="mb-2" style="font-size: 12px; font-weight: 600; color: #22C55E;">Invite link generated!</p>
			<input
				type="text"
				readonly
				value={form.inviteUrl}
				onclick={(e) => (e.currentTarget as HTMLInputElement).select()}
				style="{inputStyle} background-color: #090912; cursor: text;"
			/>
			<p class="mt-2" style="font-size: 12px; color: #9090B0;">
				Expires: {new Date(form.expiresAt).toLocaleString('en-GB')}
			</p>
		</div>
	{/if}

	{#if !showInviteForm}
		<button
			type="button"
			onclick={() => (showInviteForm = true)}
			style="background-color: #4F8EF7; color: #FFFFFF; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer;"
		>Generate Invite Link</button>
	{:else}
		<form method="POST" action="?/createInvite" class="flex gap-3 items-end">
			<div>
				<label for="invite-role" class="block font-semibold mb-1" style="font-size: 12px; color: #F0F0FF;">Role</label>
				<select
					id="invite-role"
					name="role"
					style="
						background-color: #1C1C2E;
						border: 1px solid #2A2A40;
						color: #F0F0FF;
						font-size: 14px;
						height: 40px;
						padding: 0 12px;
						border-radius: 6px;
						outline: none;
					"
				>
					<option value="player">Player</option>
					<option value="coach">Coach</option>
				</select>
			</div>
			<button
				type="submit"
				style="background-color: #4F8EF7; color: #FFFFFF; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer; white-space: nowrap;"
			>Generate</button>
			<button
				type="button"
				onclick={() => (showInviteForm = false)}
				style="background: transparent; border: 1px solid #2A2A40; color: #F0F0FF; font-size: 14px; height: 40px; padding: 0 16px; border-radius: 6px; cursor: pointer; white-space: nowrap;"
			>Cancel</button>
		</form>
	{/if}
</div>

<!-- Members table -->
<div style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 24px;">
	<h2 class="font-semibold mb-4" style="font-size: 16px; color: #F0F0FF;">Members</h2>

	{#if data.members.length === 0}
		<p style="font-size: 14px; color: #9090B0;">No members yet. Generate an invite link to add members.</p>
	{:else}
		<div style="border: 1px solid #2A2A40; border-radius: 6px; overflow: hidden;">
			<table style="width: 100%; border-collapse: collapse;">
				<thead style="background-color: #13131F;">
					<tr>
						<th scope="col" style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;">Name</th>
						<th scope="col" style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;">Role</th>
						<th scope="col" style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;">Jersey</th>
						<th scope="col" style="padding: 10px 16px; text-align: left; font-size: 12px; font-weight: 600; color: #9090B0;">Position</th>
						<th scope="col" style="padding: 10px 16px; text-align: right; font-size: 12px; font-weight: 600; color: #9090B0;">Actions</th>
					</tr>
				</thead>
				<tbody>
					{#each data.members as member}
						<tr style="border-top: 1px solid #2A2A40;">
							<td style="padding: 12px 16px; font-size: 14px; color: #F0F0FF;">{member.displayName}</td>
							<td style="padding: 12px 16px;">
								<span
									class="font-semibold"
									style="font-size: 12px; padding: 2px 8px; border-radius: 4px; {roleBadgeStyle(member.role)}"
								>{member.role}</span>
							</td>
							<td style="padding: 12px 16px; font-size: 14px; color: #9090B0;">{member.jerseyNumber ?? '—'}</td>
							<td style="padding: 12px 16px; font-size: 14px; color: #9090B0;">{member.position || '—'}</td>
							<td style="padding: 12px 16px; text-align: right;">
								<div class="flex gap-2 justify-end">
									<form method="POST" action="?/changeRole">
										<input type="hidden" name="userId" value={member.userId} />
										<input type="hidden" name="role" value={member.role === 'coach' ? 'player' : 'coach'} />
										<button
											type="submit"
											style="background: transparent; border: 1px solid #2A2A40; color: #9090B0; font-size: 12px; height: 32px; padding: 0 10px; border-radius: 6px; cursor: pointer;"
										>Make {member.role === 'coach' ? 'Player' : 'Coach'}</button>
									</form>
									<button
										type="button"
										onclick={() => (removeMemberTarget = { userId: member.userId, name: member.displayName })}
										style="background: transparent; border: 1px solid #EF4444; color: #EF4444; font-size: 12px; height: 32px; padding: 0 10px; border-radius: 6px; cursor: pointer;"
									>Remove</button>
								</div>
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
	{/if}
</div>

<!-- Archive confirmation modal -->
{#if showArchiveModal}
	<div
		style="position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 50;"
		role="dialog"
		aria-modal="true"
		aria-labelledby="archive-title"
	>
		<div style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 24px; max-width: 480px; width: 100%; margin: 0 16px;">
			<h3 id="archive-title" class="font-semibold mb-3" style="font-size: 20px; color: #F0F0FF;">Archive Team</h3>
			<p class="mb-6" style="font-size: 14px; color: #9090B0;">
				Archive {data.team.name}? Members will no longer see this team. This can be reversed later.
			</p>
			<div class="flex gap-3">
				<form method="POST" action="?/archive">
					<button
						type="submit"
						style="background-color: #EF4444; color: #FFFFFF; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer;"
					>Archive</button>
				</form>
				<button
					type="button"
					onclick={() => (showArchiveModal = false)}
					style="background: transparent; border: 1px solid #2A2A40; color: #F0F0FF; font-size: 14px; height: 40px; padding: 0 16px; border-radius: 6px; cursor: pointer;"
				>Cancel</button>
			</div>
		</div>
	</div>
{/if}

<!-- Remove member confirmation modal -->
{#if removeMemberTarget}
	<div
		style="position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 50;"
		role="dialog"
		aria-modal="true"
		aria-labelledby="remove-member-title"
	>
		<div style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 24px; max-width: 480px; width: 100%; margin: 0 16px;">
			<h3 id="remove-member-title" class="font-semibold mb-3" style="font-size: 20px; color: #F0F0FF;">Remove Member</h3>
			<p class="mb-6" style="font-size: 14px; color: #9090B0;">
				Remove {removeMemberTarget.name} from {data.team.name}? They will lose access immediately.
			</p>
			<div class="flex gap-3">
				<form method="POST" action="?/removeMember">
					<input type="hidden" name="userId" value={removeMemberTarget.userId} />
					<button
						type="submit"
						style="background-color: #EF4444; color: #FFFFFF; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer;"
					>Remove</button>
				</form>
				<button
					type="button"
					onclick={() => (removeMemberTarget = null)}
					style="background: transparent; border: 1px solid #2A2A40; color: #F0F0FF; font-size: 14px; height: 40px; padding: 0 16px; border-radius: 6px; cursor: pointer;"
				>Cancel</button>
			</div>
		</div>
	</div>
{/if}
