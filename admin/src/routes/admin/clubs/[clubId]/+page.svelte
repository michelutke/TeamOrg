<script lang="ts">
	import { enhance } from '$app/forms';
	import { Plus } from 'lucide-svelte';
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

	function statusChipClasses(status: string): string {
		if (status === 'active') return 'bg-success-container text-success';
		return 'bg-error-container text-error';
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

	function initials(name: string): string {
		return name
			.split(' ')
			.map((p) => p[0])
			.slice(0, 2)
			.join('')
			.toUpperCase();
	}

	const inputClasses =
		'w-full rounded-2xl border-none bg-surface-container-high px-[18px] py-3 text-[14px] text-on-surface outline-none placeholder:text-on-surface-variant focus:ring-2 focus:ring-primary';
	const labelClasses = 'mb-1 block text-[12px] font-medium text-on-surface-variant';
	const filledBtn =
		'cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90';
	const outlinedBtn =
		'cursor-pointer rounded-full border border-outline-variant bg-transparent px-6 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high';
	const modalCard = 'mx-4 w-full max-w-[480px] rounded-[28px] bg-white p-6 shadow-[0px_8px_32px_0px_rgba(0,0,0,0.12)]';
</script>

<svelte:head>
	<title>{data.club.name} — TeamOrg Admin</title>
</svelte:head>

<div class="flex flex-col gap-6">
	<!-- Hero card -->
	<div class="flex items-center gap-5 rounded-3xl bg-primary-container px-8 py-7">
		<div class="flex size-16 shrink-0 items-center justify-center rounded-3xl bg-primary">
			<span class="text-[20px] font-bold text-on-primary">{initials(data.club.name)}</span>
		</div>
		<div class="flex flex-col gap-1.5">
			<div class="flex items-center gap-3">
				<h1 class="font-display text-[28px] font-extrabold text-on-surface">{data.club.name}</h1>
				<span class="rounded-full px-3 py-1 text-[11px] font-bold {statusChipClasses(data.club.status)}">
					{statusLabel(data.club.status)}
				</span>
			</div>
			<p class="text-[14px] text-on-primary-container">
				{data.club.sportType} · {data.club.location || '—'} · created {formatDate(data.club.createdAt)}
			</p>
		</div>
		<div class="flex-1"></div>
		{#if !showEditForm}
			<button
				type="button"
				onclick={() => (showEditForm = true)}
				class="cursor-pointer rounded-full border border-on-primary-container bg-white px-6 py-3 text-[14px] font-bold text-on-primary-container hover:bg-surface"
			>Edit</button>
		{/if}
	</div>

	<!-- Edit form -->
	{#if showEditForm}
		<div class="rounded-3xl bg-surface-container-low p-6">
			<h2 class="mb-4 text-[17px] font-bold text-on-surface">Edit club</h2>
			<form method="POST" action="?/edit">
				<div class="mb-4 grid grid-cols-3 gap-4">
					<div>
						<label for="edit-name" class={labelClasses}>Name</label>
						<input id="edit-name" name="name" type="text" value={data.club.name} class={inputClasses} />
					</div>
					<div>
						<label for="edit-sportType" class={labelClasses}>Sport Type</label>
						<input id="edit-sportType" name="sportType" type="text" value={data.club.sportType} class={inputClasses} />
					</div>
					<div>
						<label for="edit-location" class={labelClasses}>Location</label>
						<input id="edit-location" name="location" type="text" value={data.club.location || ''} class={inputClasses} />
					</div>
				</div>
				<div class="flex gap-3">
					<button type="submit" class={filledBtn}>Save Changes</button>
					<button type="button" onclick={() => (showEditForm = false)} class={outlinedBtn}>Cancel</button>
				</div>
			</form>
		</div>
	{/if}

	<!-- Managers card -->
	<div class="rounded-3xl bg-surface-container-low p-6">
		{#if data.managerNotFound}
			<div class="mb-4 rounded-2xl bg-error-container/60 px-4 py-3 text-[13px] text-error">
				No registered user with email <strong>{data.managerNotFound}</strong> — manager not
				assigned. They must create an account first, then add them below.
			</div>
		{/if}
		<div class="mb-4 flex items-center justify-between">
			<h2 class="font-display text-[20px] font-bold text-on-surface">Managers</h2>
			{#if !showAddManagerForm}
				<button
					type="button"
					onclick={() => (showAddManagerForm = true)}
					class="flex cursor-pointer items-center gap-1 rounded-full border-none bg-transparent px-3 py-2 text-[14px] font-bold text-primary hover:bg-primary-container/50"
				>
					<Plus size={15} />
					Add manager
				</button>
			{/if}
		</div>

		{#if showAddManagerForm}
			<form method="POST" action="?/addManager" class="mb-4 flex items-end gap-3">
				<div class="flex-1">
					{#if form?.error}
						<p class="mb-1 text-[12px] font-medium text-error">{form.error}</p>
					{:else}
						<label for="manager-email" class={labelClasses}>Email</label>
					{/if}
					<input
						id="manager-email"
						name="email"
						type="email"
						placeholder="manager@example.com"
						required
						class={inputClasses}
					/>
				</div>
				<button type="submit" class="{filledBtn} whitespace-nowrap">Invite</button>
				<button
					type="button"
					onclick={() => (showAddManagerForm = false)}
					class="{outlinedBtn} whitespace-nowrap"
				>Cancel</button>
			</form>
		{/if}

		{#if data.club.managers.length > 0}
			<ul class="flex flex-col gap-2">
				{#each data.club.managers as manager}
					<li class="flex items-center gap-4 rounded-2xl bg-white px-4 py-3">
						<div class="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary-container">
							<span class="text-[12px] font-bold text-on-primary-container">{initials(manager.displayName)}</span>
						</div>
						<div class="flex flex-col">
							<span class="text-[14px] font-medium text-on-surface">{manager.displayName}</span>
							<span class="text-[12px] text-on-surface-variant">{manager.email}</span>
						</div>
						<div class="flex-1"></div>
						<button
							type="button"
							onclick={() => (impersonateTarget = { userId: manager.userId, name: manager.displayName })}
							class="cursor-pointer rounded-full border-none bg-primary px-4 py-2 text-[13px] font-bold text-on-primary hover:opacity-90"
							aria-label="Impersonate {manager.displayName}"
						>Impersonate manager</button>
						<button
							type="button"
							onclick={() => (removeManagerTarget = { userId: manager.userId, name: manager.displayName })}
							class="cursor-pointer rounded-full border-none bg-transparent px-3 py-2 text-[13px] font-bold text-error hover:bg-error-container/50"
							aria-label="Remove {manager.displayName} as ClubManager"
						>Remove</button>
					</li>
				{/each}
			</ul>
		{:else}
			<p class="text-[14px] text-on-surface-variant">No ClubManagers assigned.</p>
		{/if}
	</div>

	<!-- Danger zone -->
	<div class="flex items-center gap-4 rounded-3xl bg-error-container/60 px-8 py-5">
		<div class="flex flex-col gap-1">
			<h2 class="text-[15px] font-bold text-error">Danger zone</h2>
			<p class="text-[13px] text-error">
				Deactivating keeps all data. Deleting is permanent and requires typed confirmation.
			</p>
		</div>
		<div class="flex-1"></div>
		{#if data.club.status === 'active'}
			<button
				type="button"
				onclick={() => (showDeactivateModal = true)}
				class="cursor-pointer rounded-full border border-error bg-transparent px-5 py-2.5 text-[14px] font-bold text-error hover:bg-error-container"
				aria-label="Deactivate {data.club.name}"
			>Deactivate</button>
		{:else if data.club.status === 'deactivated'}
			<button
				type="button"
				onclick={() => (showReactivateModal = true)}
				class="cursor-pointer rounded-full border border-success bg-transparent px-5 py-2.5 text-[14px] font-bold text-success hover:bg-success-container"
				aria-label="Reactivate {data.club.name}"
			>Reactivate</button>
		{/if}
		{#if data.club.status !== 'deleted'}
			<button
				type="button"
				onclick={() => (showDeleteModal = true)}
				class="cursor-pointer rounded-full border-none bg-error px-5 py-2.5 text-[14px] font-bold text-on-error hover:opacity-90"
				aria-label="Delete {data.club.name} permanently"
			>Delete club</button>
		{/if}
	</div>
</div>

<!-- Deactivate confirmation modal -->
{#if showDeactivateModal}
	<div
		class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
		role="dialog"
		aria-modal="true"
		aria-labelledby="deactivate-title"
	>
		<div class={modalCard}>
			<h3 id="deactivate-title" class="mb-3 font-display text-[22px] font-extrabold text-on-surface">Deactivate club</h3>
			<p class="mb-6 text-[14px] text-on-surface-variant">
				Deactivating {data.club.name} will prevent all members from accessing the app. Data is preserved.
			</p>
			<div class="flex gap-3">
				<form method="POST" action="?/deactivate">
					<button
						type="submit"
						class="cursor-pointer rounded-full border-none bg-error px-6 py-3 text-[14px] font-bold text-on-error hover:opacity-90"
					>Deactivate</button>
				</form>
				<button type="button" onclick={() => (showDeactivateModal = false)} class={outlinedBtn}>Cancel</button>
			</div>
		</div>
	</div>
{/if}

<!-- Reactivate confirmation modal -->
{#if showReactivateModal}
	<div
		class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
		role="dialog"
		aria-modal="true"
		aria-labelledby="reactivate-title"
	>
		<div class={modalCard}>
			<h3 id="reactivate-title" class="mb-3 font-display text-[22px] font-extrabold text-on-surface">Reactivate club</h3>
			<p class="mb-6 text-[14px] text-on-surface-variant">
				Reactivate {data.club.name}? Members will regain access immediately.
			</p>
			<div class="flex gap-3">
				<form method="POST" action="?/reactivate">
					<button
						type="submit"
						class="cursor-pointer rounded-full border-none bg-success px-6 py-3 text-[14px] font-bold text-white hover:opacity-90"
					>Reactivate</button>
				</form>
				<button type="button" onclick={() => (showReactivateModal = false)} class={outlinedBtn}>Cancel</button>
			</div>
		</div>
	</div>
{/if}

<!-- Delete type-to-confirm modal -->
{#if showDeleteModal}
	<div
		class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
		role="dialog"
		aria-modal="true"
		aria-labelledby="delete-title"
	>
		<div class={modalCard}>
			<h3 id="delete-title" class="mb-3 font-display text-[22px] font-extrabold text-on-surface">Delete club</h3>
			<p class="mb-4 text-[14px] text-on-surface-variant">
				Type the club name to confirm permanent deletion. This cannot be undone.
			</p>
			<input
				type="text"
				bind:value={deleteConfirmInput}
				placeholder={data.club.name}
				class="mb-4 {inputClasses}"
			/>
			<div class="flex gap-3">
				<form method="POST" action="?/delete">
					<button
						type="submit"
						disabled={!deleteEnabled}
						class="rounded-full border-none px-6 py-3 text-[14px] font-bold {deleteEnabled
							? 'cursor-pointer bg-error text-on-error hover:opacity-90'
							: 'cursor-not-allowed bg-surface-container-high text-on-surface-variant'}"
					>Delete permanently</button>
				</form>
				<button
					type="button"
					onclick={() => {
						showDeleteModal = false;
						deleteConfirmInput = '';
					}}
					class={outlinedBtn}
				>Cancel</button>
			</div>
		</div>
	</div>
{/if}

<!-- Remove manager confirmation modal -->
{#if removeManagerTarget}
	<div
		class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
		role="dialog"
		aria-modal="true"
		aria-labelledby="remove-manager-title"
	>
		<div class={modalCard}>
			<h3 id="remove-manager-title" class="mb-3 font-display text-[22px] font-extrabold text-on-surface">Remove manager</h3>
			<p class="mb-6 text-[14px] text-on-surface-variant">
			Are you sure you want to remove {removeManagerTarget.name} as ClubManager of {data.club.name}? They will lose access immediately.
			</p>
			<div class="flex gap-3">
				<form method="POST" action="?/removeManager">
					<input type="hidden" name="userId" value={removeManagerTarget.userId} />
					<button
						type="submit"
						class="cursor-pointer rounded-full border-none bg-error px-6 py-3 text-[14px] font-bold text-on-error hover:opacity-90"
						aria-label="Remove {removeManagerTarget.name} as ClubManager"
					>Remove</button>
				</form>
				<button type="button" onclick={() => (removeManagerTarget = null)} class={outlinedBtn}>Cancel</button>
			</div>
		</div>
	</div>
{/if}

<!-- Impersonate confirmation modal -->
{#if impersonateTarget}
	<div
		class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
		role="dialog"
		aria-modal="true"
		aria-labelledby="impersonate-title"
	>
		<div class={modalCard}>
			<h3 id="impersonate-title" class="mb-3 font-display text-[22px] font-extrabold text-on-surface">
				Impersonate {impersonateTarget.name}?
			</h3>
			<p class="mb-6 text-[14px] text-on-surface-variant">
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
						class="cursor-pointer rounded-full border-none bg-tertiary px-6 py-3 text-[14px] font-bold text-on-tertiary hover:opacity-90"
					>Confirm</button>
				</form>
				<button type="button" onclick={() => (impersonateTarget = null)} class={outlinedBtn}>Cancel</button>
			</div>
		</div>
	</div>
{/if}
