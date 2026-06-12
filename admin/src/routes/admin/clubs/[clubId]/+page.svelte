<script lang="ts">
	import { enhance } from '$app/forms';
	import type { PageData, ActionData } from './$types';

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();

	let showEditForm = $state(false);
	let showDeactivateModal = $state(false);
	let showReactivateModal = $state(false);
	let showDeleteModal = $state(false);
	let showAddManagerForm = $state(false);
	let deleteConfirmInput = $state('');
	let removeManagerTarget = $state<{ userId: string; name: string } | null>(null);
	let impersonateTarget = $state<{ userId: string; name: string } | null>(null);

	let deleteEnabled = $derived(deleteConfirmInput === data.club.name);

	function statusColor(status: string): string {
		if (status === 'active') return '#22C55E';
		if (status === 'deactivated') return '#FACC15';
		return '#EF4444';
	}

	function statusBg(status: string): string {
		if (status === 'active') return 'rgba(34,197,94,0.12)';
		if (status === 'deactivated') return 'rgba(250,204,21,0.12)';
		return 'rgba(239,68,68,0.12)';
	}

	function statusLabel(status: string): string {
		if (status === 'active') return 'Active';
		if (status === 'deactivated') return 'Deactivated';
		return 'Deleted';
	}

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
</script>

<svelte:head>
	<title>{data.club.name} — TeamOrg Admin</title>
</svelte:head>

<!-- Club info card -->
<div
	class="mb-6"
	style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 24px;"
>
	<div class="flex items-center justify-between mb-4">
		<h2 class="font-semibold" style="font-size: 16px; color: #F0F0FF;">Club Info</h2>
		{#if !showEditForm}
			<button
				type="button"
				onclick={() => (showEditForm = true)}
				style="
					background: transparent;
					border: 1px solid #2A2A40;
					color: #F0F0FF;
					font-size: 14px;
					height: 36px;
					padding: 0 12px;
					border-radius: 6px;
					cursor: pointer;
				"
			>Edit</button>
		{/if}
	</div>

	{#if showEditForm}
		<form method="POST" action="?/edit">
			<div class="grid gap-4 mb-4" style="grid-template-columns: 1fr 1fr 1fr;">
				<div>
					<label for="edit-name" class="block font-semibold mb-1" style="font-size: 12px; color: #F0F0FF;">Name</label>
					<input id="edit-name" name="name" type="text" value={data.club.name} style={inputStyle} />
				</div>
				<div>
					<label for="edit-sportType" class="block font-semibold mb-1" style="font-size: 12px; color: #F0F0FF;">Sport Type</label>
					<input id="edit-sportType" name="sportType" type="text" value={data.club.sportType} style={inputStyle} />
				</div>
				<div>
					<label for="edit-location" class="block font-semibold mb-1" style="font-size: 12px; color: #F0F0FF;">Location</label>
					<input id="edit-location" name="location" type="text" value={data.club.location || ''} style={inputStyle} />
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
		<dl class="grid gap-3" style="grid-template-columns: repeat(4, 1fr);">
			<div>
				<dt class="font-semibold" style="font-size: 12px; color: #9090B0;">Sport Type</dt>
				<dd style="font-size: 14px; color: #F0F0FF; margin-top: 4px;">{data.club.sportType}</dd>
			</div>
			<div>
				<dt class="font-semibold" style="font-size: 12px; color: #9090B0;">Location</dt>
				<dd style="font-size: 14px; color: #F0F0FF; margin-top: 4px;">{data.club.location || '—'}</dd>
			</div>
			<div>
				<dt class="font-semibold" style="font-size: 12px; color: #9090B0;">Status</dt>
				<dd style="margin-top: 4px;">
					<span
						class="font-semibold"
						style="font-size: 12px; color: {statusColor(data.club.status)}; background-color: {statusBg(data.club.status)}; padding: 2px 8px; border-radius: 4px;"
					>{statusLabel(data.club.status)}</span>
				</dd>
			</div>
			<div>
				<dt class="font-semibold" style="font-size: 12px; color: #9090B0;">Created</dt>
				<dd style="font-size: 14px; color: #F0F0FF; margin-top: 4px;">{formatDate(data.club.createdAt)}</dd>
			</div>
		</dl>
	{/if}
</div>

<!-- Status actions -->
<div
	class="mb-6"
	style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 24px;"
>
	<h2 class="font-semibold mb-4" style="font-size: 16px; color: #F0F0FF;">Status Actions</h2>
	<div class="flex gap-3 flex-wrap">
		{#if data.club.status === 'active'}
			<button
				type="button"
				onclick={() => (showDeactivateModal = true)}
				style="background-color: #FACC15; color: #090912; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer;"
				aria-label="Deactivate {data.club.name}"
			>Deactivate</button>
		{:else if data.club.status === 'deactivated'}
			<button
				type="button"
				onclick={() => (showReactivateModal = true)}
				style="background-color: #22C55E; color: #FFFFFF; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer;"
				aria-label="Reactivate {data.club.name}"
			>Reactivate</button>
		{/if}
		{#if data.club.status !== 'deleted'}
			<button
				type="button"
				onclick={() => (showDeleteModal = true)}
				style="background-color: #EF4444; color: #FFFFFF; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer;"
				aria-label="Delete {data.club.name} permanently"
			>Delete Permanently</button>
		{/if}
	</div>
</div>

<!-- ClubManagers section -->
<div style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 24px;">
	<h2 class="font-semibold mb-4" style="font-size: 16px; color: #F0F0FF;">ClubManagers</h2>

	{#if data.club.managers.length > 0}
		<table class="w-full mb-4" style="border-collapse: collapse; border: 1px solid #2A2A40; border-radius: 6px; overflow: hidden;">
			<thead>
				<tr style="background-color: #13131F;">
					<th scope="col" class="text-left font-semibold" style="font-size: 12px; color: #9090B0; padding: 10px 16px;">Name</th>
					<th scope="col" class="text-left font-semibold" style="font-size: 12px; color: #9090B0; padding: 10px 16px;">Email</th>
					<th scope="col" class="font-semibold" style="font-size: 12px; color: #9090B0; padding: 10px 16px; text-align: right;">Actions</th>
				</tr>
			</thead>
			<tbody>
				{#each data.club.managers as manager}
					<tr style="border-top: 1px solid #2A2A40;">
						<td style="font-size: 14px; color: #F0F0FF; padding: 12px 16px;">{manager.displayName}</td>
						<td style="font-size: 14px; color: #9090B0; padding: 12px 16px;">{manager.email}</td>
						<td style="padding: 12px 16px; text-align: right;">
							<div class="flex gap-2 justify-end">
								<button
									type="button"
									onclick={() => (impersonateTarget = { userId: manager.userId, name: manager.displayName })}
									style="background: transparent; border: 1px solid #F97316; color: #F97316; font-size: 12px; height: 32px; padding: 0 10px; border-radius: 6px; cursor: pointer;"
									aria-label="Impersonate {manager.displayName}"
								>Impersonate</button>
								<button
									type="button"
									onclick={() => (removeManagerTarget = { userId: manager.userId, name: manager.displayName })}
									style="background: transparent; border: 1px solid #EF4444; color: #EF4444; font-size: 12px; height: 32px; padding: 0 10px; border-radius: 6px; cursor: pointer;"
									aria-label="Remove {manager.displayName} as ClubManager"
								>Remove</button>
							</div>
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	{:else}
		<p class="mb-4" style="font-size: 14px; color: #9090B0;">No ClubManagers assigned.</p>
	{/if}

	{#if !showAddManagerForm}
		<button
			type="button"
			onclick={() => (showAddManagerForm = true)}
			style="background-color: #4F8EF7; color: #FFFFFF; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer;"
		>Invite ClubManager</button>
	{:else}
		<form method="POST" action="?/addManager" class="flex gap-3 items-end">
			{#if form?.error}
				<div style="flex: 1;">
					<p class="mb-1" style="font-size: 12px; color: #EF4444;">{form.error}</p>
					<input
						name="email"
						type="email"
						placeholder="manager@example.com"
						required
						style={inputStyle}
					/>
				</div>
			{:else}
				<div style="flex: 1;">
					<label for="manager-email" class="block font-semibold mb-1" style="font-size: 12px; color: #F0F0FF;">Email</label>
					<input
						id="manager-email"
						name="email"
						type="email"
						placeholder="manager@example.com"
						required
						style={inputStyle}
					/>
				</div>
			{/if}
			<button
				type="submit"
				style="background-color: #4F8EF7; color: #FFFFFF; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer; white-space: nowrap;"
			>Invite</button>
			<button
				type="button"
				onclick={() => (showAddManagerForm = false)}
				style="background: transparent; border: 1px solid #2A2A40; color: #F0F0FF; font-size: 14px; height: 40px; padding: 0 16px; border-radius: 6px; cursor: pointer; white-space: nowrap;"
			>Cancel</button>
		</form>
	{/if}
</div>

<!-- Deactivate confirmation modal -->
{#if showDeactivateModal}
	<div
		style="position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 50;"
		role="dialog"
		aria-modal="true"
		aria-labelledby="deactivate-title"
	>
		<div style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 24px; max-width: 480px; width: 100%; margin: 0 16px;">
			<h3 id="deactivate-title" class="font-semibold mb-3" style="font-size: 20px; color: #F0F0FF;">Deactivate Club</h3>
			<p class="mb-6" style="font-size: 14px; color: #9090B0;">
				Deactivating {data.club.name} will prevent all members from accessing the app. Data is preserved.
			</p>
			<div class="flex gap-3">
				<form method="POST" action="?/deactivate">
					<button
						type="submit"
						style="background-color: #FACC15; color: #090912; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer;"
					>Deactivate</button>
				</form>
				<button
					type="button"
					onclick={() => (showDeactivateModal = false)}
					style="background: transparent; border: 1px solid #2A2A40; color: #F0F0FF; font-size: 14px; height: 40px; padding: 0 16px; border-radius: 6px; cursor: pointer;"
				>Cancel</button>
			</div>
		</div>
	</div>
{/if}

<!-- Reactivate confirmation modal -->
{#if showReactivateModal}
	<div
		style="position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 50;"
		role="dialog"
		aria-modal="true"
		aria-labelledby="reactivate-title"
	>
		<div style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 24px; max-width: 480px; width: 100%; margin: 0 16px;">
			<h3 id="reactivate-title" class="font-semibold mb-3" style="font-size: 20px; color: #F0F0FF;">Reactivate Club</h3>
			<p class="mb-6" style="font-size: 14px; color: #9090B0;">
				Reactivate {data.club.name}? Members will regain access immediately.
			</p>
			<div class="flex gap-3">
				<form method="POST" action="?/reactivate">
					<button
						type="submit"
						style="background-color: #22C55E; color: #FFFFFF; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer;"
					>Reactivate</button>
				</form>
				<button
					type="button"
					onclick={() => (showReactivateModal = false)}
					style="background: transparent; border: 1px solid #2A2A40; color: #F0F0FF; font-size: 14px; height: 40px; padding: 0 16px; border-radius: 6px; cursor: pointer;"
				>Cancel</button>
			</div>
		</div>
	</div>
{/if}

<!-- Delete type-to-confirm modal -->
{#if showDeleteModal}
	<div
		style="position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 50;"
		role="dialog"
		aria-modal="true"
		aria-labelledby="delete-title"
	>
		<div style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 24px; max-width: 480px; width: 100%; margin: 0 16px;">
			<h3 id="delete-title" class="font-semibold mb-3" style="font-size: 20px; color: #F0F0FF;">Delete Club</h3>
			<p class="mb-4" style="font-size: 14px; color: #9090B0;">
				Type the club name to confirm permanent deletion. This cannot be undone.
			</p>
			<input
				type="text"
				bind:value={deleteConfirmInput}
				placeholder={data.club.name}
				style="
					width: 100%;
					background-color: #090912;
					border: 1px solid #2A2A40;
					color: #F0F0FF;
					font-size: 14px;
					height: 40px;
					padding: 0 16px;
					border-radius: 6px;
					outline: none;
					margin-bottom: 16px;
				"
			/>
			<div class="flex gap-3">
				<form method="POST" action="?/delete">
					<button
						type="submit"
						disabled={!deleteEnabled}
						style="
							background-color: {deleteEnabled ? '#EF4444' : '#2A2A40'};
							color: {deleteEnabled ? '#FFFFFF' : '#9090B0'};
							font-size: 14px;
							font-weight: 600;
							height: 40px;
							padding: 0 16px;
							border-radius: 6px;
							border: none;
							cursor: {deleteEnabled ? 'pointer' : 'not-allowed'};
						"
					>Delete Permanently</button>
				</form>
				<button
					type="button"
					onclick={() => { showDeleteModal = false; deleteConfirmInput = ''; }}
					style="background: transparent; border: 1px solid #2A2A40; color: #F0F0FF; font-size: 14px; height: 40px; padding: 0 16px; border-radius: 6px; cursor: pointer;"
				>Cancel</button>
			</div>
		</div>
	</div>
{/if}

<!-- Remove manager confirmation modal -->
{#if removeManagerTarget}
	<div
		style="position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 50;"
		role="dialog"
		aria-modal="true"
		aria-labelledby="remove-manager-title"
	>
		<div style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 24px; max-width: 480px; width: 100%; margin: 0 16px;">
			<h3 id="remove-manager-title" class="font-semibold mb-3" style="font-size: 20px; color: #F0F0FF;">Remove ClubManager</h3>
			<p class="mb-6" style="font-size: 14px; color: #9090B0;">
				Are you sure you want to remove {removeManagerTarget.name} as ClubManager of {data.club.name}? They will lose access immediately.
			</p>
			<div class="flex gap-3">
				<form method="POST" action="?/removeManager">
					<input type="hidden" name="userId" value={removeManagerTarget.userId} />
					<button
						type="submit"
						style="background-color: #EF4444; color: #FFFFFF; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer;"
						aria-label="Remove {removeManagerTarget.name} as ClubManager"
					>Remove</button>
				</form>
				<button
					type="button"
					onclick={() => (removeManagerTarget = null)}
					style="background: transparent; border: 1px solid #2A2A40; color: #F0F0FF; font-size: 14px; height: 40px; padding: 0 16px; border-radius: 6px; cursor: pointer;"
				>Cancel</button>
			</div>
		</div>
	</div>
{/if}

<!-- Impersonate confirmation modal -->
{#if impersonateTarget}
	<div
		style="position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 50;"
		role="dialog"
		aria-modal="true"
		aria-labelledby="impersonate-title"
	>
		<div style="background-color: #1C1C2E; border: 1px solid #2A2A40; border-radius: 8px; padding: 24px; max-width: 480px; width: 100%; margin: 0 16px;">
			<h3 id="impersonate-title" class="font-semibold mb-3" style="font-size: 20px; color: #F0F0FF;">Impersonate {impersonateTarget.name}?</h3>
			<p class="mb-6" style="font-size: 14px; color: #9090B0;">
				You will act as ClubManager for 1 hour. All actions are audit-logged.
			</p>
			<div class="flex gap-3">
				<form method="POST" action="/admin/impersonate/start" use:enhance>
					<input type="hidden" name="targetUserId" value={impersonateTarget.userId} />
					<input type="hidden" name="clubId" value={data.club.id} />
					<input type="hidden" name="clubName" value={data.club.name} />
					<input type="hidden" name="redirectTo" value="/admin/clubs/{data.club.id}/teams" />
					<button
						type="submit"
						style="background-color: #F97316; color: #FFFFFF; font-size: 14px; font-weight: 600; height: 40px; padding: 0 16px; border-radius: 6px; border: none; cursor: pointer;"
					>Confirm</button>
				</form>
				<button
					type="button"
					onclick={() => (impersonateTarget = null)}
					style="background: transparent; border: 1px solid #2A2A40; color: #F0F0FF; font-size: 14px; height: 40px; padding: 0 16px; border-radius: 6px; cursor: pointer;"
				>Cancel</button>
			</div>
		</div>
	</div>
{/if}
